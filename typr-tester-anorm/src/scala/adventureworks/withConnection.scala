package adventureworks

object withConnection {
  def apply[T](f: java.sql.Connection => T): T = {
    val conn = java.sql.DriverManager.getConnection("jdbc:postgresql://localhost:6432/Adventureworks?user=postgres&password=password")
    conn.setAutoCommit(false)
    try {
      f(conn)
    } finally {
      conn.rollback()
      conn.close()
    }
  }
}
