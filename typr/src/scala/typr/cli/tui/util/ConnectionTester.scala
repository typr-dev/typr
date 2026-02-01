package typr.cli.tui.util

import io.circe.Json
import typr.TypoDataSource
import typr.cli.config.ConfigParser
import typr.cli.config.ParsedSource
import typr.cli.tui.ConnectionTestResult
import typr.config.generated.DatabaseSource
import typr.config.generated.DuckdbSource
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try

class ConnectionTester {
  private val result = new AtomicReference[ConnectionTestResult](ConnectionTestResult.Pending)

  def getResult: ConnectionTestResult = result.get()

  def testSource(sourceJson: Json): Unit = {
    result.set(ConnectionTestResult.Testing)

    Future {
      ConfigParser.parseSource(sourceJson) match {
        case Right(ParsedSource.Database(dbConfig)) =>
          testDatabase(dbConfig)
        case Right(ParsedSource.DuckDb(duckConfig)) =>
          testDuckDb(duckConfig)
        case Right(_) =>
          throw new Exception("OpenAPI and JSON Schema sources do not support connection testing")
        case Left(error) =>
          throw new Exception(error)
      }
    }.onComplete {
      case Success(msg) => result.set(ConnectionTestResult.Success(msg))
      case Failure(e) =>
        val msg = Option(e.getMessage).getOrElse(e.getClass.getSimpleName)
        result.set(ConnectionTestResult.Failure(msg))
    }
  }

  private def testDatabase(dbConfig: DatabaseSource): String = {
    val dbType = dbConfig.`type`.getOrElse("unknown")
    val host = dbConfig.host.getOrElse("localhost")
    val database = dbConfig.database.getOrElse("")
    val username = dbConfig.username.getOrElse("")
    val password = dbConfig.password.getOrElse("")

    val ds = dbType match {
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

    try {
      val conn = ds.ds.getConnection
      try {
        val meta = conn.getMetaData
        s"Connected to ${meta.getDatabaseProductName} ${meta.getDatabaseProductVersion}"
      } finally {
        conn.close()
      }
    } finally {
      ds.ds.asInstanceOf[com.zaxxer.hikari.HikariDataSource].close()
    }
  }

  private def testDuckDb(duckConfig: DuckdbSource): String = {
    val ds = TypoDataSource.hikariDuckDbInMemory(duckConfig.path)
    try {
      val conn = ds.ds.getConnection
      try {
        "Connected to DuckDB successfully"
      } finally {
        conn.close()
      }
    } finally {
      ds.ds.asInstanceOf[com.zaxxer.hikari.HikariDataSource].close()
    }
  }
}
