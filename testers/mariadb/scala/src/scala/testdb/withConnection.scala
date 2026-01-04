package testdb

import dev.typr.foundations.{SqlFunction, Transactor}
import dev.typr.foundations.connect.mariadb.MariaDbConfig

object withConnection {
  private val config = MariaDbConfig.builder("localhost", 3307, "typr", "typr", "password").build()
  private val transactor = new Transactor(config, Transactor.testStrategy())

  def apply[T](f: java.sql.Connection => T): T = {
    transactor.execute[T]((conn => f(conn)): SqlFunction[java.sql.Connection, T])
  }
}
