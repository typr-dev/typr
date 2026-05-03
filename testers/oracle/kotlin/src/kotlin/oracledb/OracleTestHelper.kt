package oracledb

import dev.typr.foundationskt.Connection
import dev.typr.foundationskt.ConnectionRead
import dev.typr.foundationskt.Transactor
import dev.typr.foundationskt.connect.OracleConfig

object OracleTestHelper {
    private val CONFIG: OracleConfig =
        OracleConfig.builder("localhost", 1521, "FREEPDB1", "typr", "typr_password")
            .serviceName("FREEPDB1")
            .build()

    private val TRANSACTOR: Transactor = Transactor.create(CONFIG).rollbackOnly()

    fun <T> apply(f: (Connection) -> T): T = TRANSACTOR.transact(f)

    fun run(f: (Connection) -> Unit) = TRANSACTOR.transact { conn -> f(conn) }

    fun <T> applyRead(f: (ConnectionRead) -> T): T = TRANSACTOR.transactRead(f)
}
