package testdb

import dev.typr.foundations.SqlFunction
import dev.typr.foundations.Transactor
import dev.typr.foundations.connect.mariadb.MariaDbConfig
import java.sql.Connection

object MariaDbTestHelper {
    private val CONFIG: MariaDbConfig =
        MariaDbConfig.builder("localhost", 3307, "typr", "typr", "password").build()

    private val TRANSACTOR: Transactor = Transactor(CONFIG, Transactor.testStrategy())

    fun <T> apply(f: (Connection) -> T): T {
        return TRANSACTOR.execute(SqlFunction { conn -> f(conn) })
    }

    fun run(f: (Connection) -> Unit) {
        TRANSACTOR.executeVoid { conn -> f(conn) }
    }
}
