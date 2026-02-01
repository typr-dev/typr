package typr.cli.commands

import cats.effect.ExitCode
import cats.effect.IO
import typr.bridge.{CompatibilityMode, DefaultNameAligner, DomainField, DomainTypeDefinition, NameAligner, PrimarySource}
import typr.bridge.{AlignedSource as BridgeAlignedSource}
import typr.bridge.api.BridgeApiImpl
import typr.bridge.model.*
import typr.cli.config.*
import typr.config.generated.{BridgeType, DomainGenerateOptions, DomainType, FieldSpecObject, FieldSpecString}

import java.nio.file.Files
import java.nio.file.Paths

object Check {

  def run(
      configPath: String,
      quiet: Boolean,
      debug: Boolean
  ): IO[ExitCode] = {
    val result = for {
      _ <- IO.unlessA(quiet)(IO.println(s"Reading config from: $configPath"))

      yamlContent <- IO(Files.readString(Paths.get(configPath)))
      substituted <- IO.fromEither(EnvSubstitution.substitute(yamlContent).left.map(new Exception(_)))

      config <- IO.fromEither(ConfigParser.parse(substituted).left.map(new Exception(_)))
      _ <- IO.whenA(debug)(IO.println(s"Parsed config with ${config.types.map(_.size).getOrElse(0)} types"))

      domainTypes = extractDomainTypes(config.types)
      _ <- IO.whenA(debug)(IO.println(s"Found ${domainTypes.size} domain types"))

      _ <-
        if (domainTypes.isEmpty) {
          IO.println("No domain types found in config. Nothing to check.") *> IO.pure(())
        } else IO.pure(())

      nameAligner = buildNameAligner(config)
      sourceDeclarations = extractSourceDeclarations(config.types)

      report <-
        if (domainTypes.isEmpty) {
          IO.pure(CheckReport(findings = Nil, entitySummaries = Nil, timestamp = System.currentTimeMillis()))
        } else {
          IO {
            val api = new BridgeApiImpl()
            api.check(
              domainTypes = domainTypes,
              sourceDeclarations = sourceDeclarations,
              sourceEntities = Map.empty,
              nameAligner = nameAligner
            )
          }
        }

      _ <- renderReport(report, quiet = quiet, debug = debug)
    } yield report.exitCode match {
      case 0 => ExitCode.Success
      case _ => ExitCode.Error
    }

    result.handleErrorWith { e =>
      IO.println(s"Error: ${e.getMessage}") *>
        IO.whenA(debug)(IO.println(e.getStackTrace.mkString("\n"))) *>
        IO.pure(ExitCode.Error)
    }
  }

  private def extractDomainTypes(types: Option[Map[String, BridgeType]]): Map[String, DomainTypeDefinition] =
    types
      .getOrElse(Map.empty)
      .collect { case (name, ct: DomainType) =>
        val fields = ct.fields.toList.map { case (fieldName, fieldType) =>
          val (typeName, nullable, array, description) = fieldType match {
            case FieldSpecString(value) =>
              val parsed = DomainField.parseCompact(value)
              (parsed.typeName, parsed.nullable, parsed.array, None)
            case fd: FieldSpecObject =>
              (fd.`type`, fd.nullable.getOrElse(false), fd.array.getOrElse(false), fd.description)
          }
          DomainField(
            name = fieldName,
            typeName = typeName,
            nullable = nullable,
            array = array,
            description = description
          )
        }

        val primary = ct.primary.flatMap(PrimarySource.fromKey)

        val alignedSources = (ct.alignedSources.getOrElse(Map.empty) ++ ct.projections.getOrElse(Map.empty)).map { case (key, aligned) =>
          val (sourceName, entityPath) = key.split(":", 2).toList match {
            case src :: rest => (src, rest.mkString(":"))
            case _           => (key, aligned.entity.getOrElse(""))
          }
          key -> BridgeAlignedSource(
            sourceName = sourceName,
            entityPath = aligned.entity.getOrElse(entityPath),
            mode = CompatibilityMode.fromString(aligned.mode.getOrElse("superset")).getOrElse(CompatibilityMode.Superset),
            mappings = aligned.mappings.getOrElse(Map.empty),
            exclude = aligned.exclude.getOrElse(Nil).toSet,
            includeExtra = aligned.include_extra.getOrElse(Nil),
            readonly = aligned.readonly.getOrElse(false)
          )
        }

        val genOpts = ct.generate.getOrElse(DomainGenerateOptions(None, None, None, None, None, None))

        name -> DomainTypeDefinition(
          name = name,
          primary = primary,
          fields = fields,
          alignedSources = alignedSources,
          description = ct.description,
          generateDomainType = genOpts.domainType.orElse(genOpts.canonical).getOrElse(true),
          generateMappers = genOpts.mappers.getOrElse(true),
          generateInterface = genOpts.interface.getOrElse(false),
          generateBuilder = genOpts.builder.getOrElse(false),
          generateCopy = genOpts.copy.getOrElse(true)
        )
      }

  private def extractSourceDeclarations(types: Option[Map[String, BridgeType]]): Map[String, Map[String, SourceDeclaration]] =
    types
      .getOrElse(Map.empty)
      .collect { case (name, ct: DomainType) =>
        val primaryDecls = ct.primary
          .flatMap(PrimarySource.fromKey)
          .map { primary =>
            primary.key -> SourceDeclaration(
              sourceName = primary.sourceName,
              entityPath = primary.entityPath,
              role = SourceRole.Primary,
              direction = FlowDirection.InOut,
              mode = CompatibilityMode.Exact,
              mappings = Map.empty,
              exclude = Set.empty,
              includeExtra = Nil,
              readonly = false,
              defaultTypePolicy = TypePolicy.Exact,
              fieldOverrides = Map.empty
            )
          }
          .toMap

        val alignedDecls = (ct.alignedSources.getOrElse(Map.empty) ++ ct.projections.getOrElse(Map.empty)).map { case (key, aligned) =>
          val (sourceName, entityPath) = key.split(":", 2).toList match {
            case src :: rest => (src, rest.mkString(":"))
            case _           => (key, aligned.entity.getOrElse(""))
          }
          key -> SourceDeclaration(
            sourceName = sourceName,
            entityPath = aligned.entity.getOrElse(entityPath),
            role = SourceRole.Aligned,
            direction = FlowDirection.InOut,
            mode = CompatibilityMode.fromString(aligned.mode.getOrElse("superset")).getOrElse(CompatibilityMode.Superset),
            mappings = aligned.mappings.getOrElse(Map.empty),
            exclude = aligned.exclude.getOrElse(Nil).toSet,
            includeExtra = aligned.include_extra.getOrElse(Nil),
            readonly = aligned.readonly.getOrElse(false),
            defaultTypePolicy = TypePolicy.Exact,
            fieldOverrides = Map.empty
          )
        }

        name -> (primaryDecls ++ alignedDecls)
      }

  private def buildNameAligner(config: TyprConfig): NameAligner =
    DefaultNameAligner.default

  private def renderReport(report: CheckReport, quiet: Boolean, debug: Boolean): IO[Unit] = IO {
    if (!(quiet && !report.hasErrors)) {
      val errors = report.errors
      val warnings = report.warnings

      if (errors.nonEmpty) {
        println()
        println(s"ERRORS (${errors.size}):")
        errors.foreach { f =>
          val location = List(
            Some(f.entityName),
            f.sourceKey.map(s => s"source=$s"),
            f.fieldName.map(fn => s"field=$fn")
          ).flatten.mkString(" > ")
          println(s"  [${f.code}] $location")
          println(s"    ${f.message}")
          f.suggestion.foreach(s => println(s"    Suggestion: $s"))
        }
      }

      if (warnings.nonEmpty && !quiet) {
        println()
        println(s"WARNINGS (${warnings.size}):")
        warnings.foreach { f =>
          val location = List(
            Some(f.entityName),
            f.sourceKey.map(s => s"source=$s"),
            f.fieldName.map(fn => s"field=$fn")
          ).flatten.mkString(" > ")
          println(s"  [${f.code}] $location")
          println(s"    ${f.message}")
        }
      }

      if (!quiet) {
        println()
        println("Entity Summary:")
        report.entitySummaries.foreach { s =>
          println(s"  ${s.name}: ${s.fieldCount} fields, ${s.sourceCount} sources, ${s.forwardCount} forward, ${s.dropCount} drop, ${s.customCount} custom")
          if (s.errorCount > 0 || s.warningCount > 0) {
            println(s"    ${s.errorCount} errors, ${s.warningCount} warnings")
          }
        }
      }

      println()
      if (report.hasErrors) {
        println(s"Check FAILED: ${errors.size} error(s), ${warnings.size} warning(s)")
      } else if (warnings.nonEmpty) {
        println(s"Check PASSED with ${warnings.size} warning(s)")
      } else {
        println("Check PASSED")
      }
    }
  }
}
