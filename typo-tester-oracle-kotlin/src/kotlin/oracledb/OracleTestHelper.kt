package oracledb

import java.sql.Connection
import java.sql.DriverManager

object OracleTestHelper {
    private const val JDBC_URL = "jdbc:oracle:thin:@localhost:1521/FREEPDB1"
    private const val USERNAME = "typo"
    private const val PASSWORD = "typo_password"

    fun <T> apply(f: (Connection) -> T): T {
        val conn = DriverManager.getConnection(JDBC_URL, USERNAME, PASSWORD)
        conn.autoCommit = false
        return try {
            f(conn)
        } finally {
            conn.rollback()
            conn.close()
        }
    }

    fun run(f: (Connection) -> Unit) {
        val conn = DriverManager.getConnection(JDBC_URL, USERNAME, PASSWORD)
        conn.autoCommit = false
        try {
            f(conn)
        } finally {
            conn.rollback()
            conn.close()
        }
    }
}
