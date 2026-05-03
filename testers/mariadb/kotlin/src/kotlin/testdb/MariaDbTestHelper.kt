package testdb

import dev.typr.foundationskt.Connection
import dev.typr.foundationskt.ConnectionRead
import dev.typr.foundationskt.Transactor
import dev.typr.foundationskt.connect.ConnectionSettings
import dev.typr.foundationskt.connect.MariaConfig
import dev.typr.foundationskt.connect.TransactionIsolation

object MariaDbTestHelper {
    private val CONFIG: MariaConfig =
        MariaConfig.builder("localhost", 3307, "typr", "typr", "password").build()

    private val SETTINGS: ConnectionSettings =
        ConnectionSettings.builder()
            .transactionIsolation(TransactionIsolation.READ_COMMITTED)
            .build()

    private val TRANSACTOR: Transactor = Transactor.create(CONFIG, SETTINGS).rollbackOnly()

    fun <T> apply(f: (Connection) -> T): T = TRANSACTOR.transact(f)

    fun run(f: (Connection) -> Unit) = TRANSACTOR.transact { conn -> f(conn) }

    fun <T> applyRead(f: (ConnectionRead) -> T): T = TRANSACTOR.transactRead(f)
}
