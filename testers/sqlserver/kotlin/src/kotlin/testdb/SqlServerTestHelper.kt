package testdb

import java.sql.Connection
import java.sql.DriverManager

object SqlServerTestHelper {
    private const val JDBC_URL = "jdbc:sqlserver://localhost:1433;databaseName=typr;encrypt=false"
    private const val USERNAME = "sa"
    private const val PASSWORD = "YourStrong@Passw0rd"

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
