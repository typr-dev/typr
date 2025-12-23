package typr

import com.zaxxer.hikari.{HikariConfig, HikariDataSource}

import java.sql.Connection
import javax.sql.DataSource
import scala.concurrent.{ExecutionContext, Future, blocking}

case class TypoDataSource(ds: DataSource, dbType: DbType) {
  def run[T](f: Connection => T)(implicit ec: ExecutionContext): Future[T] =
    blocking {
      Future {
        val conn = ds.getConnection
        try f(conn)
        finally conn.close()
      }
    }
}

object TypoDataSource {

  /** Create a TypoDataSource for PostgreSQL */
  def hikariPostgres(server: String, port: Int, databaseName: String, username: String, password: String): TypoDataSource = {
    val config = new HikariConfig
    config.setJdbcUrl(s"jdbc:postgresql://$server:$port/$databaseName")
    config.setUsername(username)
    config.setPassword(password)
    val ds = new HikariDataSource(config)
    TypoDataSource(ds, DbType.PostgreSQL)
  }

  /** Create a TypoDataSource for MariaDB */
  def hikariMariaDb(server: String, port: Int, databaseName: String, username: String, password: String): TypoDataSource = {
    val config = new HikariConfig
    config.setJdbcUrl(s"jdbc:mariadb://$server:$port/$databaseName")
    config.setUsername(username)
    config.setPassword(password)
    val ds = new HikariDataSource(config)
    TypoDataSource(ds, DbType.MariaDB)
  }

  /** Create an in-memory DuckDB TypoDataSource */
  def hikariDuckDbInMemory(databaseName: String): TypoDataSource = {
    val config = new HikariConfig
    config.setJdbcUrl(s"jdbc:duckdb:$databaseName")
    val ds = new HikariDataSource(config)
    TypoDataSource(ds, DbType.DuckDB)
  }

  /** Create a TypoDataSource for Oracle */
  def hikariOracle(server: String, port: Int, serviceName: String, username: String, password: String): TypoDataSource = {
    val config = new HikariConfig
    config.setJdbcUrl(s"jdbc:oracle:thin:@//$server:$port/$serviceName")
    config.setUsername(username)
    config.setPassword(password)
    val ds = new HikariDataSource(config)
    TypoDataSource(ds, DbType.Oracle)
  }

  /** Create a TypoDataSource for SQL Server */
  def hikariSqlServer(server: String, port: Int, databaseName: String, username: String, password: String): TypoDataSource = {
    val config = new HikariConfig
    config.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver")
    config.setJdbcUrl(s"jdbc:sqlserver://$server:$port;databaseName=$databaseName;encrypt=false;trustServerCertificate=true")
    config.setUsername(username)
    config.setPassword(password)
    val ds = new HikariDataSource(config)
    TypoDataSource(ds, DbType.SqlServer)
  }

  /** Create a TypoDataSource with auto-detection of database type */
  def hikari(ds: DataSource): TypoDataSource = {
    val conn = ds.getConnection
    try {
      val dbType = DbType.detect(conn)
      TypoDataSource(ds, dbType)
    } finally conn.close()
  }

  @deprecated("Use hikariPostgres instead", "0.x")
  def hikari(server: String, port: Int, databaseName: String, username: String, password: String): TypoDataSource =
    hikariPostgres(server, port, databaseName, username, password)
}
