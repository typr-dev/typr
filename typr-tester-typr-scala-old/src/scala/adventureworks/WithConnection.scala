package adventureworks

import java.sql.{Connection, DriverManager}

object WithConnection {
  private val JDBC_URL = "jdbc:postgresql://localhost:6432/Adventureworks?user=postgres&password=password"

  def apply[T](f: Connection ?=> T): T = {
    given conn: Connection = DriverManager.getConnection(JDBC_URL)
    conn.setAutoCommit(false)
    try {
      f
    } finally {
      conn.rollback()
      conn.close()
    }
  }
}
