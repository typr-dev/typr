package adventureworks

import dev.typr.foundations.connect.postgres.PostgresConfig
import java.sql.Connection

object WithConnection {
  private val config = PostgresConfig.builder("localhost", 6432, "Adventureworks", "postgres", "password").build()

  def apply[T](f: Connection ?=> T): T = {
    given conn: Connection = config.connect()
    conn.setAutoCommit(false)
    try {
      f
    } finally {
      conn.rollback()
      conn.close()
    }
  }
}
