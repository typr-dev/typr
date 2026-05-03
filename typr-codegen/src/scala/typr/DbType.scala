package typr

import typr.internal.codegen.{Db2Adapter, DbAdapter, DuckDbAdapter, MariaDbAdapter, OracleAdapter, PostgresAdapter, SqlServerAdapter}

import java.sql.Connection

sealed trait DbType {

  /** Get the database adapter for code generation.
    * @param needsTimestampCasts
    *   true for TypeMapperJvmOld (anorm, doobie, zio-jdbc), false for TypeMapperJvmNew (typo-dsl)
    */
  def adapter(needsTimestampCasts: Boolean): DbAdapter
}

object DbType {
  case object PostgreSQL extends DbType {
    def adapter(needsTimestampCasts: Boolean): DbAdapter =
      if (needsTimestampCasts) PostgresAdapter.WithTimestampCasts
      else PostgresAdapter.NoTimestampCasts
  }
  case object MariaDB extends DbType {
    def adapter(needsTimestampCasts: Boolean): DbAdapter = MariaDbAdapter
  }

  case object DuckDB extends DbType {
    def adapter(needsTimestampCasts: Boolean): DbAdapter = DuckDbAdapter
  }
  case object SqlServer extends DbType {
    def adapter(needsTimestampCasts: Boolean): DbAdapter = SqlServerAdapter
  }

  case object Oracle extends DbType {
    def adapter(needsTimestampCasts: Boolean): DbAdapter = OracleAdapter
  }

  case object DB2 extends DbType {
    def adapter(needsTimestampCasts: Boolean): DbAdapter = Db2Adapter
  }

  def detect(connection: Connection): DbType = {
    val metadata = connection.getMetaData
    val productName = metadata.getDatabaseProductName.toLowerCase
    productName match {
      case name if name.contains("postgresql")           => PostgreSQL
      case name if name.contains("mariadb")              => MariaDB
      case name if name.contains("mysql")                => MariaDB
      case name if name.contains("duckdb")               => DuckDB
      case name if name.contains("oracle")               => Oracle
      case name if name.contains("microsoft sql server") => SqlServer
      case name if name.contains("sql server")           => SqlServer
      case name if name.contains("db2")                  => DB2
      case other                                         => sys.error(s"Unsupported database: $other")
    }
  }

  def detectFromDriver(connection: Connection): DbType = {
    val driverName = connection.getMetaData.getDriverName.toLowerCase
    driverName match {
      case name if name.contains("postgresql")            => PostgreSQL
      case name if name.contains("mariadb")               => MariaDB
      case name if name.contains("mysql")                 => MariaDB
      case name if name.contains("duckdb")                => DuckDB
      case name if name.contains("oracle")                => Oracle
      case name if name.contains("microsoft jdbc driver") => SqlServer
      case name if name.contains("sqlserver")             => SqlServer
      case name if name.contains("db2")                   => DB2
      case name if name.contains("ibm data server")       => DB2
      case other                                          => sys.error(s"Unknown database driver: $other")
    }
  }
}
