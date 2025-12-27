package testdb

object withConnection {
  def apply[T](f: java.sql.Connection => T): T = {
    val conn = java.sql.DriverManager.getConnection(
      "jdbc:sqlserver://localhost:1433;databaseName=typr;encrypt=false",
      "sa",
      "YourStrong@Passw0rd"
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
