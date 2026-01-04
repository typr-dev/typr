package testdb

import dev.typr.foundations.SqlFunction
import dev.typr.foundations.Transactor
import dev.typr.foundations.connect.db2.Db2Config
import java.sql.Connection

object Db2TestHelper {
    private val CONFIG: Db2Config =
        Db2Config.builder("localhost", 50000, "typr", "db2inst1", "password").build()

    private val TRANSACTOR: Transactor = Transactor(CONFIG, Transactor.testStrategy())

    fun <T> apply(f: (Connection) -> T): T {
        return TRANSACTOR.execute(SqlFunction { conn -> f(conn) })
    }

    fun run(f: (Connection) -> Unit) {
        TRANSACTOR.executeVoid { conn -> f(conn) }
    }
}
