package testdb

import dev.typr.foundationskt.Connection
import dev.typr.foundationskt.ConnectionRead
import dev.typr.foundationskt.Transactor
import dev.typr.foundationskt.connect.ConnectionSettings
import dev.typr.foundationskt.connect.DuckDbConfig
import java.nio.file.Files
import java.nio.file.Path

object DuckDbTestHelper {
    private val CONFIG: DuckDbConfig = DuckDbConfig.inMemory().build()
    private val SETTINGS: ConnectionSettings =
        ConnectionSettings.builder()
            .connectionInitSql(Files.readString(Path.of("sql-init/duckdb/00-schema.sql")))
            .build()

    private val TRANSACTOR: Transactor = Transactor.create(CONFIG, SETTINGS).rollbackOnly()

    fun <T> apply(f: (Connection) -> T): T = TRANSACTOR.transact(f)

    fun run(f: (Connection) -> Unit) = TRANSACTOR.transact { conn -> f(conn) }

    fun <T> applyRead(f: (ConnectionRead) -> T): T = TRANSACTOR.transactRead(f)
}
