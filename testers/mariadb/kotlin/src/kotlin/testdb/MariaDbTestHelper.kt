package testdb

import java.sql.Connection
import java.sql.DriverManager

object MariaDbTestHelper {
    private const val JDBC_URL = "jdbc:mariadb://localhost:3307/typr?user=typr&password=password"

    fun <T> apply(f: (Connection) -> T): T {
        val conn = DriverManager.getConnection(JDBC_URL)
        conn.autoCommit = false
        return try {
            f(conn)
        } finally {
            conn.rollback()
            conn.close()
        }
    }

    fun run(f: (Connection) -> Unit) {
        val conn = DriverManager.getConnection(JDBC_URL)
        conn.autoCommit = false
        try {
            f(conn)
        } finally {
            conn.rollback()
            conn.close()
        }
    }
}
