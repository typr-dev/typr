package typr

import typr.internal.external.{ExternalTools, ExternalToolsConfig}
import typr.internal.pg.OpenEnum
import typr.internal.sqlfiles.SqlFileReader
import typr.internal.{FileSync, TypeAligner, TypeMatcher, TypoType, generate}
import typr.internal.codegen.{CodeInterpolator, DbAdapter, MariaDbAdapter, PostgresAdapter, toCode}
import typr.openapi.OpenApiCodegen
import typr.openapi.parser.OpenApiParser

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.Duration

/** Result of generating code from a SchemaSource */
case class SourceGenerated(
    source: SchemaSource,
    generated: List[Generated]
)

/** Unified code generation from multiple schema sources.
  *
  * This is the main entry point for generating code from a combination of databases and OpenAPI specs, with shared TypeDefinitions that can match across all sources.
  */
object generateFromConfig {

  /** Generate code from all configured sources.
    *
    * @param config
    *   Generation configuration with sources and shared settings
    * @return
    *   List of generated results, one per source
    */
  def apply(config: GenerateConfig): List[SourceGenerated] = {
    implicit val ec: ExecutionContext = ExecutionContext.global

    // Initialize external tools once for all sources
    val externalTools = ExternalTools.init(config.logger, ExternalToolsConfig.default)

    // If sharedPkg is set and we have TypeDefinitions, run alignment across sources
    val alignmentInfo: Option[AlignmentInfo] = config.sharedPkg.flatMap { sharedPkg =>
      if (config.typeDefinitions.isEmpty) None
      else Some(collectAlignmentInfo(config, sharedPkg, externalTools))
    }

    // Log alignment warnings
    alignmentInfo.foreach { info =>
      info.alignmentResult.warnings.foreach(w => config.logger.warn(w))
    }

    // Generate shared types if we have aligned entries
    val sharedGenerated: List[SourceGenerated] = (config.sharedPkg, config.sharedTargetFolder, alignmentInfo) match {
      case (Some(sharedPkg), Some(sharedFolder), Some(info)) if info.alignmentResult.alignedEntries.nonEmpty =>
        // Log aligned types
        info.alignmentResult.alignedEntries.foreach { aligned =>
          val sources = aligned.sourceAlignments.map(sa => s"${sa.sourceName}(${sa.locations.mkString(", ")})").mkString(", ")
          config.logger.info(s"Aligned type '${aligned.entry.name}' across sources: $sources")
        }

        // Generate shared type files
        val files = generateSharedTypes(config, sharedPkg, info)
        if (files.nonEmpty) {
          val generated = Generated(
            language = config.lang,
            folder = sharedFolder,
            testFolder = None,
            files = files.iterator
          )
          List(SourceGenerated(SchemaSource.Shared(sharedPkg, sharedFolder), generated))
        } else Nil
      case _ => Nil
    }

    // Process each source with alignment info
    val sourceGenerated = config.sources.map {
      case dbSource: SchemaSource.Database =>
        generateFromDatabase(config, dbSource, externalTools, alignmentInfo)

      case apiSource: SchemaSource.OpenApi =>
        generateFromOpenApi(config, apiSource, alignmentInfo)

      case parsedSource: SchemaSource.OpenApiParsed =>
        generateFromParsedOpenApi(config, parsedSource, alignmentInfo)

      case _: SchemaSource.Shared =>
        throw new IllegalArgumentException("SchemaSource.Shared is a synthetic source and should not be in config.sources")
    }

    sharedGenerated ++ sourceGenerated
  }

  /** Information collected for cross-source alignment */
  private case class AlignmentInfo(
      alignmentResult: TypeAligner.AlignmentResult,
      sharedPkg: jvm.QIdent,
      dbScanResultsBySource: Map[String, TypeMatcher.ScanResult],
      apiScanResultsBySource: Map[String, TypeMatcher.OpenApiScanResult]
  ) {

    /** Build a TypeOverride that maps aligned entries to the shared package */
    def sharedTypeOverride: TypeOverride = { (relation, colName) =>
      // Check if this column is part of an aligned entry
      alignmentResult.alignedEntries.flatMap { aligned =>
        aligned.sourceAlignments.flatMap { sa =>
          sa.locations.collectFirst {
            case loc if matchesLocation(loc, relation, colName) =>
              s"${sharedPkg.dotName}.${aligned.entry.name}"
          }
        }
      }.headOption
    }

    /** Get the names of all aligned entries (for filtering TypeDefinitions in OpenAPI codegen) */
    def alignedEntryNames: Set[String] =
      alignmentResult.alignedEntries.map(_.entry.name).toSet

    /** Build a map of field name -> shared type for OpenAPI sources */
    def apiTypeOverrides: Map[String, jvm.Type.Qualified] = {
      alignmentResult.alignedEntries.flatMap { aligned =>
        // Get field names from the API locations
        aligned.sourceAlignments.collect { case sa if sa.sourceKind.isInstanceOf[TypeAligner.SourceKind.OpenApi] => sa }.flatMap { sa =>
          // Also include the entry's API name patterns for matching
          val apiNames = aligned.entry.api.name
          val locationNames = sa.locations.map(_.split('.').last)
          (apiNames ++ locationNames).map(_.toLowerCase -> jvm.Type.Qualified(sharedPkg / jvm.Ident(aligned.entry.name)))
        }
      }.toMap
    }

    private def matchesLocation(location: String, relation: db.RelationName, colName: db.ColName): Boolean = {
      // Location format: "schema.table.column" or "table.column"
      val parts = location.split('.')
      parts.length match {
        case 3 =>
          val schema = parts(0)
          val table = parts(1)
          val col = parts(2)
          relation.schema.contains(schema) && relation.name == table && colName.value == col
        case 2 =>
          val table = parts(0)
          val col = parts(1)
          relation.name == table && colName.value == col
        case _ => false
      }
    }
  }

  /** Collect alignment info from all sources (databases and OpenAPI) */
  private def collectAlignmentInfo(
      config: GenerateConfig,
      sharedPkg: jvm.QIdent,
      externalTools: ExternalTools
  )(implicit ec: ExecutionContext): AlignmentInfo = {
    // Collect scan results from all database sources
    val dbSources = config.sources.collect { case db: SchemaSource.Database => db }

    val dbScanResults: List[(String, DbType, TypeMatcher.ScanResult)] = dbSources.flatMap { source =>
      val eventualMetaDb = MetaDb.fromDb(config.logger, source.dataSource, source.selector, source.schemaMode, externalTools)
      val metaDb = Await.result(eventualMetaDb, Duration.Inf)

      val tablesForTypeDefs = metaDb.relations.values.flatMap(_.get).collect { case t: db.Table => t }.toList
      val sharedTypesPackage = source.pkg / jvm.Ident("userdefined")

      val scanResult = TypeMatcher.scanTables(
        config.typeDefinitions,
        source.name,
        tablesForTypeDefs,
        sharedTypesPackage
      )

      Some((source.name, metaDb.dbType, scanResult))
    }

    // Collect scan results from all OpenAPI sources
    val apiSources = config.sources.collect {
      case api: SchemaSource.OpenApi       => api
      case api: SchemaSource.OpenApiParsed => api
    }

    val apiScanResults: List[(String, TypeMatcher.OpenApiScanResult)] = apiSources.flatMap {
      case api: SchemaSource.OpenApi =>
        OpenApiParser.parseFile(api.specPath) match {
          case Left(_)     => None
          case Right(spec) => Some((api.name, TypeMatcher.scanOpenApi(config.typeDefinitions, api.name, spec)))
        }
      case api: SchemaSource.OpenApiParsed =>
        Some((api.name, TypeMatcher.scanOpenApi(config.typeDefinitions, api.name, api.spec)))
      case _ => None // unreachable but needed for exhaustiveness
    }

    val dbScanResultsBySource = dbScanResults.map { case (name, _, result) => name -> result }.toMap
    val apiScanResultsBySource = apiScanResults.toMap

    // Run alignment with both database and OpenAPI sources
    val alignmentResult = TypeAligner.align(config.typeDefinitions, dbScanResults, apiScanResults)

    AlignmentInfo(alignmentResult, sharedPkg, dbScanResultsBySource, apiScanResultsBySource)
  }

  /** Generate shared type files for aligned entries */
  private def generateSharedTypes(
      config: GenerateConfig,
      sharedPkg: jvm.QIdent,
      info: AlignmentInfo
  ): List[jvm.File] = {
    import typr.internal.codegen.{addPackageAndImports, scaladoc, toCode}

    // Build map of source name -> source package
    val sourcePackages: Map[String, jvm.QIdent] = config.sources.collect { case db: SchemaSource.Database =>
      db.name -> db.pkg
    }.toMap

    info.alignmentResult.alignedEntries.map { aligned =>
      val tpe = jvm.Type.Qualified(sharedPkg / jvm.Ident(aligned.entry.name))

      val comments = scaladoc(
        List(
          s"Shared type `${aligned.entry.name}` aligned across sources:",
          aligned.sourceAlignments
            .map { sa =>
              val kindStr = sa.sourceKind match {
                case TypeAligner.SourceKind.Database(dbType) => s"${sa.sourceName} ($dbType)"
                case TypeAligner.SourceKind.OpenApi(_)       => s"${sa.sourceName} (OpenAPI)"
              }
              s"  - $kindStr: ${sa.locations.mkString(", ")}"
            }
            .mkString("\n")
        )
      )

      val value = jvm.Ident("value")

      // Determine the canonical JVM type from the aligned db type
      val underlyingJvmType: jvm.Type = canonicalJvmType(aligned.canonicalDbType, config.lang)

      // Jackson @JsonValue annotation for the value field
      val jsonValueAnnotation = jvm.Annotation(
        jvm.Type.Qualified("com.fasterxml.jackson.annotation.JsonValue"),
        Nil
      )

      // Generate database instances for each source alignment
      val dbInstances = aligned.sourceAlignments.flatMap { sa =>
        generateDbInstance(config.lang, tpe, underlyingJvmType, sa, sourcePackages.get(sa.sourceName))
      }

      val cls = jvm.Adt.Record(
        annotations = Nil,
        constructorAnnotations = Nil,
        isWrapper = true,
        privateConstructor = false,
        comments = comments,
        name = tpe,
        tparams = Nil,
        params = List(jvm.Param(List(jsonValueAnnotation), jvm.Comments.Empty, value, underlyingJvmType, None)),
        implicitParams = Nil,
        `extends` = None,
        implements = Nil,
        members = Nil,
        staticMembers = dbInstances
      )

      val file = jvm.File(tpe, cls, secondaryTypes = Nil, scope = Scope.Main)

      // Build known names map for import resolution
      val knownNamesByPkg: Map[jvm.QIdent, Map[jvm.Ident, jvm.Type.Qualified]] = Map(
        sharedPkg -> Map(tpe.value.name -> tpe)
      )
      val withImports = addPackageAndImports(config.lang, knownNamesByPkg, file)
      withImports.copy(contents = jvm.Code.Str(config.fileHeader) ++ withImports.contents)
    }
  }

  /** Generate database type instance for a source alignment using DbAdapter infrastructure.
    *
    * Uses the same patterns as DbLibFoundations.generateUnpackInstances for consistency.
    */
  @annotation.nowarn("msg=unused")
  private def generateDbInstance(
      lang: Lang,
      wrapperType: jvm.Type.Qualified,
      underlyingJvmType: jvm.Type,
      alignment: TypeAligner.SourceAlignment,
      sourcePkg: Option[jvm.QIdent]
  ): Option[jvm.Given] = {
    val value = jvm.Ident("value")

    // Get the adapter for this database type (OpenAPI sources don't need db instances)
    val adapter: DbAdapter = alignment.sourceKind match {
      case TypeAligner.SourceKind.Database(DbType.PostgreSQL) => PostgresAdapter.NoTimestampCasts
      case TypeAligner.SourceKind.Database(DbType.MariaDB)    => MariaDbAdapter
      case _                                                  => return None // Skip OpenAPI and unsupported databases
    }

    val body: jvm.Code = alignment.transform match {
      case TypoType.AlignmentTransform.Direct =>
        // Direct: simple bimap from base type lookup
        // e.g., MariaTypes.bool.bimap(IsActive::new, IsActive::value)
        val baseTypeLookup = adapter.lookupTypeByDbType(alignment.sampleDbType, adapter.Types, new Naming(jvm.QIdent("unused"), lang), lang.typeSupport)
        code"$baseTypeLookup.bimap(${jvm.ConstructorMethodRef(wrapperType)}, ${jvm.FieldGetterRef(wrapperType, value)})"

      case TypoType.AlignmentTransform.Unpack =>
        // Unpack: bimap through intermediate domain type using proper AST nodes
        // e.g., Name.pgType.bimap(x -> new FirstName(x.value()), w -> new Name(w.value()))
        val domainType: Option[jvm.Type.Qualified] = (alignment.sampleDbType, sourcePkg) match {
          case (db.PgType.DomainRef(relName, _, _), Some(pkg)) =>
            val schemaStr = relName.schema.getOrElse("public")
            val schemaIdent = jvm.Ident(if (schemaStr == "public") "public_" else schemaStr)
            Some(jvm.Type.Qualified(pkg / schemaIdent / jvm.Ident(toCamelCase(relName.name))))
          case _ => None
        }

        domainType match {
          case Some(domain) =>
            // Use proper AST nodes for lambdas with method calls on lambda parameters
            val x = jvm.Ident("x")
            val w = jvm.Ident("w")
            // x -> new WrapperType(x.value()) - call .value() on lambda param x
            val toWrapper = jvm.Lambda(x, code"new $wrapperType($x.$value())")
            // w -> new DomainType(w.value()) - call .value() on lambda param w
            val fromWrapper = jvm.Lambda(w, code"new $domain($w.$value())")
            code"${jvm.Import(domain)}$domain.${adapter.typeFieldName}.bimap($toWrapper, $fromWrapper)"
          case None =>
            // Fallback to direct if we can't resolve the domain
            val baseTypeLookup = adapter.lookupTypeByDbType(alignment.sampleDbType, adapter.Types, new Naming(jvm.QIdent("unused"), lang), lang.typeSupport)
            code"$baseTypeLookup.bimap(${jvm.ConstructorMethodRef(wrapperType)}, ${jvm.FieldGetterRef(wrapperType, value)})"
        }
    }

    Some(
      jvm.Given(
        tparams = Nil,
        name = adapter.typeFieldName,
        implicitParams = Nil,
        tpe = adapter.TypeClass.of(wrapperType),
        body = body
      )
    )
  }

  /** Convert snake_case or lowercase to CamelCase */
  private def toCamelCase(s: String): String =
    s.split("_").map(_.capitalize).mkString

  /** Map a canonical db.Type to the corresponding JVM primitive type.
    *
    * For shared types, we always want the underlying primitive type, not domain wrappers. E.g., a PostgreSQL domain Flag -> boolean should return lang.Boolean, not the Flag type.
    */
  private def canonicalJvmType(dbType: db.Type, lang: Lang): jvm.Type = {
    // Unwrap domain/alias types to their underlying primitive
    val unwrapped = unwrapDbType(dbType)

    val mapper = typr.internal.TypeMapperJvmNew(
      lang = lang,
      typeOverride = TypeOverride.Empty,
      nullabilityOverride = NullabilityOverride.Empty,
      naming = new Naming(jvm.QIdent("unused"), lang),
      duckDbStructLookup = Map.empty
    )
    mapper.baseType(unwrapped)
  }

  /** Unwrap domain/alias types to their underlying primitive type. */
  private def unwrapDbType(dbType: db.Type): db.Type = dbType match {
    case db.PgType.DomainRef(_, _, underlyingType)              => unwrapDbType(underlyingType)
    case db.SqlServerType.AliasTypeRef(_, _, underlyingType, _) => unwrapDbType(underlyingType)
    case db.DB2Type.DistinctType(_, underlying)                 => unwrapDbType(underlying)
    case other                                                  => other
  }

  /** Generate and write files from all configured sources.
    *
    * @param config
    *   Generation configuration
    * @param softWrite
    *   Whether to use soft write (skip unchanged files)
    * @return
    *   Map of path to sync status for each source
    */
  def applyAndWrite(
      config: GenerateConfig,
      softWrite: FileSync.SoftWrite
  ): List[(SchemaSource, Map[java.nio.file.Path, FileSync.Synced])] = {
    apply(config).map { result =>
      val synced = result.generated.flatMap { gen =>
        gen.overwriteFolder(softWrite)
      }.toMap
      (result.source, synced)
    }
  }

  @annotation.nowarn("msg=unused")
  private def generateFromDatabase(
      config: GenerateConfig,
      source: SchemaSource.Database,
      externalTools: ExternalTools,
      alignmentInfo: Option[AlignmentInfo]
  )(implicit ec: ExecutionContext): SourceGenerated = {
    val viewSelector = source.selector
    val eventualMetaDb = MetaDb.fromDb(config.logger, source.dataSource, viewSelector, source.schemaMode, externalTools)

    val eventualScripts = Future
      .sequence(
        source.scriptsPaths.map(p => SqlFileReader(config.logger, p, source.dataSource, externalTools))
      )
      .map(_.flatten)

    val combined = for {
      metaDb <- eventualMetaDb
      eventualOpenEnums = OpenEnum.find(source.dataSource, config.logger, viewSelector, openEnumSelector = source.openEnums, metaDb = metaDb)
      openEnums <- eventualOpenEnums
      scripts <- eventualScripts
    } yield {
      // If we have alignment info, combine the shared type override with config's typeOverride
      // This makes aligned entries reference the shared package instead of generating locally
      val effectiveTypeOverride = alignmentInfo match {
        case Some(info) => info.sharedTypeOverride.orElse(config.typeOverride)
        case None       => config.typeOverride
      }

      // For aligned entries, we don't want to generate local shared types
      // The TypeDefinitions should be filtered to exclude aligned entries
      val effectiveTypeDefinitions = alignmentInfo match {
        case Some(info) =>
          val alignedNames = info.alignmentResult.alignedEntries.map(_.entry.name).toSet
          config.typeDefinitions.filterNot(e => alignedNames.contains(e.name))
        case None =>
          config.typeDefinitions
      }

      // Build Options from config + source settings
      val options = Options(
        pkg = source.pkg.dotName,
        lang = config.lang,
        dbLib = Some(source.dbLib),
        jsonLibs = config.jsonLibs,
        silentBanner = true,
        logger = config.logger,
        fileHeader = config.fileHeader,
        typeOverride = effectiveTypeOverride,
        nullabilityOverride = source.nullabilityOverride,
        generateMockRepos = source.generateMockRepos,
        enablePrimaryKeyType = source.enablePrimaryKeyType,
        enableFieldValue = source.enableFieldValue,
        enableTestInserts = source.enableTestInserts,
        readonlyRepo = source.readonlyRepo,
        enableStreamingInserts = true,
        enableDsl = source.enableDsl,
        debugTypes = config.debugTypes,
        inlineImplicits = config.inlineImplicits,
        fixVerySlowImplicit = config.fixVerySlowImplicit,
        keepDependencies = config.keepDependencies,
        schemaMode = source.schemaMode,
        openEnums = source.openEnums,
        typeDefinitions = effectiveTypeDefinitions
      )

      val graph = ProjectGraph(
        name = source.name,
        target = source.targetFolder,
        testTarget = source.testTargetFolder,
        value = source.selector,
        scripts = scripts,
        downstream = Nil
      )

      generate.orThrow(options, metaDb, graph, openEnums)
    }

    val generated = Await.result(combined, Duration.Inf)
    SourceGenerated(source, generated)
  }

  private def generateFromOpenApi(
      config: GenerateConfig,
      source: SchemaSource.OpenApi,
      alignmentInfo: Option[AlignmentInfo]
  ): SourceGenerated = {
    // Parse the spec
    OpenApiParser.parseFile(source.specPath) match {
      case Left(errors) =>
        throw new RuntimeException(s"Failed to parse OpenAPI spec ${source.specPath}: ${errors.mkString(", ")}")
      case Right(parsedSpec) =>
        generateFromParsedOpenApi(
          config,
          SchemaSource.OpenApiParsed(
            name = source.name,
            spec = parsedSpec,
            pkg = source.pkg,
            targetFolder = source.targetFolder,
            testTargetFolder = source.testTargetFolder,
            options = source.options
          ),
          alignmentInfo
        )
    }
  }

  private def generateFromParsedOpenApi(
      config: GenerateConfig,
      source: SchemaSource.OpenApiParsed,
      alignmentInfo: Option[AlignmentInfo]
  ): SourceGenerated = {
    import typr.internal.codegen.addPackageAndImports

    // For aligned entries, filter them out from TypeDefinitions (they're in shared package)
    // and add fieldTypeOverrides to reference the shared types
    val (effectiveTypeDefinitions, extraFieldTypeOverrides) = alignmentInfo match {
      case Some(info) =>
        val alignedNames = info.alignedEntryNames
        val filtered = config.typeDefinitions.filterNot(e => alignedNames.contains(e.name))
        (filtered, info.apiTypeOverrides)
      case None =>
        (config.typeDefinitions, Map.empty[String, jvm.Type.Qualified])
    }

    // Merge config-level TypeDefinitions with source-level options
    val optionsWithTypeDefinitions = source.options.copy(
      typeDefinitions = source.options.typeDefinitions ++ effectiveTypeDefinitions,
      fieldTypeOverrides = source.options.fieldTypeOverrides ++ extraFieldTypeOverrides
    )

    val result = OpenApiCodegen.generateFromSpec(source.spec, optionsWithTypeDefinitions, config.lang)

    if (result.errors.nonEmpty) {
      throw new RuntimeException(s"OpenAPI generation errors for ${source.name}: ${result.errors.mkString(", ")}")
    }

    // Build known names by package for import resolution
    // Include shared types if we have alignment info
    val sharedTypeNames: Map[jvm.QIdent, Map[jvm.Ident, jvm.Type.Qualified]] = alignmentInfo match {
      case Some(info) =>
        val sharedTypes = info.alignedEntryNames.map { name =>
          jvm.Ident(name) -> jvm.Type.Qualified(info.sharedPkg / jvm.Ident(name))
        }.toMap
        Map(info.sharedPkg -> sharedTypes)
      case None => Map.empty
    }

    val knownNamesByPkg: Map[jvm.QIdent, Map[jvm.Ident, jvm.Type.Qualified]] = result.files
      .groupBy(_.pkg)
      .map { case (pkg, files) =>
        pkg -> files.flatMap { f =>
          f.secondaryTypes.map(st => st.value.name -> st) :+ (f.tpe.value.name -> f.tpe)
        }.toMap
      } ++ sharedTypeNames

    // Add imports and file header
    val filesWithImports = result.files.map { file =>
      val withImports = addPackageAndImports(config.lang, knownNamesByPkg, file)
      withImports.copy(contents = jvm.Code.Str(config.fileHeader) ++ withImports.contents)
    }

    // Create Generated wrapper using the apply that returns List[Generated]
    val generated = Generated(
      language = config.lang,
      folder = source.targetFolder,
      testFolder = source.testTargetFolder,
      files = filesWithImports.iterator
    )

    SourceGenerated(source, generated)
  }
}
