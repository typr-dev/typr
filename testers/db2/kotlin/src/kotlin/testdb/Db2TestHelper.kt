package testdb

import dev.typr.foundationskt.Connection
import dev.typr.foundationskt.ConnectionRead
import dev.typr.foundationskt.Transactor
import dev.typr.foundationskt.connect.Db2Config

object Db2TestHelper {
    private val CONFIG: Db2Config =
        Db2Config.builder("localhost", 50000, "typr", "db2inst1", "password").build()

    private val TRANSACTOR: Transactor = Transactor.create(CONFIG).rollbackOnly()

    fun <T> apply(f: (Connection) -> T): T = TRANSACTOR.transact(f)

    fun run(f: (Connection) -> Unit) = TRANSACTOR.transact { conn -> f(conn) }

    fun <T> applyRead(f: (ConnectionRead) -> T): T = TRANSACTOR.transactRead(f)
}
