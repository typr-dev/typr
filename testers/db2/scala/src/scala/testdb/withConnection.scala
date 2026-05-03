package testdb

import dev.typr.foundationssc.*
import dev.typr.foundationssc.connect.Db2Config

object withConnection {
  private val config = Db2Config.builder("localhost", 50000, "typr", "db2inst1", "password").build()
  private val transactor = Transactor.create(config).rollbackOnly()

  def apply[T](f: Connection ?=> T): T = transactor.transact(f)
}
