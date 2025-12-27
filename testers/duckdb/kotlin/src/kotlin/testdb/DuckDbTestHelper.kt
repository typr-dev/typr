package testdb

import java.sql.Connection
import java.sql.DriverManager

object DuckDbTestHelper {
    private const val JDBC_URL = "jdbc:duckdb:/Users/oyvind/pr/typr-2/duckdb-test/duckdb-test.db"

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
