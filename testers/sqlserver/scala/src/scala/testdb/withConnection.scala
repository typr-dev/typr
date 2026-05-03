package testdb

import dev.typr.foundationssc.*
import dev.typr.foundationssc.connect.{ConnectionSettings, SqlServerConfig, SqlServerEncrypt, TransactionIsolation}

object withConnection {
  private val config = SqlServerConfig
    .builder("localhost", 1433, "typr", "sa", "YourStrong@Passw0rd")
    .encrypt(SqlServerEncrypt.FALSE)
    .build()
  // SQL Server uses pessimistic locking by default, which causes deadlocks when
  // multiple tests run in parallel and access the same tables. READ_UNCOMMITTED
  // prevents lock contention. Since tests rollback anyway, dirty reads are fine.
  private val settings = ConnectionSettings.builder().transactionIsolation(TransactionIsolation.READ_UNCOMMITTED).build()
  private val transactor = Transactor.create(config, settings).rollbackOnly()

  def apply[T](f: Connection ?=> T): T = transactor.transact(f)
}
