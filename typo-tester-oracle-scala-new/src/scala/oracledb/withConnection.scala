package oracledb

object withConnection {
  def apply[T](f: java.sql.Connection => T): T = {
    val conn = java.sql.DriverManager.getConnection(
      "jdbc:oracle:thin:@localhost:1521/FREEPDB1",
      "typo",
      "typo_password"
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
