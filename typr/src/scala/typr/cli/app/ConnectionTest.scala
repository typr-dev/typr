package typr.cli.app

import com.zaxxer.hikari.HikariDataSource
import io.circe.Json
import typr.TypoDataSource
import typr.cli.config.{ConfigParser, ParsedSource}
import typr.config.generated.{DatabaseBoundary, DuckdbBoundary, SqliteBoundary}

/** Synchronous "does this source actually connect / parse?" probe. Returns `Left(message)` on failure, `Right(message)` with a short success line on success (e.g. database product / version).
  * Designed to be invoked from a daemon thread; never throws.
  */
object ConnectionTest {

  def run(sourceJson: Json): Either[String, String] =
    ConfigParser.parseSource(sourceJson) match {
      case Right(ParsedSource.Database(db))   => tryDatabase(db)
      case Right(ParsedSource.DuckDb(duck))   => tryDuckDb(duck)
      case Right(ParsedSource.Sqlite(sqlite)) => trySqlite(sqlite)
      case Right(other)                       =>
        Left(s"${other.sourceType} sources don't support connection testing — try the browser screen to validate the spec/schema.")
      case Left(err) => Left(err)
    }

  private def tryDatabase(db: DatabaseBoundary): Either[String, String] = {
    val kind = db.`type`.getOrElse("").toLowerCase
    val host = db.host.getOrElse("localhost")
    val database = db.database.getOrElse("")
    val username = db.username.getOrElse("")
    val password = db.password.getOrElse("")

    val ds: Either[String, TypoDataSource] = kind match {
      case "postgresql" =>
        Right(TypoDataSource.hikariPostgres(host, db.port.map(_.toInt).getOrElse(5432), database, username, password))
      case "mariadb" | "mysql" =>
        Right(TypoDataSource.hikariMariaDb(host, db.port.map(_.toInt).getOrElse(3306), database, username, password))
      case "sqlserver" =>
        Right(TypoDataSource.hikariSqlServer(host, db.port.map(_.toInt).getOrElse(1433), database, username, password))
      case "oracle" =>
        val service = db.service.orElse(db.sid).getOrElse("")
        Right(TypoDataSource.hikariOracle(host, db.port.map(_.toInt).getOrElse(1521), service, username, password))
      case "db2" =>
        Right(TypoDataSource.hikariDb2(host, db.port.map(_.toInt).getOrElse(50000), database, username, password))
      case other =>
        Left(s"unknown database type: $other")
    }

    ds.flatMap { d =>
      try probe(d, "")
      finally close(d)
    }
  }

  private def tryDuckDb(duck: DuckdbBoundary): Either[String, String] = {
    val d = TypoDataSource.hikariDuckDbInMemory(duck.path)
    try probe(d, "duckdb")
    finally close(d)
  }

  private def trySqlite(sqlite: SqliteBoundary): Either[String, String] = {
    val d = TypoDataSource.hikariSqlite(sqlite.path)
    try probe(d, "sqlite")
    finally close(d)
  }

  private def probe(ds: TypoDataSource, fallbackLabel: String): Either[String, String] =
    try {
      val conn = ds.ds.getConnection
      try {
        val meta = conn.getMetaData
        val product = Option(meta.getDatabaseProductName).getOrElse(fallbackLabel)
        val version = Option(meta.getDatabaseProductVersion).getOrElse("")
        Right(if (version.isEmpty) s"connected to $product" else s"connected to $product $version")
      } finally conn.close()
    } catch {
      case t: Throwable =>
        Left(Option(t.getMessage).getOrElse(t.toString))
    }

  private def close(ds: TypoDataSource): Unit =
    try
      ds.ds match {
        case h: HikariDataSource => h.close()
        case _                   => ()
      }
    catch case _: Throwable => ()

  /** Whether the test button should be offered for a given source — only db/duckdb sources today. */
  def isTestable(json: Json): Boolean =
    ConfigParser.parseSource(json).toOption.exists {
      case _: ParsedSource.Database | _: ParsedSource.DuckDb | _: ParsedSource.Sqlite => true
      case _                                                                          => false
    }
}
