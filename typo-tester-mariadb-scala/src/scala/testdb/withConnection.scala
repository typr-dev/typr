package testdb

import java.sql.Connection

object withConnection {
  def apply[T](f: Connection ?=> T): T = {
    val conn = java.sql.DriverManager.getConnection("jdbc:mariadb://localhost:3307/typo?user=typo&password=password")
    conn.setAutoCommit(false)
    try {
      given Connection = conn
      f
    } finally {
      conn.rollback()
      conn.close()
    }
  }
}
