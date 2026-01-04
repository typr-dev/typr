package testdb

import dev.typr.foundations.Transactor
import dev.typr.foundations.connect.duckdb.DuckDbConfig
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection

object DuckDbTestHelper {
    private val CONFIG: DuckDbConfig = DuckDbConfig.inMemory().build()
    private val schemaSQL: String by lazy {
        Files.readString(Path.of("sql-init/duckdb/00-schema.sql"))
    }

    private fun createConnection(): Connection {
        val conn = CONFIG.connect()
        conn.createStatement().execute(schemaSQL)
        return conn
    }

    private val TRANSACTOR: Transactor = Transactor(::createConnection, Transactor.testStrategy())

    fun run(f: (Connection) -> Unit) {
        TRANSACTOR.executeVoid { conn -> f(conn) }
    }
}
