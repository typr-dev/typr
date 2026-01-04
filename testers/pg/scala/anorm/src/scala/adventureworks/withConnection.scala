package adventureworks

import dev.typr.foundations.connect.postgres.PostgresConfig

object withConnection {
  private val config = PostgresConfig.builder("localhost", 6432, "Adventureworks", "postgres", "password").build()

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
