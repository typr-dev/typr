package testdb

import dev.typr.foundationssc.*
import dev.typr.foundationssc.connect.{ConnectionSettings, MariaConfig, TransactionIsolation}

object withConnection {
  private val config = MariaConfig.builder("localhost", 3307, "typr", "typr", "password").build()
  private val settings = ConnectionSettings.builder().transactionIsolation(TransactionIsolation.READ_COMMITTED).build()
  private val transactor = Transactor.create(config, settings).rollbackOnly()

  def apply[T](f: Connection ?=> T): T = transactor.transact(f)
}
