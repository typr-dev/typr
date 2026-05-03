package adventureworks

import dev.typr.foundationssc.*
import dev.typr.foundationssc.connect.PgConfig

object WithConnection {
  private val config = PgConfig.builder("localhost", 6432, "Adventureworks", "postgres", "password").build()
  private val transactor = Transactor.create(config).rollbackOnly()

  def apply[T](f: Connection ?=> T): T = transactor.transact(f)
}
