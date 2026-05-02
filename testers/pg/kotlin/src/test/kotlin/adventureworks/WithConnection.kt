package adventureworks

import dev.typr.foundationskt.Connection
import dev.typr.foundationskt.ConnectionRead
import dev.typr.foundationskt.Transactor
import dev.typr.foundationskt.connect.PgConfig

object WithConnection {
    private val CONFIG: PgConfig =
        PgConfig.builder("localhost", 6432, "Adventureworks", "postgres", "password").build()

    private val TRANSACTOR: Transactor = Transactor.create(CONFIG).rollbackOnly()

    fun <T> apply(f: (Connection) -> T): T = TRANSACTOR.transact(f)

    fun run(f: (Connection) -> Unit) = TRANSACTOR.transact { conn -> f(conn) }

    fun <T> applyRead(f: (ConnectionRead) -> T): T = TRANSACTOR.transactRead(f)
}
