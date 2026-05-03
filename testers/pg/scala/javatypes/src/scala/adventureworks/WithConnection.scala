package adventureworks

import dev.typr.foundations.{Connection, SqlFunction, Transactor}
import dev.typr.foundations.connect.PgConfig

object WithConnection {
  private val config = PgConfig.builder("localhost", 6432, "Adventureworks", "postgres", "password").build()
  private val transactor = Transactor.create(config).rollbackOnly()

  def apply[T](f: Connection ?=> T): T = {
    val fn: SqlFunction[Connection, T] = c => f(using c)
    transactor.transact(fn)
  }
}
