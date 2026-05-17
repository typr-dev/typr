package typr.cli.app

import com.zaxxer.hikari.HikariDataSource
import io.circe.Json
import typr.cli.config.{ConfigParser, ConfigToOptions, ParsedSource}
import typr.config.generated.{DatabaseBoundary, DuckdbBoundary}
import typr.TypoDataSource
import typr.internal.external.{ExternalTools, ExternalToolsConfig}
import typr.{MetaDb, TypoLogger}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}

/** Synchronously fetch a `MetaDb` for one source. Designed to be called from a daemon thread inside [[screens.SchemaBrowser]]; pings logger as it goes. ExternalTools.init may download Python on first
  * run, so the first fetch on a fresh machine is slow — subsequent runs reuse the cached install.
  */
object MetaDbFetch {

  def fetch(name: String, json: Json, logger: TypoLogger)(implicit ec: ExecutionContext): MetaDb =
    ConfigParser
      .parseSource(json)
      .fold(
        err => throw new Exception(s"parse source config: $err"),
        {
          case ParsedSource.Database(db) => fetchDatabase(name, db, logger)
          case ParsedSource.DuckDb(duck) => fetchDuckDb(name, duck, logger)
          case _                         => throw new Exception("not a database / duckdb source")
        }
      )

  private def fetchDatabase(name: String, dbConfig: DatabaseBoundary, logger: TypoLogger)(implicit
      ec: ExecutionContext
  ): MetaDb = {
    val sourceConfig = ConfigToOptions
      .convertDatabaseBoundary(name, dbConfig)
      .fold(
        err => throw new Exception(s"convert source config: $err"),
        identity
      )
    val ds = buildDataSource(dbConfig)
    try {
      val externalTools = ExternalTools.init(logger, ExternalToolsConfig.default)
      Await.result(
        MetaDb.fromDb(logger, ds, sourceConfig.selector, sourceConfig.schemaMode, externalTools),
        Duration.Inf
      )
    } finally close(ds)
  }

  private def fetchDuckDb(name: String, duckConfig: DuckdbBoundary, logger: TypoLogger)(implicit
      ec: ExecutionContext
  ): MetaDb = {
    val sourceConfig = ConfigToOptions
      .convertDuckDbSource(name, duckConfig)
      .fold(
        err => throw new Exception(s"convert source config: $err"),
        identity
      )
    val ds = TypoDataSource.hikariDuckDbInMemory(duckConfig.path)
    try {
      // Apply schema.sql if present so the resulting in-memory DB actually has tables.
      duckConfig.schema_sql.foreach { sqlPath =>
        val conn = ds.ds.getConnection
        try {
          val stmt = conn.createStatement()
          stmt.execute(java.nio.file.Files.readString(java.nio.file.Paths.get(sqlPath)))
          stmt.close()
        } finally conn.close()
      }
      val externalTools = ExternalTools.init(logger, ExternalToolsConfig.default)
      Await.result(
        MetaDb.fromDb(logger, ds, sourceConfig.selector, sourceConfig.schemaMode, externalTools),
        Duration.Inf
      )
    } finally close(ds)
  }

  private def buildDataSource(dbConfig: DatabaseBoundary): TypoDataSource = {
    val dbType = dbConfig.`type`.getOrElse("").toLowerCase
    val host = dbConfig.host.getOrElse("")
    val database = dbConfig.database.getOrElse("")
    val username = dbConfig.username.getOrElse("")
    val password = dbConfig.password.getOrElse("")
    dbType match {
      case "postgresql" =>
        TypoDataSource.hikariPostgres(host, dbConfig.port.map(_.toInt).getOrElse(5432), database, username, password)
      case "mariadb" | "mysql" =>
        TypoDataSource.hikariMariaDb(host, dbConfig.port.map(_.toInt).getOrElse(3306), database, username, password)
      case "sqlserver" =>
        TypoDataSource.hikariSqlServer(host, dbConfig.port.map(_.toInt).getOrElse(1433), database, username, password)
      case "oracle" =>
        val service = dbConfig.service.orElse(dbConfig.sid).getOrElse("")
        TypoDataSource.hikariOracle(host, dbConfig.port.map(_.toInt).getOrElse(1521), service, username, password)
      case "db2" =>
        TypoDataSource.hikariDb2(host, dbConfig.port.map(_.toInt).getOrElse(50000), database, username, password)
      case other =>
        throw new Exception(s"unknown database type: $other")
    }
  }

  private def close(ds: TypoDataSource): Unit =
    try
      ds.ds match {
        case h: HikariDataSource => h.close()
        case _                   => ()
      }
    catch case _: Throwable => ()

}
