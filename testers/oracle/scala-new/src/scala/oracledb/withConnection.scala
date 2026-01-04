package oracledb

import dev.typr.foundations.connect.oracle.OracleConfig

object withConnection {
  private val config = OracleConfig.builder("localhost", 1521, "FREEPDB1", "typr", "typr_password").serviceName("FREEPDB1").build()

  def apply[T](f: java.sql.Connection => T): T = {
    val conn = config.connect()
    conn.setAutoCommit(false)
    try {
      f(conn)
    } finally {
      conn.rollback()
      conn.close()
    }
  }
}
