package adventureworks

import dev.typr.foundations.{SqlFunction, Transactor}
import dev.typr.foundations.connect.PgConfig

import java.sql.Connection

object withConnection {
  private val config = PgConfig.builder("localhost", 6432, "Adventureworks", "postgres", "password").build()
  private val transactor = Transactor.create(config).rollbackOnly()

  def apply[T](f: Connection => T): T =
    transactor.transact(new SqlFunction[dev.typr.foundations.Connection, T] {
      def apply(c: dev.typr.foundations.Connection): T = f(c.unwrap())
    })
}
