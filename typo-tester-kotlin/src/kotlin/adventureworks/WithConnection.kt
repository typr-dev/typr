package adventureworks

import java.sql.Connection
import java.sql.DriverManager

object WithConnection {
    private const val JDBC_URL = "jdbc:postgresql://localhost:6432/Adventureworks?user=postgres&password=password"

    fun <T> apply(block: (Connection) -> T): T {
        DriverManager.getConnection(JDBC_URL).use { conn ->
            conn.autoCommit = false
            try {
                return block(conn)
            } finally {
                conn.rollback()
            }
        }
    }

    fun run(block: (Connection) -> Unit) {
        apply(block)
    }
}
