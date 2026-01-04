package oracledb

import dev.typr.foundations.SqlFunction
import dev.typr.foundations.Transactor
import dev.typr.foundations.connect.oracle.OracleConfig
import java.sql.Connection

object OracleTestHelper {
    private val CONFIG: OracleConfig =
        OracleConfig.builder("localhost", 1521, "FREEPDB1", "typr", "typr_password")
            .serviceName("FREEPDB1")
            .build()

    private val TRANSACTOR: Transactor = Transactor(CONFIG, Transactor.testStrategy())

    fun <T> apply(f: (Connection) -> T): T {
        return TRANSACTOR.execute(SqlFunction { conn -> f(conn) })
    }

    fun run(f: (Connection) -> Unit) {
        TRANSACTOR.executeVoid { conn -> f(conn) }
    }
}
