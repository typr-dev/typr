package testdb

object withConnection {
  def apply[T](f: java.sql.Connection => T): T = {
    val conn = java.sql.DriverManager.getConnection(
      "jdbc:duckdb:/Users/oyvind/pr/typr-2/duckdb-test/duckdb-test.db"
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
