package adventureworks

import java.sql.Connection
import java.sql.DriverManager

object WithConnection {
    private const val JDBC_URL = "jdbc:postgresql://localhost:6432/Adventureworks?user=postgres&password=password"

    fun <T> apply(f: (Connection) -> T): T {
        DriverManager.getConnection(JDBC_URL).use { conn ->
            conn.autoCommit = false
            try {
                return f(conn)
            } finally {
                conn.rollback()
            }
        }
    }

    fun run(f: (Connection) -> Unit) {
        DriverManager.getConnection(JDBC_URL).use { conn ->
            conn.autoCommit = false
            try {
                f(conn)
            } finally {
                conn.rollback()
            }
        }
    }
}
