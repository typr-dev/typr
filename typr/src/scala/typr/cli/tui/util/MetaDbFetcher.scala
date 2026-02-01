package typr.cli.tui.util

import io.circe.Json
import typr.*
import typr.cli.config.ConfigParser
import typr.cli.config.ConfigToOptions
import typr.cli.config.ParsedSource
import typr.cli.tui.MetaDbStatus
import typr.cli.tui.RelationInfo
import typr.cli.tui.RelationType
import typr.config.generated.DatabaseSource
import typr.config.generated.DuckdbSource
import typr.internal.external.ExternalTools
import typr.internal.external.ExternalToolsConfig

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.util.Failure
import scala.util.Success

class MetaDbFetcher {
  private val result = new AtomicReference[MetaDbStatus](MetaDbStatus.Pending)

  def getResult: MetaDbStatus = result.get()

  def fetchSource(sourceName: String, sourceJson: Json): Unit = {
    result.set(MetaDbStatus.Fetching)

    Future {
      ConfigParser.parseSource(sourceJson) match {
        case Right(ParsedSource.Database(dbConfig)) =>
          fetchDatabaseMetaDb(sourceName, dbConfig)
        case Right(ParsedSource.DuckDb(duckConfig)) =>
          fetchDuckDbMetaDb(sourceName, duckConfig)
        case Right(_) =>
          throw new Exception("OpenAPI and JSON Schema sources do not provide metadata")
        case Left(error) =>
          throw new Exception(error)
      }
    }.onComplete {
      case Success(status) => result.set(status)
      case Failure(e) =>
        val msg = Option(e.getMessage).getOrElse(e.getClass.getSimpleName)
        result.set(MetaDbStatus.Failed(msg))
    }
  }

  private def fetchDatabaseMetaDb(sourceName: String, dbConfig: DatabaseSource): MetaDbStatus = {
    val sourceConfig = ConfigToOptions.convertDatabaseSource(sourceName, dbConfig) match {
      case Right(cfg) => cfg
      case Left(err)  => throw new Exception(err)
    }

    val dataSource = buildDataSource(dbConfig)

    try {
      val logger = new TypoLogger {
        override def info(x: String): Unit = ()
        override def warn(x: String): Unit = ()
      }

      val externalTools = ExternalTools.init(logger, ExternalToolsConfig.default)

      val metadb = Await.result(
        MetaDb.fromDb(logger, dataSource, sourceConfig.selector, sourceConfig.schemaMode, externalTools),
        Duration.Inf
      )

      val relations = metadb.relations.toList
        .map { case (relName, lazyRel) =>
          val relType = lazyRel.forceGet match {
            case _: db.Table => RelationType.Table
            case _: db.View  => RelationType.View
          }
          RelationInfo(relName.schema, relName.name, relType)
        }
        .sortBy(r => (r.schema.getOrElse(""), r.name))

      MetaDbStatus.Loaded(relations, metadb.enums.size, metadb.domains.size)
    } finally {
      dataSource.ds.asInstanceOf[com.zaxxer.hikari.HikariDataSource].close()
    }
  }

  private def fetchDuckDbMetaDb(sourceName: String, duckConfig: DuckdbSource): MetaDbStatus = {
    val sourceConfig = ConfigToOptions.convertDuckDbSource(sourceName, duckConfig) match {
      case Right(cfg) => cfg
      case Left(err)  => throw new Exception(err)
    }

    val dataSource = TypoDataSource.hikariDuckDbInMemory(duckConfig.path)

    try {
      val logger = new TypoLogger {
        override def info(x: String): Unit = ()
        override def warn(x: String): Unit = ()
      }

      val externalTools = ExternalTools.init(logger, ExternalToolsConfig.default)

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

      val metadb = Await.result(
        MetaDb.fromDb(logger, dataSource, sourceConfig.selector, sourceConfig.schemaMode, externalTools),
        Duration.Inf
      )

      val relations = metadb.relations.toList
        .map { case (relName, lazyRel) =>
          val relType = lazyRel.forceGet match {
            case _: db.Table => RelationType.Table
            case _: db.View  => RelationType.View
          }
          RelationInfo(relName.schema, relName.name, relType)
        }
        .sortBy(r => (r.schema.getOrElse(""), r.name))

      MetaDbStatus.Loaded(relations, metadb.enums.size, metadb.domains.size)
    } finally {
      dataSource.ds.asInstanceOf[com.zaxxer.hikari.HikariDataSource].close()
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
