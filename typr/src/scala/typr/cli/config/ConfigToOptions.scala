package typr.cli.config

import io.circe.Encoder
import typr.*
import typr.avro.*
import typr.cli.util.PatternMatcher
import typr.config.generated.*
import typr.effects.EffectType
import typr.grpc.*
import typr.internal.codegen.{LangJava, LangKotlin, LangScala, TypeSupportKotlin}
import typr.openapi.codegen.JacksonSupport

object ConfigToOptions {

  case class BoundaryConfig(
      name: String,
      boundaryType: String,
      schemaMode: SchemaMode,
      selector: Selector,
      sqlScriptsPath: Option[String],
      schemaSqlPath: Option[String],
      typeDefinitions: TypeDefinitions,
      typeOverride: TypeOverride,
      openEnumsSelector: Selector,
      precisionTypesSelector: Selector
  )

  case class OutputConfig(
      name: String,
      path: String,
      sourcePatterns: List[String],
      options: Options,
      effectType: Option[String],
      framework: Option[String]
  )

  def convertDatabaseBoundary(name: String, dbConfig: DatabaseBoundary): Either[String, BoundaryConfig] = {
    val schemaMode = parseSchemaMode(dbConfig.schema_mode)
    val selector = buildSelectorFromBoundary(dbConfig.selectors, dbConfig.schemas)
    val typeDefinitions = buildTypeDefinitions(dbConfig.types)
    val typeOverride = buildTypeOverride(dbConfig.type_override)
    val openEnumsSelector = PatternMatcher.fromFeatureMatcherDefaultNone(dbConfig.selectors.flatMap(_.open_enums))
    val precisionTypesSelector = PatternMatcher.fromFeatureMatcherDefaultNone(dbConfig.selectors.flatMap(_.precision_types))

    Right(
      BoundaryConfig(
        name = name,
        boundaryType = dbConfig.`type`.getOrElse("unknown"),
        schemaMode = schemaMode,
        selector = selector,
        sqlScriptsPath = dbConfig.sql_scripts,
        schemaSqlPath = dbConfig.schema_sql,
        typeDefinitions = typeDefinitions,
        typeOverride = typeOverride,
        openEnumsSelector = openEnumsSelector,
        precisionTypesSelector = precisionTypesSelector
      )
    )
  }

  def convertDuckDbBoundary(name: String, duckConfig: DuckdbBoundary): Either[String, BoundaryConfig] = {
    val selector = buildSelectorFromBoundary(duckConfig.selectors, None)
    val typeDefinitions = buildTypeDefinitions(duckConfig.types)
    val openEnumsSelector = PatternMatcher.fromFeatureMatcherDefaultNone(duckConfig.selectors.flatMap(_.open_enums))
    val precisionTypesSelector = PatternMatcher.fromFeatureMatcherDefaultNone(duckConfig.selectors.flatMap(_.precision_types))

    Right(
      BoundaryConfig(
        name = name,
        boundaryType = "duckdb",
        schemaMode = SchemaMode.SingleSchema("main"),
        selector = selector,
        sqlScriptsPath = duckConfig.sql_scripts,
        schemaSqlPath = duckConfig.schema_sql,
        typeDefinitions = typeDefinitions,
        typeOverride = TypeOverride.Empty,
        openEnumsSelector = openEnumsSelector,
        precisionTypesSelector = precisionTypesSelector
      )
    )
  }

  def convertAvroBoundary(
      name: String,
      config: AvroBoundary,
      pkg: jvm.QIdent,
      effectType: Option[String],
      framework: Option[String]
  ): Either[String, AvroOptions] = {
    val schemaSource = (config.schema, config.schemas, config.schema_registry) match {
      case (Some(path), _, _) =>
        typr.avro.SchemaSource.Directory(java.nio.file.Path.of(path))
      case (_, Some(paths), _) =>
        typr.avro.SchemaSource.Multi(paths.map(p => typr.avro.SchemaSource.Directory(java.nio.file.Path.of(p))))
      case (_, _, Some(url)) =>
        typr.avro.SchemaSource.Registry(url)
      case _ =>
        typr.avro.SchemaSource.Directory(java.nio.file.Path.of("."))
    }

    val wireFormat = config.wire_format match {
      case Some("binary") => AvroWireFormat.BinaryEncoded
      case Some("json")   => AvroWireFormat.JsonEncoded(JacksonSupport)
      case _              => AvroWireFormat.ConfluentRegistry
    }

    val resolvedEffectType = toEffectType(effectType)
    val frameworkIntegration = toAvroFramework(framework)

    val topicKeys = config.topic_keys.getOrElse(Map.empty).map { case (topic, json) =>
      topic -> parseKeyType(json)
    }

    val defaultKeyType = config.default_key_type.map(parseKeyType).getOrElse(KeyType.StringKey)

    val headerSchemas = config.header_schemas.getOrElse(Map.empty).map { case (name, hs) =>
      name -> typr.avro.HeaderSchema(
        fields = hs.fields.map { hf =>
          typr.avro.HeaderField(
            name = hf.name,
            headerType = toHeaderType(hf.`type`),
            required = hf.required.getOrElse(true)
          )
        }
      )
    }

    val schemaEvolution = config.schema_evolution match {
      case Some("all_versions")    => SchemaEvolution.AllVersions
      case Some("with_migrations") => SchemaEvolution.WithMigrations
      case _                       => SchemaEvolution.LatestOnly
    }

    val compatibilityMode = config.compatibility_mode match {
      case Some("forward") => CompatibilityMode.Forward
      case Some("full")    => CompatibilityMode.Full
      case Some("none")    => CompatibilityMode.None
      case _               => CompatibilityMode.Backward
    }

    Right(
      AvroOptions(
        pkg = pkg,
        avroWireFormat = wireFormat,
        schemaSource = schemaSource,
        generateRecords = config.generate_records.getOrElse(true),
        generateSerdes = config.generate_serdes.getOrElse(true),
        generateProducers = config.generate_producers.getOrElse(true),
        generateConsumers = config.generate_consumers.getOrElse(true),
        generateTopicBindings = config.generate_topic_bindings.getOrElse(true),
        generateHeaders = config.generate_headers.getOrElse(true),
        generateSchemaValidator = config.generate_schema_validator.getOrElse(true),
        generateProtocols = config.generate_protocols.getOrElse(true),
        generateUnionTypes = config.generate_union_types.getOrElse(true),
        effectType = resolvedEffectType,
        topicMapping = config.topic_mapping.getOrElse(Map.empty),
        topicGroups = config.topic_groups.getOrElse(Map.empty),
        topicKeys = topicKeys,
        defaultKeyType = defaultKeyType,
        headerSchemas = headerSchemas,
        topicHeaders = config.topic_headers.getOrElse(Map.empty),
        defaultHeaderSchema = config.default_header_schema,
        schemaEvolution = schemaEvolution,
        compatibilityMode = compatibilityMode,
        markNewFields = config.mark_new_fields.getOrElse(false),
        enablePreciseTypes = config.enable_precise_types.getOrElse(false),
        frameworkIntegration = frameworkIntegration,
        generateKafkaEvents = config.generate_kafka_events.getOrElse(frameworkIntegration != FrameworkIntegration.None),
        generateKafkaRpc = config.generate_kafka_rpc.getOrElse(frameworkIntegration != FrameworkIntegration.None)
      )
    )
  }

  def convertGrpcBoundary(
      name: String,
      config: GrpcBoundary,
      pkg: jvm.QIdent,
      effectType: Option[String],
      framework: Option[String]
  ): Either[String, GrpcOptions] = {
    val protoSource = config.descriptor_set match {
      case Some(path) =>
        ProtoSource.DescriptorSet(java.nio.file.Path.of(path))
      case scala.None =>
        val path = config.proto_path.getOrElse("proto/")
        val includePaths = config.include_paths.getOrElse(Nil).map(java.nio.file.Path.of(_))
        ProtoSource.Directory(java.nio.file.Path.of(path), includePaths)
    }

    val resolvedEffectType = toEffectType(effectType)
    val frameworkIntegration = toGrpcFramework(framework)

    Right(
      GrpcOptions(
        pkg = pkg,
        protoSource = protoSource,
        generateMessages = config.generate_messages.getOrElse(true),
        generateServices = config.generate_services.getOrElse(true),
        generateServers = config.generate_servers.getOrElse(true),
        generateClients = config.generate_clients.getOrElse(true),
        effectType = resolvedEffectType,
        frameworkIntegration = frameworkIntegration
      )
    )
  }

  def convertOutput(
      name: String,
      output: Output,
      globalTypes: Option[Map[String, BridgeType]]
  ): Either[String, OutputConfig] = {
    val scalaJson = output.scala
    val dialect = scalaJson.flatMap(_.hcursor.get[String]("dialect").toOption).getOrElse("scala3")
    val dsl = scalaJson.flatMap(_.hcursor.get[String]("dsl").toOption).getOrElse("scala")
    val useNativeTypes = scalaJson.flatMap(_.hcursor.get[Boolean]("use_native_types").toOption).getOrElse(true)

    val sourcePatterns = output.sources match {
      case None                         => List("*")
      case Some(s: StringOrArrayString) => List(s.value)
      case Some(a: StringOrArrayArray)  => a.value
    }

    val globalTypeDefinitions = buildTypeDefinitions(globalTypes)

    for {
      lang <- toLang(output.language, dialect, dsl, useNativeTypes)
      dbLib <- toDbLib(output.db_lib, dsl)
    } yield {
      val jsonLibs = output.json.flatMap(toJsonLib).toList
      val matchers = output.matchers
      val effectivePrecisionTypes = if (isLegacyDbLib(dbLib)) Selector.None else Selector.All

      val bridgeCompositeTypes = buildBridgeCompositeTypes(globalTypes)

      val options = Options(
        pkg = output.`package`,
        lang = lang,
        dbLib = Some(dbLib),
        jsonLibs = jsonLibs,
        typeOverride = TypeOverride.Empty,
        nullabilityOverride = NullabilityOverride.Empty,
        generateMockRepos = PatternMatcher.fromFeatureMatcher(matchers.flatMap(_.mock_repos)),
        enablePrimaryKeyType = PatternMatcher.fromFeatureMatcher(matchers.flatMap(_.primary_key_types)),
        enableFieldValue = PatternMatcher.fromFeatureMatcherDefaultNone(matchers.flatMap(_.field_values)),
        enableTestInserts = PatternMatcher.fromFeatureMatcherDefaultNone(matchers.flatMap(_.test_inserts)),
        readonlyRepo = PatternMatcher.fromFeatureMatcherDefaultNone(matchers.flatMap(_.readonly)),
        enableDsl = true,
        schemaMode = SchemaMode.MultiSchema,
        openEnums = Selector.None,
        enablePreciseTypes = effectivePrecisionTypes,
        typeDefinitions = globalTypeDefinitions,
        bridgeCompositeTypes = bridgeCompositeTypes
      )

      OutputConfig(
        name = name,
        path = output.path,
        sourcePatterns = sourcePatterns,
        options = options,
        effectType = output.effect_type,
        framework = output.framework
      )
    }
  }

  def matchesSource(sourceName: String, patterns: List[String]): Boolean = {
    patterns.exists { pattern =>
      if (pattern == "*") true
      else if (pattern.contains("*") || pattern.contains("?")) {
        val regex = pattern.replace(".", "\\.").replace("*", ".*").replace("?", ".")
        sourceName.matches(regex)
      } else {
        sourceName == pattern
      }
    }
  }

  private def toEffectType(s: Option[String]): EffectType = s match {
    case Some("completable_future") => EffectType.CompletableFuture
    case Some("reactor_mono")       => EffectType.ReactorMono
    case Some("mutiny_uni")         => EffectType.MutinyUni
    case Some("cats_io")            => EffectType.CatsIO
    case Some("zio")                => EffectType.ZIO
    case _                          => EffectType.Blocking
  }

  private def toAvroFramework(s: Option[String]): FrameworkIntegration = s match {
    case Some("spring")  => FrameworkIntegration.Spring
    case Some("quarkus") => FrameworkIntegration.Quarkus
    case Some("cats")    => FrameworkIntegration.Cats
    case _               => FrameworkIntegration.None
  }

  private def toGrpcFramework(s: Option[String]): GrpcFrameworkIntegration = s match {
    case Some("spring")  => GrpcFrameworkIntegration.Spring
    case Some("quarkus") => GrpcFrameworkIntegration.Quarkus
    case Some("cats")    => GrpcFrameworkIntegration.Cats
    case _               => GrpcFrameworkIntegration.None
  }

  private def parseKeyType(json: io.circe.Json): KeyType = {
    json.asString match {
      case Some("string") => KeyType.StringKey
      case Some("uuid")   => KeyType.UUIDKey
      case Some("long")   => KeyType.LongKey
      case Some("int")    => KeyType.IntKey
      case Some("bytes")  => KeyType.BytesKey
      case _ =>
        json.hcursor.downField("schema").as[String] match {
          case Right(schemaName) => KeyType.SchemaKey(schemaName)
          case Left(_)           => KeyType.StringKey
        }
    }
  }

  private def toHeaderType(s: String): HeaderType = s match {
    case "uuid"    => HeaderType.UUID
    case "instant" => HeaderType.Instant
    case "long"    => HeaderType.Long
    case "int"     => HeaderType.Int
    case "boolean" => HeaderType.Boolean
    case _         => HeaderType.String
  }

  private def toLang(s: String, dialect: String, dsl: String, useNativeTypes: Boolean): Either[String, Lang] = s.toLowerCase match {
    case "java"   => Right(LangJava)
    case "kotlin" => Right(LangKotlin(TypeSupportKotlin))
    case "scala" =>
      val scalaDialect = dialect.toLowerCase match {
        case "scala2" | "scala2.13" => Dialect.Scala2XSource3
        case "scala3" | "scala3.3"  => Dialect.Scala3
        case _                      => Dialect.Scala3
      }
      val typeSupport = if (useNativeTypes) TypeSupportScala else TypeSupportJava
      dsl.toLowerCase match {
        case "legacy" => Right(LangScala.legacyDsl(scalaDialect, typeSupport))
        case "java"   => Right(LangScala.javaDsl(scalaDialect, typeSupport))
        case "scala"  => Right(LangScala.scalaDsl(scalaDialect, typeSupport))
        case _        => Right(LangScala.scalaDsl(scalaDialect, typeSupport))
      }
    case other => Left(s"Unknown language: $other")
  }

  private def toDbLib(s: Option[String], dsl: String): Either[String, DbLibName] = s.map(_.toLowerCase) match {
    case None | Some("foundations") => Right(DbLibName.Typo)
    case Some("anorm")              => Right(DbLibName.Anorm)
    case Some("doobie")             => Right(DbLibName.Doobie)
    case Some("zio-jdbc")           => Right(DbLibName.ZioJdbc)
    case Some(other)                => Left(s"Unknown db_lib: $other")
  }

  private def isLegacyDbLib(dbLib: DbLibName): Boolean = dbLib match {
    case DbLibName.Anorm | DbLibName.Doobie | DbLibName.ZioJdbc => true
    case _                                                      => false
  }

  private def toJsonLib(s: String): Option[JsonLibName] = s.toLowerCase match {
    case "jackson"   => Some(JsonLibName.Jackson)
    case "circe"     => Some(JsonLibName.Circe)
    case "play-json" => Some(JsonLibName.PlayJson)
    case "zio-json"  => Some(JsonLibName.ZioJson)
    case _           => None
  }

  private def parseSchemaMode(s: Option[String]): SchemaMode = s match {
    case None                                      => SchemaMode.MultiSchema
    case Some("multi_schema")                      => SchemaMode.MultiSchema
    case Some(s) if s.startsWith("single_schema:") => SchemaMode.SingleSchema(s.stripPrefix("single_schema:"))
    case Some(_)                                   => SchemaMode.MultiSchema
  }

  private def buildSelectorFromBoundary(selectors: Option[BoundarySelectors], schemas: Option[List[String]]): Selector = {
    val schemaSelector = schemas match {
      case None | Some(Nil) => Selector.ExcludePostgresInternal
      case Some(list)       => Selector.schemas(list*)
    }

    val tableSelector = selectors.flatMap(_.tables) match {
      case None    => Selector.All
      case Some(m) => PatternMatcher.fromMatcherValue(m)
    }

    val excludeSelector = selectors.flatMap(_.exclude_tables) match {
      case None | Some(Nil) => Selector.All
      case Some(list)       => !Selector.relationNames(list*)
    }

    schemaSelector and tableSelector and excludeSelector
  }

  private def buildTypeDefinitions(types: Option[Map[String, BridgeType]]): TypeDefinitions =
    TypeDefinitions(
      types
        .getOrElse(Map.empty)
        .collect { case (name, st: FieldType) =>
          TypeEntry(
            name = name,
            db = convertDbMatch(st.db),
            model = convertModelMatch(st.model),
            api = convertApiMatch(st.api)
          )
        }
        .toList
    )

  private def buildBridgeCompositeTypes(types: Option[Map[String, BridgeType]]): Map[String, BridgeCompositeType] =
    types
      .getOrElse(Map.empty)
      .collect { case (name, ct: DomainType) =>
        val fields = ct.fields.toList.map { case (fieldName, fieldType) =>
          val (typeName, nullable, array, description) = fieldType match {
            case FieldSpecString(value) =>
              val parsed = parseCompactFieldType(value)
              (parsed._1, parsed._2, parsed._3, None)
            case fd: FieldSpecObject =>
              (fd.`type`, fd.nullable.getOrElse(false), fd.array.getOrElse(false), fd.description)
          }
          BridgeCompositeField(
            name = fieldName,
            typeName = typeName,
            nullable = nullable,
            array = array,
            description = description
          )
        }

        val projections = ct.projections.getOrElse(Map.empty).map { case (key, proj) =>
          val (sourceName, entityPath) = key.split(":").toList match {
            case src :: rest => (src, rest.mkString(":"))
            case _           => (key, proj.entity.getOrElse(""))
          }
          key -> BridgeProjection(
            sourceName = sourceName,
            entityPath = proj.entity.getOrElse(entityPath),
            mode = BridgeProjectionMode.fromString(proj.mode.getOrElse("superset")),
            mappings = proj.mappings.getOrElse(Map.empty),
            exclude = proj.exclude.getOrElse(Nil).toSet,
            includeExtra = proj.include_extra.getOrElse(Nil),
            readonly = proj.readonly.getOrElse(false)
          )
        }

        val genOpts = ct.generate.getOrElse(DomainGenerateOptions(None, None, None, None, None, None))

        name -> BridgeCompositeType(
          name = name,
          description = ct.description,
          fields = fields,
          projections = projections,
          generateCanonical = genOpts.canonical.getOrElse(true),
          generateMappers = genOpts.mappers.getOrElse(true),
          generateInterface = genOpts.interface.getOrElse(false),
          generateBuilder = genOpts.builder.getOrElse(false),
          generateCopy = genOpts.copy.getOrElse(true)
        )
      }

  private def parseCompactFieldType(syntax: String): (String, Boolean, Boolean) = {
    val trimmed = syntax.trim
    val isArray = trimmed.endsWith("[]")
    val withoutArray = if (isArray) trimmed.dropRight(2) else trimmed
    val isNullable = withoutArray.endsWith("?")
    val typeName = if (isNullable) withoutArray.dropRight(1) else withoutArray
    (typeName, isNullable, isArray)
  }

  private def buildTypeOverride(overrides: Option[Map[String, String]]): TypeOverride =
    overrides match {
      case None                     => TypeOverride.Empty
      case Some(map) if map.isEmpty => TypeOverride.Empty
      case Some(map) =>
        TypeOverride.relation {
          case (relation, col) if map.contains(s"$relation.$col") =>
            map(s"$relation.$col")
        }
    }

  private def convertDbMatch(db: Option[typr.config.generated.DbMatch]): typr.DbMatch =
    db match {
      case None => typr.DbMatch.Empty
      case Some(m) =>
        typr.DbMatch(
          database = stringOrArrayToList(m.source),
          schema = stringOrArrayToList(m.schema),
          table = stringOrArrayToList(m.table),
          column = stringOrArrayToList(m.column),
          dbType = stringOrArrayToList(m.db_type),
          domain = stringOrArrayToList(m.domain),
          primaryKey = m.primary_key,
          nullable = m.nullable,
          references = stringOrArrayToList(m.references),
          comment = stringOrArrayToList(m.comment),
          annotation = stringOrArrayToList(m.annotation)
        )
    }

  private def convertModelMatch(model: Option[typr.config.generated.ModelMatch]): typr.ModelMatch =
    model match {
      case None => typr.ModelMatch.Empty
      case Some(m) =>
        typr.ModelMatch(
          spec = stringOrArrayToList(m.source),
          schema = stringOrArrayToList(m.schema),
          name = stringOrArrayToList(m.name),
          jsonPath = stringOrArrayToList(m.json_path),
          schemaType = stringOrArrayToList(m.schema_type),
          format = stringOrArrayToList(m.format),
          required = m.required,
          extension = m.extension.getOrElse(Map.empty)
        )
    }

  private def convertApiMatch(api: Option[typr.config.generated.ApiMatch]): typr.ApiMatch =
    api match {
      case None => typr.ApiMatch.Empty
      case Some(m) =>
        typr.ApiMatch(
          location = m.location.getOrElse(Nil).flatMap(locationFromString),
          spec = stringOrArrayToList(m.source),
          operationId = stringOrArrayToList(m.operation_id),
          httpMethod = stringOrArrayToList(m.http_method),
          path = stringOrArrayToList(m.path),
          name = stringOrArrayToList(m.name),
          required = m.required,
          extension = m.extension.getOrElse(Map.empty)
        )
    }

  private def stringOrArrayToList(soa: Option[StringOrArray]): List[String] = soa match {
    case None                         => Nil
    case Some(s: StringOrArrayString) => List(s.value)
    case Some(a: StringOrArrayArray)  => a.value
  }

  private def locationFromString(s: String): Option[ApiLocation] = s.toLowerCase match {
    case "path"   => Some(ApiLocation.Path)
    case "query"  => Some(ApiLocation.Query)
    case "header" => Some(ApiLocation.Header)
    case "cookie" => Some(ApiLocation.Cookie)
    case _        => None
  }

  type SourceConfig = BoundaryConfig

  def convertDatabaseSource(name: String, dbConfig: DatabaseSource): Either[String, BoundaryConfig] = {
    val json = Encoder[DatabaseSource].apply(dbConfig)
    json.as[DatabaseBoundary] match {
      case Right(boundary) => convertDatabaseBoundary(name, boundary)
      case Left(err)       => Left(s"Failed to convert database source: ${err.getMessage}")
    }
  }

  def convertDuckDbSource(name: String, duckConfig: DuckdbSource): Either[String, BoundaryConfig] = {
    val json = Encoder[DuckdbSource].apply(duckConfig)
    json.as[DuckdbBoundary] match {
      case Right(boundary) => convertDuckDbBoundary(name, boundary)
      case Left(err)       => Left(s"Failed to convert DuckDB source: ${err.getMessage}")
    }
  }
}
