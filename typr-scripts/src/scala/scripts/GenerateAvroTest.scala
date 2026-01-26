package scripts

import typr.avro.{AvroCodegen, AvroOptions, AvroWireFormat, FrameworkIntegration, HeaderField, HeaderSchema, HeaderType, SchemaSource}
import typr.effects.EffectType
import typr.internal.FileSync
import typr.jvm
import typr.internal.codegen.{LangJava, LangKotlin, LangScala, TypeSupportKotlin, addPackageAndImports}
import typr.openapi.codegen.JacksonSupport
import typr.{Dialect, Lang, RelPath, TypeSupportScala}

import java.nio.file.Path

object GenerateAvroTest {
  val buildDir: Path = Path.of(sys.props("user.dir"))

  def main(args: Array[String]): Unit = {
    val schemasPath = buildDir.resolve("testers/avro/schemas")

    println(s"Generating Avro code from: $schemasPath")

    val langScala = LangScala.javaDsl(Dialect.Scala3, TypeSupportScala)
    val langKotlin = LangKotlin(TypeSupportKotlin)

    // Java - Confluent + Blocking (default)
    generateCode(
      schemasPath = schemasPath,
      lang = LangJava,
      outputDir = "testers/avro/java",
      avroWireFormat = AvroWireFormat.ConfluentRegistry,
      effectType = EffectType.Blocking
    )

    // Java - Confluent + CompletableFuture (async)
    generateCode(
      schemasPath = schemasPath,
      lang = LangJava,
      outputDir = "testers/avro/java-async",
      avroWireFormat = AvroWireFormat.ConfluentRegistry,
      effectType = EffectType.CompletableFuture
    )

    // Java - BinaryEncoded + Blocking (no schema registry)
    generateCode(
      schemasPath = schemasPath,
      lang = LangJava,
      outputDir = "testers/avro/java-vanilla",
      avroWireFormat = AvroWireFormat.BinaryEncoded,
      effectType = EffectType.Blocking
    )

    // Scala - Confluent + Blocking (default)
    generateCode(
      schemasPath = schemasPath,
      lang = langScala,
      outputDir = "testers/avro/scala",
      avroWireFormat = AvroWireFormat.ConfluentRegistry,
      effectType = EffectType.Blocking
    )

    // Scala - Confluent + CatsIO
    generateCode(
      schemasPath = schemasPath,
      lang = langScala,
      outputDir = "testers/avro/scala-cats",
      avroWireFormat = AvroWireFormat.ConfluentRegistry,
      effectType = EffectType.CatsIO
    )

    // Kotlin - Confluent + Blocking (default)
    generateCode(
      schemasPath = schemasPath,
      lang = langKotlin,
      outputDir = "testers/avro/kotlin",
      avroWireFormat = AvroWireFormat.ConfluentRegistry,
      effectType = EffectType.Blocking
    )

    // Java - JSON wire format (Jackson annotations, no Avro serdes)
    generateCode(
      schemasPath = schemasPath,
      lang = LangJava,
      outputDir = "testers/avro/java-json",
      avroWireFormat = AvroWireFormat.JsonEncoded(JacksonSupport),
      effectType = EffectType.Blocking
    )

    // Kotlin - JSON wire format (Jackson annotations, no Avro serdes)
    generateCode(
      schemasPath = schemasPath,
      lang = langKotlin,
      outputDir = "testers/avro/kotlin-json",
      avroWireFormat = AvroWireFormat.JsonEncoded(JacksonSupport),
      effectType = EffectType.Blocking
    )

    // Scala - JSON wire format (Jackson annotations, no Avro serdes)
    generateCode(
      schemasPath = schemasPath,
      lang = langScala,
      outputDir = "testers/avro/scala-json",
      avroWireFormat = AvroWireFormat.JsonEncoded(JacksonSupport),
      effectType = EffectType.Blocking
    )

    // Java - Spring framework integration (JSON + event publishers/listeners + RPC)
    generateCodeWithFramework(
      schemasPath = schemasPath,
      lang = LangJava,
      outputDir = "testers/avro/java-spring",
      avroWireFormat = AvroWireFormat.JsonEncoded(JacksonSupport),
      frameworkIntegration = FrameworkIntegration.Spring,
      effectType = EffectType.Blocking
    )

    // Java - Quarkus framework integration (JSON + event publishers/listeners + RPC)
    generateCodeWithFramework(
      schemasPath = schemasPath,
      lang = LangJava,
      outputDir = "testers/avro/java-quarkus",
      avroWireFormat = AvroWireFormat.JsonEncoded(JacksonSupport),
      frameworkIntegration = FrameworkIntegration.Quarkus,
      effectType = EffectType.Blocking
    )

    // Kotlin - Quarkus framework integration with Mutiny (async RPC)
    generateCodeWithFramework(
      schemasPath = schemasPath,
      lang = langKotlin,
      outputDir = "testers/avro/kotlin-quarkus-mutiny",
      avroWireFormat = AvroWireFormat.JsonEncoded(JacksonSupport),
      frameworkIntegration = FrameworkIntegration.Quarkus,
      effectType = EffectType.MutinyUni
    )

    println("Done!")
  }

  private def generateCode(
      schemasPath: Path,
      lang: Lang,
      outputDir: String,
      avroWireFormat: AvroWireFormat,
      effectType: EffectType
  ): Unit = {
    val projectDir = buildDir.resolve(outputDir)
    val sourceDir = projectDir.resolve("generated-and-checked-in")

    println(s"Output directory: $sourceDir")

    // Event groups are auto-detected from directory structure:
    // - schemas/order-events/*.avsc -> OrderEvents sealed interface
    // - schemas/*.avsc -> standalone types
    val standardHeaders = HeaderSchema(
      List(
        HeaderField("correlationId", HeaderType.UUID, required = true),
        HeaderField("timestamp", HeaderType.Instant, required = true),
        HeaderField("source", HeaderType.String, required = false)
      )
    )

    val options = AvroOptions
      .default(
        pkg = jvm.QIdent(List(jvm.Ident("com"), jvm.Ident("example"), jvm.Ident("events"))),
        schemaSource = SchemaSource.Directory(schemasPath)
      )
      .copy(
        avroWireFormat = avroWireFormat,
        effectType = effectType,
        headerSchemas = Map("standard" -> standardHeaders),
        topicHeaders = Map("order-events" -> "standard"),
        defaultHeaderSchema = Some("standard"),
        enablePreciseTypes = true
      )

    val result = AvroCodegen.generate(options, lang)

    if (result.errors.nonEmpty) {
      println("Errors:")
      result.errors.foreach(e => println(s"  - $e"))
      sys.exit(1)
    }

    if (result.files.isEmpty) {
      println("No files generated!")
      return
    }

    // Build known names by package for import resolution
    val knownNamesByPkg: Map[jvm.QIdent, Map[jvm.Ident, jvm.Type.Qualified]] = result.files
      .groupBy(_.pkg)
      .map { case (pkg, files) =>
        pkg -> files.flatMap { f =>
          f.secondaryTypes.map(st => st.value.name -> st) :+ (f.tpe.value.name -> f.tpe)
        }.toMap
      }

    // Convert files to RelPath -> String map for FileSync
    val fileMap: Map[RelPath, String] = result.files.map { file =>
      val pathParts = file.tpe.value.idents.map(_.value)
      val relativePath = RelPath(pathParts.init :+ s"${pathParts.last}.${lang.extension}")
      val fileWithImports = addPackageAndImports(lang, knownNamesByPkg, file)
      relativePath -> fileWithImports.contents.render(lang).asString
    }.toMap

    // Use FileSync to write files (delete old files to avoid conflicts)
    val synced = FileSync.syncStrings(
      folder = sourceDir,
      fileRelMap = fileMap,
      deleteUnknowns = FileSync.DeleteUnknowns.Yes(maxDepth = None),
      softWrite = FileSync.SoftWrite.Yes(Set.empty)
    )

    val changed = synced.filter { case (_, status) => status != FileSync.Synced.Unchanged }
    println(s"Generated ${result.files.size} files (${changed.size} changed):")
    changed.foreach { case (path, status) =>
      println(s"  - $status: ${sourceDir.relativize(path)}")
    }
  }

  /** Generate code with framework integration (Spring/Quarkus) */
  private def generateCodeWithFramework(
      schemasPath: Path,
      lang: Lang,
      outputDir: String,
      avroWireFormat: AvroWireFormat,
      frameworkIntegration: FrameworkIntegration,
      effectType: EffectType
  ): Unit = {
    val projectDir = buildDir.resolve(outputDir)
    val sourceDir = projectDir.resolve("generated-and-checked-in")

    println(s"Output directory: $sourceDir")

    val standardHeaders = HeaderSchema(
      List(
        HeaderField("correlationId", HeaderType.UUID, required = true),
        HeaderField("timestamp", HeaderType.Instant, required = true),
        HeaderField("source", HeaderType.String, required = false)
      )
    )

    val options = AvroOptions
      .default(
        pkg = jvm.QIdent(List(jvm.Ident("com"), jvm.Ident("example"), jvm.Ident("events"))),
        schemaSource = SchemaSource.Directory(schemasPath)
      )
      .copy(
        avroWireFormat = avroWireFormat,
        effectType = effectType,
        headerSchemas = Map("standard" -> standardHeaders),
        topicHeaders = Map("order-events" -> "standard"),
        defaultHeaderSchema = Some("standard"),
        enablePreciseTypes = true,
        frameworkIntegration = frameworkIntegration,
        generateKafkaEvents = true,
        generateKafkaRpc = true
      )

    val result = AvroCodegen.generate(options, lang)

    if (result.errors.nonEmpty) {
      println("Errors:")
      result.errors.foreach(e => println(s"  - $e"))
      sys.exit(1)
    }

    if (result.files.isEmpty) {
      println("No files generated!")
      return
    }

    // Build known names by package for import resolution
    val knownNamesByPkg: Map[jvm.QIdent, Map[jvm.Ident, jvm.Type.Qualified]] = result.files
      .groupBy(_.pkg)
      .map { case (pkg, files) =>
        pkg -> files.flatMap { f =>
          f.secondaryTypes.map(st => st.value.name -> st) :+ (f.tpe.value.name -> f.tpe)
        }.toMap
      }

    // Convert files to RelPath -> String map for FileSync
    val fileMap: Map[RelPath, String] = result.files.map { file =>
      val pathParts = file.tpe.value.idents.map(_.value)
      val relativePath = RelPath(pathParts.init :+ s"${pathParts.last}.${lang.extension}")
      val fileWithImports = addPackageAndImports(lang, knownNamesByPkg, file)
      relativePath -> fileWithImports.contents.render(lang).asString
    }.toMap

    // Use FileSync to write files (delete old files to avoid conflicts)
    val synced = FileSync.syncStrings(
      folder = sourceDir,
      fileRelMap = fileMap,
      deleteUnknowns = FileSync.DeleteUnknowns.Yes(maxDepth = None),
      softWrite = FileSync.SoftWrite.Yes(Set.empty)
    )

    val changed = synced.filter { case (_, status) => status != FileSync.Synced.Unchanged }
    println(s"Generated ${result.files.size} files (${changed.size} changed):")
    changed.foreach { case (path, status) =>
      println(s"  - $status: ${sourceDir.relativize(path)}")
    }
  }
}
