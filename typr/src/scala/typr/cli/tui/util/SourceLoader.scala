package typr.cli.tui.util

import io.circe.Json
import typr.*
import typr.cli.config.ConfigParser
import typr.cli.config.ConfigToOptions
import typr.cli.config.ParsedSource
import typr.cli.config.TyprConfig
import typr.cli.tui.LoadedSqlScript
import typr.cli.tui.SourceError
import typr.cli.tui.SourceName
import typr.cli.tui.SourceStatus
import typr.config.generated.DatabaseSource
import typr.config.generated.DuckdbSource
import typr.config.generated.JsonschemaSource
import typr.config.generated.OpenapiSource
import typr.internal.external.ExternalTools
import typr.internal.external.ExternalToolsConfig
import typr.openapi.parser.OpenApiParser
import typr.jsonschema.JsonSchemaParser
import typr.cli.tui.SpecSourceType

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.jdk.CollectionConverters.*
import scala.util.Failure
import scala.util.Success

/** Async loader for source MetaDb and SQL scripts at boot time */
class SourceLoader {
  private val statuses = new ConcurrentHashMap[SourceName, SourceStatus]()
  private val loadedConfigs = new ConcurrentHashMap[SourceName, Json]()

  def getStatuses: Map[SourceName, SourceStatus] =
    statuses.asScala.toMap

  def getLoadedConfig(sourceName: SourceName): Option[Json] =
    Option(loadedConfigs.get(sourceName))

  def startLoading(config: TyprConfig, buildDir: Path): Unit = {
    val sources = config.sources.getOrElse(Map.empty)

    sources.foreach { case (name, json) =>
      val sourceName = SourceName(name)
      statuses.put(sourceName, SourceStatus.Pending)
      loadedConfigs.put(sourceName, json)

      Future {
        loadSource(sourceName, json, buildDir)
      }
    }
  }

  def retry(sourceName: SourceName, config: TyprConfig, buildDir: Path): Unit = {
    statuses.get(sourceName) match {
      case failed: SourceStatus.Failed =>
        statuses.put(sourceName, SourceStatus.Pending)
        config.sources.flatMap(_.get(sourceName.value)).foreach { json =>
          Future {
            loadSource(sourceName, json, buildDir)
          }
        }
      case _ =>
    }
  }

  /** Load a single new source that was just added */
  def loadNewSource(sourceName: SourceName, sourceJson: Json, buildDir: Path): Unit = {
    statuses.put(sourceName, SourceStatus.Pending)
    loadedConfigs.put(sourceName, sourceJson)
    Future {
      loadSource(sourceName, sourceJson, buildDir)
    }
  }

  /** Remove a source that was deleted from config */
  def removeSource(sourceName: SourceName): Unit = {
    statuses.get(sourceName) match {
      case s: SourceStatus.RunningSqlGlot =>
        closeDataSource(s.dataSource)
      case s: SourceStatus.Ready =>
        closeDataSource(s.dataSource)
      case _ =>
    }
    statuses.remove(sourceName)
    loadedConfigs.remove(sourceName)
  }

  /** Reload a source that was updated in config */
  def reloadSource(sourceName: SourceName, sourceJson: Json, buildDir: Path): Unit = {
    statuses.get(sourceName) match {
      case s: SourceStatus.RunningSqlGlot =>
        closeDataSource(s.dataSource)
      case s: SourceStatus.Ready =>
        closeDataSource(s.dataSource)
      case _ =>
    }
    statuses.put(sourceName, SourceStatus.Pending)
    loadedConfigs.put(sourceName, sourceJson)
    Future {
      loadSource(sourceName, sourceJson, buildDir)
    }
  }

  def shutdown(): Unit = {
    statuses.asScala.foreach {
      case (_, s: SourceStatus.RunningSqlGlot) =>
        closeDataSource(s.dataSource)
      case (_, s: SourceStatus.Ready) =>
        closeDataSource(s.dataSource)
      case _ =>
    }
    statuses.clear()
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

  private def loadSource(
      sourceName: SourceName,
      sourceJson: Json,
      buildDir: Path
  ): Unit = {
    val now = System.currentTimeMillis()
    statuses.put(sourceName, SourceStatus.LoadingMetaDb(startedAt = now))

    try {
      val parsed = ConfigParser.parseSource(sourceJson) match {
        case Right(p) => p
        case Left(err) =>
          statuses.put(
            sourceName,
            SourceStatus.Failed(
              SourceError.ConfigParseFailed(err),
              failedAt = System.currentTimeMillis(),
              retryCount = currentRetryCount(sourceName)
            )
          )
          return
      }

      parsed match {
        case ParsedSource.Database(dbConfig) =>
          loadDatabaseSource(sourceName, dbConfig, buildDir)
        case ParsedSource.DuckDb(duckConfig) =>
          loadDuckDbSource(sourceName, duckConfig, buildDir)
        case ParsedSource.OpenApi(openapiConfig) =>
          loadOpenApiSource(sourceName, openapiConfig, buildDir)
        case ParsedSource.JsonSchema(jsonSchemaConfig) =>
          loadJsonSchemaSource(sourceName, jsonSchemaConfig, buildDir)
      }
    } catch {
      case e: Exception =>
        val msg = Option(e.getMessage).getOrElse(e.getClass.getSimpleName)
        statuses.put(
          sourceName,
          SourceStatus.Failed(
            SourceError.MetaDbFetchFailed(msg),
            failedAt = System.currentTimeMillis(),
            retryCount = currentRetryCount(sourceName)
          )
        )
    }
  }

  private def currentRetryCount(sourceName: SourceName): Int = {
    statuses.get(sourceName) match {
      case f: SourceStatus.Failed => f.retryCount + 1
      case _                      => 0
    }
  }

  private def loadDatabaseSource(
      sourceName: SourceName,
      dbConfig: DatabaseSource,
      buildDir: Path
  ): Unit = {
    val sourceConfig = ConfigToOptions.convertDatabaseSource(sourceName.value, dbConfig) match {
      case Right(cfg) => cfg
      case Left(err) =>
        statuses.put(
          sourceName,
          SourceStatus.Failed(
            SourceError.ConfigParseFailed(err),
            failedAt = System.currentTimeMillis(),
            retryCount = currentRetryCount(sourceName)
          )
        )
        return
    }

    val dataSource = buildDataSource(dbConfig)
    val metaDbLoadedAt = loadMetaDb(sourceName, sourceConfig, dataSource)

    if (metaDbLoadedAt.isEmpty) return

    runSqlGlotPhase(sourceName, sourceConfig, dataSource, metaDbLoadedAt.get, buildDir)
  }

  private def loadDuckDbSource(
      sourceName: SourceName,
      duckConfig: DuckdbSource,
      buildDir: Path
  ): Unit = {
    val sourceConfig = ConfigToOptions.convertDuckDbSource(sourceName.value, duckConfig) match {
      case Right(cfg) => cfg
      case Left(err) =>
        statuses.put(
          sourceName,
          SourceStatus.Failed(
            SourceError.ConfigParseFailed(err),
            failedAt = System.currentTimeMillis(),
            retryCount = currentRetryCount(sourceName)
          )
        )
        return
    }

    val dataSource = TypoDataSource.hikariDuckDbInMemory(duckConfig.path)

    duckConfig.schema_sql.foreach { schemaPath =>
      val conn = dataSource.ds.getConnection
      try {
        val stmt = conn.createStatement()
        val sql = java.nio.file.Files.readString(java.nio.file.Paths.get(schemaPath))
        stmt.execute(sql)
        stmt.close()
      } finally {
        conn.close()
      }
    }

    val metaDbLoadedAt = loadMetaDb(sourceName, sourceConfig, dataSource)

    if (metaDbLoadedAt.isEmpty) return

    runSqlGlotPhase(sourceName, sourceConfig, dataSource, metaDbLoadedAt.get, buildDir)
  }

  private def loadOpenApiSource(
      sourceName: SourceName,
      openapiConfig: OpenapiSource,
      buildDir: Path
  ): Unit = {
    val now = System.currentTimeMillis()
    statuses.put(sourceName, SourceStatus.LoadingSpec(startedAt = now))

    val specPath = openapiConfig.spec.orElse(openapiConfig.specs.flatMap(_.headOption)).getOrElse {
      statuses.put(
        sourceName,
        SourceStatus.Failed(
          SourceError.ConfigParseFailed("OpenAPI source requires 'spec' or 'specs' field"),
          failedAt = System.currentTimeMillis(),
          retryCount = currentRetryCount(sourceName)
        )
      )
      return
    }

    val resolvedPath = buildDir.resolve(specPath)
    if (!Files.exists(resolvedPath)) {
      statuses.put(
        sourceName,
        SourceStatus.Failed(
          SourceError.SpecNotFound(specPath),
          failedAt = System.currentTimeMillis(),
          retryCount = currentRetryCount(sourceName)
        )
      )
      return
    }

    OpenApiParser.parseFile(resolvedPath) match {
      case Right(parsedSpec) =>
        statuses.put(
          sourceName,
          SourceStatus.ReadySpec(
            spec = parsedSpec,
            specPath = specPath,
            sourceType = SpecSourceType.OpenApi,
            loadedAt = System.currentTimeMillis()
          )
        )
      case Left(errors) =>
        statuses.put(
          sourceName,
          SourceStatus.Failed(
            SourceError.SpecParseFailed(specPath, errors),
            failedAt = System.currentTimeMillis(),
            retryCount = currentRetryCount(sourceName)
          )
        )
    }
  }

  private def loadJsonSchemaSource(
      sourceName: SourceName,
      jsonSchemaConfig: JsonschemaSource,
      buildDir: Path
  ): Unit = {
    val now = System.currentTimeMillis()
    statuses.put(sourceName, SourceStatus.LoadingSpec(startedAt = now))

    val specPath = jsonSchemaConfig.spec.orElse(jsonSchemaConfig.specs.flatMap(_.headOption)).getOrElse {
      statuses.put(
        sourceName,
        SourceStatus.Failed(
          SourceError.ConfigParseFailed("JSON Schema source requires 'spec' or 'specs' field"),
          failedAt = System.currentTimeMillis(),
          retryCount = currentRetryCount(sourceName)
        )
      )
      return
    }

    val resolvedPath = buildDir.resolve(specPath)
    if (!Files.exists(resolvedPath)) {
      statuses.put(
        sourceName,
        SourceStatus.Failed(
          SourceError.SpecNotFound(specPath),
          failedAt = System.currentTimeMillis(),
          retryCount = currentRetryCount(sourceName)
        )
      )
      return
    }

    JsonSchemaParser.parseFile(resolvedPath) match {
      case Right(parsedSchema) =>
        // Convert JsonSchema result to ParsedSpec for unified handling
        val parsedSpec = typr.openapi.ParsedSpec(
          info = typr.openapi.SpecInfo(
            title = specPath,
            version = "1.0.0",
            description = None
          ),
          models = parsedSchema.models,
          sumTypes = parsedSchema.sumTypes,
          apis = Nil,
          webhooks = Nil,
          securitySchemes = Map.empty,
          warnings = Nil
        )
        statuses.put(
          sourceName,
          SourceStatus.ReadySpec(
            spec = parsedSpec,
            specPath = specPath,
            sourceType = SpecSourceType.JsonSchema,
            loadedAt = System.currentTimeMillis()
          )
        )
      case Left(errors) =>
        statuses.put(
          sourceName,
          SourceStatus.Failed(
            SourceError.SpecParseFailed(specPath, errors),
            failedAt = System.currentTimeMillis(),
            retryCount = currentRetryCount(sourceName)
          )
        )
    }
  }

  private def loadMetaDb(
      sourceName: SourceName,
      sourceConfig: ConfigToOptions.SourceConfig,
      dataSource: TypoDataSource
  ): Option[Long] = {
    try {
      val logger = TypoLogger.Noop
      val externalTools = ExternalTools.init(logger, ExternalToolsConfig.default)

      val metaDb = Await.result(
        MetaDb.fromDb(logger, dataSource, sourceConfig.selector, sourceConfig.schemaMode, externalTools),
        Duration.Inf
      )

      val metaDbLoadedAt = System.currentTimeMillis()

      statuses.put(
        sourceName,
        SourceStatus.RunningSqlGlot(
          metaDb = metaDb,
          dataSource = dataSource,
          sourceConfig = sourceConfig,
          metaDbLoadedAt = metaDbLoadedAt,
          sqlGlotStartedAt = System.currentTimeMillis()
        )
      )

      Some(metaDbLoadedAt)
    } catch {
      case e: Exception =>
        closeDataSource(dataSource)
        val msg = Option(e.getMessage).getOrElse(e.getClass.getSimpleName)
        statuses.put(
          sourceName,
          SourceStatus.Failed(
            SourceError.MetaDbFetchFailed(msg),
            failedAt = System.currentTimeMillis(),
            retryCount = currentRetryCount(sourceName)
          )
        )
        None
    }
  }

  private def runSqlGlotPhase(
      sourceName: SourceName,
      sourceConfig: ConfigToOptions.SourceConfig,
      dataSource: TypoDataSource,
      metaDbLoadedAt: Long,
      buildDir: Path
  ): Unit = {
    val currentStatus = statuses.get(sourceName).asInstanceOf[SourceStatus.RunningSqlGlot]
    val metaDb = currentStatus.metaDb

    try {
      val sqlScripts = sourceConfig.sqlScriptsPath match {
        case Some(scriptsPath) =>
          val path = java.nio.file.Paths.get(scriptsPath)
          if (java.nio.file.Files.isDirectory(path)) {
            import scala.jdk.CollectionConverters.*
            java.nio.file.Files
              .list(path)
              .iterator
              .asScala
              .filter(_.toString.endsWith(".sql"))
              .flatMap { sqlPath =>
                try {
                  val content = java.nio.file.Files.readString(sqlPath)
                  Some(LoadedSqlScript(sqlPath, content))
                } catch {
                  case _: Exception => None
                }
              }
              .toList
          } else if (java.nio.file.Files.exists(path)) {
            try {
              val content = java.nio.file.Files.readString(path)
              List(LoadedSqlScript(path, content))
            } catch {
              case _: Exception => Nil
            }
          } else {
            Nil
          }
        case None => Nil
      }

      val sqlGlotFinishedAt = System.currentTimeMillis()

      statuses.put(
        sourceName,
        SourceStatus.Ready(
          metaDb = metaDb,
          dataSource = dataSource,
          sourceConfig = sourceConfig,
          sqlScripts = sqlScripts,
          metaDbLoadedAt = metaDbLoadedAt,
          sqlGlotFinishedAt = sqlGlotFinishedAt
        )
      )
    } catch {
      case e: Exception =>
        val msg = Option(e.getMessage).getOrElse(e.getClass.getSimpleName)
        statuses.put(
          sourceName,
          SourceStatus.Failed(
            SourceError.SqlGlotFailed("unknown", msg),
            failedAt = System.currentTimeMillis(),
            retryCount = currentRetryCount(sourceName)
          )
        )
    }
  }

  private def buildDataSource(dbConfig: DatabaseSource): TypoDataSource = {
    val dbType = dbConfig.`type`.getOrElse("unknown")
    val host = dbConfig.host.getOrElse("localhost")
    val database = dbConfig.database.getOrElse("")
    val username = dbConfig.username.getOrElse("")
    val password = dbConfig.password.getOrElse("")

    dbType match {
      case "postgresql" =>
        val port = dbConfig.port.map(_.toInt).getOrElse(5432)
        TypoDataSource.hikariPostgres(host, port, database, username, password)

      case "mariadb" | "mysql" =>
        val port = dbConfig.port.map(_.toInt).getOrElse(3306)
        TypoDataSource.hikariMariaDb(host, port, database, username, password)

      case "sqlserver" =>
        val port = dbConfig.port.map(_.toInt).getOrElse(1433)
        TypoDataSource.hikariSqlServer(host, port, database, username, password)

      case "oracle" =>
        val port = dbConfig.port.map(_.toInt).getOrElse(1521)
        val serviceName = dbConfig.service.orElse(dbConfig.sid).getOrElse("")
        TypoDataSource.hikariOracle(host, port, serviceName, username, password)

      case "db2" =>
        val port = dbConfig.port.map(_.toInt).getOrElse(50000)
        TypoDataSource.hikariDb2(host, port, database, username, password)

      case other =>
        throw new Exception(s"Unknown database type: $other")
    }
  }
}
