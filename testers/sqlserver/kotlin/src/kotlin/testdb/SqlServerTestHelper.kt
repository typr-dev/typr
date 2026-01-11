package testdb

import dev.typr.foundations.SqlFunction
import dev.typr.foundations.Transactor
import dev.typr.foundations.connect.ConnectionSettings
import dev.typr.foundations.connect.TransactionIsolation
import dev.typr.foundations.connect.sqlserver.SqlServerConfig
import dev.typr.foundations.connect.sqlserver.SqlServerEncrypt
import java.sql.Connection

object SqlServerTestHelper {
    private val CONFIG: SqlServerConfig =
        SqlServerConfig.builder("localhost", 1433, "typr", "sa", "YourStrong@Passw0rd")
            .encrypt(SqlServerEncrypt.FALSE)
            .build()

    // SQL Server uses pessimistic locking by default, which causes deadlocks when
    // multiple tests run in parallel and access the same tables. READ_UNCOMMITTED
    // prevents lock contention. Since tests rollback anyway, dirty reads are fine.
    private val TRANSACTOR: Transactor = CONFIG.transactor(
        ConnectionSettings.builder().transactionIsolation(TransactionIsolation.READ_UNCOMMITTED).build(),
        Transactor.testStrategy()
    )

    fun <T> apply(f: (Connection) -> T): T {
        return TRANSACTOR.execute(SqlFunction { conn -> f(conn) })
    }

    fun run(f: (Connection) -> Unit) {
        TRANSACTOR.executeVoid { conn -> f(conn) }
    }
}
