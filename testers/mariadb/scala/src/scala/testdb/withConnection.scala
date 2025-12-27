package testdb

object withConnection {
  def apply[T](f: java.sql.Connection => T): T = {
    val conn = java.sql.DriverManager.getConnection(
      "jdbc:mariadb://localhost:3307/typr?user=typr&password=password"
    )
    conn.setAutoCommit(false)
    try {
      f(conn)
    } finally {
      conn.rollback()
      conn.close()
    }
  }
}
