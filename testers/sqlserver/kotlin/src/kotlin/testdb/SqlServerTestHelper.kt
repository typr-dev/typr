package testdb

import dev.typr.foundationskt.Connection
import dev.typr.foundationskt.ConnectionRead
import dev.typr.foundationskt.Transactor
import dev.typr.foundationskt.connect.ConnectionSettings
import dev.typr.foundationskt.connect.SqlServerConfig
import dev.typr.foundationskt.connect.SqlServerEncrypt
import dev.typr.foundationskt.connect.TransactionIsolation

object SqlServerTestHelper {
    private val CONFIG: SqlServerConfig =
        SqlServerConfig.builder("localhost", 1433, "typr", "sa", "YourStrong@Passw0rd")
            .encrypt(SqlServerEncrypt.FALSE)
            .build()

    private val SETTINGS: ConnectionSettings =
        ConnectionSettings.builder()
            .transactionIsolation(TransactionIsolation.READ_UNCOMMITTED)
            .build()

    private val TRANSACTOR: Transactor = Transactor.create(CONFIG, SETTINGS).rollbackOnly()

    fun <T> apply(f: (Connection) -> T): T = TRANSACTOR.transact(f)

    fun run(f: (Connection) -> Unit) = TRANSACTOR.transact { conn -> f(conn) }

    fun <T> applyRead(f: (ConnectionRead) -> T): T = TRANSACTOR.transactRead(f)
}
