package oracledb

import dev.typr.foundations.{SqlFunction, Transactor}
import dev.typr.foundations.connect.oracle.OracleConfig

object withConnection {
  private val config = OracleConfig.builder("localhost", 1521, "FREEPDB1", "typr", "typr_password").serviceName("FREEPDB1").build()
  private val transactor = new Transactor(config, Transactor.testStrategy())

  def apply[T](f: java.sql.Connection => T): T = {
    transactor.execute[T]((conn => f(conn)): SqlFunction[java.sql.Connection, T])
  }
}
