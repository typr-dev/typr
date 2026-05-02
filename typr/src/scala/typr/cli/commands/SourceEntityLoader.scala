package typr.cli.commands

import io.circe.Json
import typr.*
import typr.avro.AvroRecord
import typr.avro.AvroSchemaFile
import typr.avro.parser.AvroParser
import typr.bridge.SourceEntity
import typr.bridge.SourceEntityType
import typr.bridge.SourceField
import typr.bridge.model.SourceDeclaration
import typr.cli.config.ConfigParser
import typr.cli.config.ConfigToOptions
import typr.cli.config.ParsedSource
import typr.cli.config.TyprConfig
import typr.cli.commands.ProjectionFieldFormat
import typr.config.generated.DatabaseBoundary
import typr.config.generated.DuckdbBoundary
import typr.config.generated.GrpcBoundary
import typr.config.generated.JsonschemaBoundary
import typr.config.generated.OpenapiBoundary
import typr.grpc.ProtoFile
import typr.grpc.parser.ProtobufParser
import typr.internal.external.ExternalTools
import typr.internal.external.ExternalToolsConfig
import typr.jsonschema.JsonSchemaParser
import typr.openapi.ParsedSpec
import typr.openapi.parser.OpenApiParser

import java.nio.file.Files
import java.nio.file.Path
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

object SourceEntityLoader {

  sealed trait LoadedSource
  object LoadedSource {
    case class Database(metaDb: MetaDb, dataSource: TypoDataSource) extends LoadedSource
    case class Spec(spec: ParsedSpec) extends LoadedSource
    case class Avro(schemas: List[AvroSchemaFile]) extends LoadedSource
    case class Proto(protoFiles: List[ProtoFile]) extends LoadedSource
  }

  def loadSourceEntities(
      config: TyprConfig,
      sourceDeclarations: Map[String, Map[String, SourceDeclaration]],
      buildDir: Path,
      quiet: Boolean
  ): Either[String, Map[String, Map[String, SourceEntity]]] = {
    val allSourceNames = sourceDeclarations.values.flatMap(_.values.map(_.sourceName)).toSet
    val sources = config.sources.getOrElse(Map.empty)

    val errors = List.newBuilder[String]
    val result = scala.collection.mutable.Map[String, scala.collection.mutable.Map[String, SourceEntity]]()

    allSourceNames.foreach { sourceName =>
      sources.get(sourceName) match {
        case None =>
          errors += s"Source '$sourceName' referenced in domain types but not defined in config"
        case Some(sourceJson) =>
          loadSource(sourceName, sourceJson, buildDir, quiet) match {
            case Left(err) =>
              errors += s"Failed to load source '$sourceName': $err"
            case Right(loaded) =>
              val entityMap = result.getOrElseUpdate(sourceName, scala.collection.mutable.Map.empty)
              val neededEntities = sourceDeclarations.values.flatMap(_.values).filter(_.sourceName == sourceName).map(_.entityPath).toSet
              neededEntities.foreach { entityPath =>
                extractEntity(loaded, sourceName, entityPath) match {
                  case Right(entity) =>
                    entityMap(entityPath) = entity
                  case Left(err) =>
                    errors += s"Source '$sourceName', entity '$entityPath': $err"
                }
              }
              closeSource(loaded)
          }
      }
    }

    val errorList = errors.result()
    if (errorList.nonEmpty) {
      Left(errorList.mkString("\n"))
    } else {
      Right(result.map { case (k, v) => k -> v.toMap }.toMap)
    }
  }

  private def loadSource(
      sourceName: String,
      sourceJson: Json,
      buildDir: Path,
      quiet: Boolean
  ): Either[String, LoadedSource] = {
    ConfigParser.parseSource(sourceJson) match {
      case Left(err) => Left(err)
      case Right(parsed) =>
        parsed match {
          case ParsedSource.Database(dbConfig) =>
            loadDatabaseSource(sourceName, dbConfig)
          case ParsedSource.DuckDb(duckConfig) =>
            loadDuckDbSource(duckConfig, buildDir)
          case ParsedSource.OpenApi(openapiConfig) =>
            loadOpenApiSource(openapiConfig, buildDir)
          case ParsedSource.JsonSchema(jsonSchemaConfig) =>
            loadJsonSchemaSource(jsonSchemaConfig, buildDir)
          case ParsedSource.Avro(avroConfig) =>
            loadAvroSource(avroConfig, buildDir)
          case ParsedSource.Grpc(grpcConfig) =>
            loadProtoSource(grpcConfig, buildDir)
        }
    }
  }

  private def loadDatabaseSource(
      sourceName: String,
      dbConfig: DatabaseBoundary
  ): Either[String, LoadedSource] = {
    for {
      sourceConfig <- ConfigToOptions.convertDatabaseSource(sourceName, dbConfig)
      dataSource <- buildDataSource(dbConfig)
      metaDb <- {
        try {
          val logger = TypoLogger.Noop
          val externalTools = ExternalTools.init(logger, ExternalToolsConfig.default)
          val db = Await.result(
            MetaDb.fromDb(logger, dataSource, sourceConfig.selector, sourceConfig.schemaMode, externalTools),
            Duration.Inf
          )
          Right(db)
        } catch {
          case e: Exception =>
            closeDataSource(dataSource)
            Left(Option(e.getMessage).getOrElse(e.getClass.getSimpleName))
        }
      }
    } yield LoadedSource.Database(metaDb, dataSource)
  }

  private def loadDuckDbSource(
      duckConfig: DuckdbBoundary,
      buildDir: Path
  ): Either[String, LoadedSource] = {
    try {
      val sourceName = "duckdb"
      val sourceConfig = ConfigToOptions.convertDuckDbSource(sourceName, duckConfig) match {
        case Right(cfg) => cfg
        case Left(err)  => return Left(err)
      }
      val dataSource = TypoDataSource.hikariDuckDbInMemory(duckConfig.path)

      duckConfig.schema_sql.foreach { schemaPath =>
        val resolvedPath = buildDir.resolve(schemaPath)
        val conn = dataSource.ds.getConnection
        try {
          val stmt = conn.createStatement()
          val sql = Files.readString(resolvedPath)
          stmt.execute(sql)
          stmt.close()
        } finally {
          conn.close()
        }
      }

      val logger = TypoLogger.Noop
      val externalTools = ExternalTools.init(logger, ExternalToolsConfig.default)
      val metaDb = Await.result(
        MetaDb.fromDb(logger, dataSource, sourceConfig.selector, sourceConfig.schemaMode, externalTools),
        Duration.Inf
      )
      Right(LoadedSource.Database(metaDb, dataSource))
    } catch {
      case e: Exception =>
        Left(Option(e.getMessage).getOrElse(e.getClass.getSimpleName))
    }
  }

  private def loadOpenApiSource(
      openapiConfig: OpenapiBoundary,
      buildDir: Path
  ): Either[String, LoadedSource] = {
    val specPath = openapiConfig.spec
      .orElse(openapiConfig.specs.flatMap(_.headOption))
      .toRight("OpenAPI source requires 'spec' or 'specs' field")

    specPath.flatMap { sp =>
      val resolvedPath = buildDir.resolve(sp)
      if (!Files.exists(resolvedPath)) {
        Left(s"OpenAPI spec not found: $sp")
      } else {
        OpenApiParser.parseFile(resolvedPath) match {
          case Right(parsedSpec) => Right(LoadedSource.Spec(parsedSpec))
          case Left(errors)      => Left(s"Failed to parse OpenAPI spec: ${errors.mkString(", ")}")
        }
      }
    }
  }

  private def loadJsonSchemaSource(
      jsonSchemaConfig: JsonschemaBoundary,
      buildDir: Path
  ): Either[String, LoadedSource] = {
    val specPath = jsonSchemaConfig.spec
      .orElse(jsonSchemaConfig.specs.flatMap(_.headOption))
      .toRight("JSON Schema source requires 'spec' or 'specs' field")

    specPath.flatMap { sp =>
      val resolvedPath = buildDir.resolve(sp)
      if (!Files.exists(resolvedPath)) {
        Left(s"JSON Schema spec not found: $sp")
      } else {
        JsonSchemaParser.parseFile(resolvedPath) match {
          case Right(parsedSchema) =>
            val parsedSpec = typr.openapi.ParsedSpec(
              info = typr.openapi.SpecInfo(title = sp, version = "1.0.0", description = None),
              models = parsedSchema.models,
              sumTypes = parsedSchema.sumTypes,
              apis = Nil,
              webhooks = Nil,
              securitySchemes = Map.empty,
              warnings = Nil
            )
            Right(LoadedSource.Spec(parsedSpec))
          case Left(errors) =>
            Left(s"Failed to parse JSON Schema: ${errors.mkString(", ")}")
        }
      }
    }
  }

  private def loadAvroSource(
      avroConfig: typr.config.generated.AvroBoundary,
      buildDir: Path
  ): Either[String, LoadedSource] = {
    val schemaPath = avroConfig.schema
      .orElse(avroConfig.schemas.flatMap(_.headOption))
      .toRight("Avro source requires 'schema' or 'schemas' field")

    schemaPath.flatMap { sp =>
      val resolvedPath = buildDir.resolve(sp)
      if (!Files.exists(resolvedPath)) {
        Left(s"Avro schema not found: $sp")
      } else {
        val parseResult = if (Files.isDirectory(resolvedPath)) {
          AvroParser.parseDirectory(resolvedPath)
        } else {
          AvroParser.parseFile(resolvedPath).map(List(_))
        }
        parseResult match {
          case Right(schemas) => Right(LoadedSource.Avro(schemas))
          case Left(error)    => Left(s"Failed to parse Avro schema: ${error.message}")
        }
      }
    }
  }

  private def loadProtoSource(
      grpcConfig: GrpcBoundary,
      buildDir: Path
  ): Either[String, LoadedSource] = {
    val protoPath = grpcConfig.proto_path.orElse(grpcConfig.proto_paths.flatMap(_.headOption))

    protoPath match {
      case Some(pp) =>
        val resolvedPath = buildDir.resolve(pp)
        if (!Files.exists(resolvedPath)) {
          Left(s"Proto path not found: $pp")
        } else {
          val includePaths = grpcConfig.include_paths.getOrElse(Nil).map(buildDir.resolve)
          val parseResult = if (Files.isDirectory(resolvedPath)) {
            ProtobufParser.parseDirectory(resolvedPath, includePaths)
          } else {
            ProtobufParser.parseDescriptorSet(resolvedPath)
          }
          parseResult match {
            case Right(protoFiles) => Right(LoadedSource.Proto(protoFiles.map(ProtobufParser.resolveMapFields)))
            case Left(error)       => Left(s"Failed to parse Proto: ${error.message}")
          }
        }
      case None =>
        grpcConfig.descriptor_set match {
          case Some(descPath) =>
            val resolvedPath = buildDir.resolve(descPath)
            if (!Files.exists(resolvedPath)) {
              Left(s"Proto descriptor set not found: $descPath")
            } else {
              ProtobufParser.parseDescriptorSet(resolvedPath) match {
                case Right(protoFiles) => Right(LoadedSource.Proto(protoFiles.map(ProtobufParser.resolveMapFields)))
                case Left(error)       => Left(s"Failed to parse descriptor set: ${error.message}")
              }
            }
          case None =>
            Left("gRPC source requires 'proto_path', 'proto_paths', or 'descriptor_set' field")
        }
    }
  }

  private def extractEntity(
      loaded: LoadedSource,
      sourceName: String,
      entityPath: String
  ): Either[String, SourceEntity] = {
    loaded match {
      case LoadedSource.Database(metaDb, _) =>
        extractFromDb(metaDb, sourceName, entityPath)
      case LoadedSource.Spec(spec) =>
        extractFromSpec(spec, sourceName, entityPath)
      case LoadedSource.Avro(schemas) =>
        extractFromAvro(schemas, sourceName, entityPath)
      case LoadedSource.Proto(protoFiles) =>
        extractFromProto(protoFiles, sourceName, entityPath)
    }
  }

  private def extractFromDb(
      metaDb: MetaDb,
      sourceName: String,
      entityPath: String
  ): Either[String, SourceEntity] = {
    val (schema, table) = parseEntityPath(entityPath)
    val relName = db.RelationName(schema, table)

    metaDb.relations.get(relName) match {
      case Some(lazyRel) =>
        val relation = lazyRel.forceGet
        val cols: List[db.Col] = relation match {
          case t: db.Table => t.cols.toList
          case v: db.View  => v.cols.toList.map(_._1)
        }
        val pks: Set[db.ColName] = relation match {
          case t: db.Table => t.primaryKey.map(_.colNames.toList.toSet).getOrElse(Set.empty)
          case _: db.View  => Set.empty
        }
        val fields = cols.map { col =>
          SourceField(
            name = col.name.value,
            typeName = col.tpe.toString,
            nullable = col.nullability == Nullability.Nullable,
            isPrimaryKey = pks.contains(col.name),
            comment = col.comment
          )
        }
        val sourceType = relation match {
          case _: db.Table => SourceEntityType.Table
          case _: db.View  => SourceEntityType.View
        }
        Right(SourceEntity(sourceName, entityPath, sourceType, fields))
      case None =>
        Left(s"Entity '$entityPath' not found in source '$sourceName'")
    }
  }

  private def extractFromSpec(
      spec: ParsedSpec,
      sourceName: String,
      entityPath: String
  ): Either[String, SourceEntity] = {
    spec.models.collectFirst {
      case obj: typr.openapi.ModelClass.ObjectType if obj.name == entityPath => obj
    } match {
      case Some(objectType) =>
        val fields = objectType.properties.map { prop =>
          SourceField(
            name = prop.name,
            typeName = formatTypeInfo(prop.typeInfo),
            nullable = prop.nullable || !prop.required,
            isPrimaryKey = false,
            comment = prop.description
          )
        }
        Right(SourceEntity(sourceName, entityPath, SourceEntityType.Schema, fields))
      case None =>
        val modelNames = spec.models.map(_.name).sorted
        Left(s"Schema '$entityPath' not found. Available: ${modelNames.take(5).mkString(", ")}${if (modelNames.length > 5) "..." else ""}")
    }
  }

  private def extractFromAvro(
      schemas: List[AvroSchemaFile],
      sourceName: String,
      entityPath: String
  ): Either[String, SourceEntity] = {
    val allRecords = schemas.flatMap { schemaFile =>
      val primary = schemaFile.primarySchema match {
        case r: AvroRecord => List(r)
        case _             => Nil
      }
      val inlines = schemaFile.inlineSchemas.collect { case r: AvroRecord => r }
      primary ++ inlines
    }

    allRecords.find(_.fullName == entityPath).orElse(allRecords.find(_.name == entityPath)) match {
      case Some(record) =>
        val fields = record.fields.map { field =>
          SourceField(
            name = field.name,
            typeName = ProjectionFieldFormat.formatAvroType(field.fieldType),
            nullable = field.isOptional,
            isPrimaryKey = false,
            comment = field.doc
          )
        }
        Right(SourceEntity(sourceName, entityPath, SourceEntityType.Record, fields))
      case None =>
        val recordNames = allRecords.map(_.fullName).sorted
        Left(s"Record '$entityPath' not found. Available: ${recordNames.take(5).mkString(", ")}${if (recordNames.length > 5) "..." else ""}")
    }
  }

  private def extractFromProto(
      protoFiles: List[ProtoFile],
      sourceName: String,
      entityPath: String
  ): Either[String, SourceEntity] = {
    val allMessages = protoFiles.flatMap(ProjectionFieldFormat.flattenMessages)

    allMessages.find(_.fullName == entityPath).orElse(allMessages.find(_.name == entityPath)) match {
      case Some(message) =>
        val fields = message.fields.map { field =>
          SourceField(
            name = field.name,
            typeName = ProjectionFieldFormat.formatProtoType(field.fieldType, field.label, field.proto3Optional),
            nullable = field.proto3Optional || field.label == typr.grpc.ProtoFieldLabel.Optional,
            isPrimaryKey = false,
            comment = None
          )
        }
        Right(SourceEntity(sourceName, entityPath, SourceEntityType.Message, fields))
      case None =>
        val messageNames = allMessages.map(_.fullName).sorted
        Left(s"Message '$entityPath' not found. Available: ${messageNames.take(5).mkString(", ")}${if (messageNames.length > 5) "..." else ""}")
    }
  }

  private def formatTypeInfo(typeInfo: typr.openapi.TypeInfo): String = typeInfo match {
    case typr.openapi.TypeInfo.Primitive(pt)       => pt.toString.toLowerCase
    case typr.openapi.TypeInfo.Ref(name)           => name
    case typr.openapi.TypeInfo.ListOf(inner)       => s"[${formatTypeInfo(inner)}]"
    case typr.openapi.TypeInfo.MapOf(_, valueType) => s"Map[${formatTypeInfo(valueType)}]"
    case typr.openapi.TypeInfo.Optional(inner)     => s"${formatTypeInfo(inner)}?"
    case typr.openapi.TypeInfo.Any                 => "any"
    case typr.openapi.TypeInfo.InlineEnum(values)  => s"enum(${values.take(3).mkString(",")}${if (values.length > 3) "..." else ""})"
  }

  private def parseEntityPath(entityPath: String): (Option[String], String) = {
    val parts = entityPath.split("\\.", 2)
    if (parts.length == 2) (Some(parts(0)), parts(1))
    else (None, entityPath)
  }

  private def buildDataSource(dbConfig: DatabaseBoundary): Either[String, TypoDataSource] = {
    try {
      val dbType = dbConfig.`type`.getOrElse("unknown")
      val host = dbConfig.host.getOrElse("localhost")
      val database = dbConfig.database.getOrElse("")
      val username = dbConfig.username.getOrElse("")
      val password = dbConfig.password.getOrElse("")

      val ds = dbType match {
        case "postgresql" =>
          TypoDataSource.hikariPostgres(host, dbConfig.port.map(_.toInt).getOrElse(5432), database, username, password)
        case "mariadb" | "mysql" =>
          TypoDataSource.hikariMariaDb(host, dbConfig.port.map(_.toInt).getOrElse(3306), database, username, password)
        case "sqlserver" =>
          TypoDataSource.hikariSqlServer(host, dbConfig.port.map(_.toInt).getOrElse(1433), database, username, password)
        case "oracle" =>
          val serviceName = dbConfig.service.orElse(dbConfig.sid).getOrElse("")
          TypoDataSource.hikariOracle(host, dbConfig.port.map(_.toInt).getOrElse(1521), serviceName, username, password)
        case "db2" =>
          TypoDataSource.hikariDb2(host, dbConfig.port.map(_.toInt).getOrElse(50000), database, username, password)
        case other =>
          throw new Exception(s"Unknown database type: $other")
      }
      Right(ds)
    } catch {
      case e: Exception =>
        Left(Option(e.getMessage).getOrElse(e.getClass.getSimpleName))
    }
  }

  private def closeSource(loaded: LoadedSource): Unit = loaded match {
    case LoadedSource.Database(_, dataSource) => closeDataSource(dataSource)
    case _                                    =>
  }

  private def closeDataSource(ds: TypoDataSource): Unit = {
    try {
      ds.ds match {
        case hikari: com.zaxxer.hikari.HikariDataSource => hikari.close()
        case _                                          =>
      }
    } catch {
      case _: Exception =>
    }
  }
}
