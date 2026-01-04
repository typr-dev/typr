package typr

import dev.typr.foundations.connect.DatabaseKind
import dev.typr.foundations.connect.db2.Db2Config
import dev.typr.foundations.connect.duckdb.DuckDbConfig
import dev.typr.foundations.connect.mariadb.MariaDbConfig
import dev.typr.foundations.connect.oracle.OracleConfig
import dev.typr.foundations.connect.postgres.PostgresConfig
import dev.typr.foundations.connect.sqlserver.{SqlServerConfig, SqlServerEncrypt}
import dev.typr.foundations.hikari.HikariDataSourceFactory

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
    val config = PostgresConfig.builder(server, port, databaseName, username, password).build()
    val pooled = HikariDataSourceFactory.create(config)
    TypoDataSource(pooled.unwrap(), DbType.PostgreSQL)
  }

  /** Create a TypoDataSource for MariaDB */
  def hikariMariaDb(server: String, port: Int, databaseName: String, username: String, password: String): TypoDataSource = {
    val config = MariaDbConfig.builder(server, port, databaseName, username, password).build()
    val pooled = HikariDataSourceFactory.create(config)
    TypoDataSource(pooled.unwrap(), DbType.MariaDB)
  }

  /** Create an in-memory DuckDB TypoDataSource */
  def hikariDuckDbInMemory(databaseName: String): TypoDataSource = {
    val config = DuckDbConfig.builder(databaseName).build()
    val pooled = HikariDataSourceFactory.create(config)
    TypoDataSource(pooled.unwrap(), DbType.DuckDB)
  }

  /** Create a TypoDataSource for Oracle */
  def hikariOracle(server: String, port: Int, serviceName: String, username: String, password: String): TypoDataSource = {
    val config = OracleConfig.builder(server, port, serviceName, username, password).build()
    val pooled = HikariDataSourceFactory.create(config)
    TypoDataSource(pooled.unwrap(), DbType.Oracle)
  }

  /** Create a TypoDataSource for SQL Server */
  def hikariSqlServer(server: String, port: Int, databaseName: String, username: String, password: String): TypoDataSource = {
    val config = SqlServerConfig
      .builder(server, port, databaseName, username, password)
      .encrypt(SqlServerEncrypt.FALSE)
      .trustServerCertificate(true)
      .build()
    val pooled = HikariDataSourceFactory.create(config)
    TypoDataSource(pooled.unwrap(), DbType.SqlServer)
  }

  /** Create a TypoDataSource for IBM DB2 */
  def hikariDb2(server: String, port: Int, databaseName: String, username: String, password: String): TypoDataSource = {
    val config = Db2Config.builder(server, port, databaseName, username, password).build()
    val pooled = HikariDataSourceFactory.create(config)
    TypoDataSource(pooled.unwrap(), DbType.DB2)
  }

  /** Create a TypoDataSource with auto-detection of database type */
  def hikari(ds: DataSource): TypoDataSource = {
    val conn = ds.getConnection
    try {
      val kind = DatabaseKind.detect(conn)
      val dbType = kind match {
        case DatabaseKind.POSTGRESQL => DbType.PostgreSQL
        case DatabaseKind.MARIADB    => DbType.MariaDB
        case DatabaseKind.DUCKDB     => DbType.DuckDB
        case DatabaseKind.ORACLE     => DbType.Oracle
        case DatabaseKind.SQLSERVER  => DbType.SqlServer
        case DatabaseKind.DB2        => DbType.DB2
      }
      TypoDataSource(ds, dbType)
    } finally conn.close()
  }

  @deprecated("Use hikariPostgres instead", "0.x")
  def hikari(server: String, port: Int, databaseName: String, username: String, password: String): TypoDataSource =
    hikariPostgres(server, port, databaseName, username, password)
}
