package testdb

import dev.typr.foundations.connect.mariadb.MariaDbConfig
import java.sql.Connection

object WithConnection {
    private val CONFIG: MariaDbConfig =
        MariaDbConfig.builder("localhost", 3307, "typr", "typr", "password").build()

    fun <T> apply(f: (Connection) -> T): T {
        CONFIG.connect().use { conn ->
            conn.autoCommit = false
            try {
                return f(conn)
            } finally {
                conn.rollback()
            }
        }
    }

    fun run(f: (Connection) -> Unit) {
        CONFIG.connect().use { conn ->
            conn.autoCommit = false
            try {
                f(conn)
            } finally {
                conn.rollback()
            }
        }
    }
}
