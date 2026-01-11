package testdb

import dev.typr.foundations.{SqlFunction, Transactor}
import dev.typr.foundations.connect.{ConnectionSettings, TransactionIsolation}
import dev.typr.foundations.connect.sqlserver.{SqlServerConfig, SqlServerEncrypt}

object withConnection {
  private val config = SqlServerConfig
    .builder("localhost", 1433, "typr", "sa", "YourStrong@Passw0rd")
    .encrypt(SqlServerEncrypt.FALSE)
    .build()
  // SQL Server uses pessimistic locking by default, which causes deadlocks when
  // multiple tests run in parallel and access the same tables. READ_UNCOMMITTED
  // prevents lock contention. Since tests rollback anyway, dirty reads are fine.
  private val transactor = config.transactor(
    ConnectionSettings.builder().transactionIsolation(TransactionIsolation.READ_UNCOMMITTED).build(),
    Transactor.testStrategy()
  )

  def apply[T](f: java.sql.Connection => T): T = {
    transactor.execute[T]((conn => f(conn)): SqlFunction[java.sql.Connection, T])
  }
}
