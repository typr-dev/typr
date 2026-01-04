package adventureworks

import dev.typr.foundations.SqlFunction
import dev.typr.foundations.Transactor
import dev.typr.foundations.connect.postgres.PostgresConfig
import java.sql.Connection

object WithConnection {
    private val CONFIG: PostgresConfig =
        PostgresConfig.builder("localhost", 6432, "Adventureworks", "postgres", "password").build()

    private val TRANSACTOR: Transactor = Transactor(CONFIG, Transactor.testStrategy())

    fun <T> apply(f: (Connection) -> T): T {
        return TRANSACTOR.execute(SqlFunction { conn -> f(conn) })
    }

    fun run(f: (Connection) -> Unit) {
        TRANSACTOR.executeVoid { conn -> f(conn) }
    }
}
