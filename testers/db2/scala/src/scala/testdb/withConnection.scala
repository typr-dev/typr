package testdb

import dev.typr.foundations.{SqlFunction, Transactor}
import dev.typr.foundations.connect.db2.Db2Config

object withConnection {
  private val config = Db2Config.builder("localhost", 50000, "typr", "db2inst1", "password").build()
  private val transactor = new Transactor(config, Transactor.testStrategy())

  def apply[T](f: java.sql.Connection => T): T = {
    transactor.execute[T]((conn => f(conn)): SqlFunction[java.sql.Connection, T])
  }
}
