package testdb

import dev.typr.foundations.SqlFunction
import dev.typr.foundations.Transactor
import dev.typr.foundations.connect.sqlserver.SqlServerConfig
import dev.typr.foundations.connect.sqlserver.SqlServerEncrypt
import java.sql.Connection

object SqlServerTestHelper {
    private val CONFIG: SqlServerConfig =
        SqlServerConfig.builder("localhost", 1433, "typr", "sa", "YourStrong@Passw0rd")
            .encrypt(SqlServerEncrypt.FALSE)
            .build()

    private val TRANSACTOR: Transactor = Transactor(CONFIG, Transactor.testStrategy())

    fun <T> apply(f: (Connection) -> T): T {
        return TRANSACTOR.execute(SqlFunction { conn -> f(conn) })
    }

    fun run(f: (Connection) -> Unit) {
        TRANSACTOR.executeVoid { conn -> f(conn) }
    }
}
