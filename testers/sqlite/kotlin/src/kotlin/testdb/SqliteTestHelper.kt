package testdb

import dev.typr.foundationskt.Connection
import dev.typr.foundationskt.ConnectionRead
import dev.typr.foundationskt.Transactor
import dev.typr.foundationskt.connect.ConnectionSettings
import dev.typr.foundationskt.connect.SqliteConfig
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

/**
 * SQLite in-memory test helper. xerial driver doesn't support multi-statement execute(), so we
 * split the schema and run statements one at a time during a one-time init; per-test transactions
 * are then rollback-only on the same connection.
 */
object SqliteTestHelper {
    private val CONFIG: SqliteConfig = SqliteConfig.inMemory().foreignKeys(true).build()
    private val BASE: Transactor = Transactor.create(CONFIG, ConnectionSettings.EMPTY)
    private val TRANSACTOR: Transactor = BASE.rollbackOnly()
    private val INITIALISED = AtomicBoolean(false)

    private fun splitStatements(sql: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var inString = false
        var i = 0
        while (i < sql.length) {
            val c = sql[i]
            when {
                !inString && c == '-' && i + 1 < sql.length && sql[i + 1] == '-' -> {
                    while (i < sql.length && sql[i] != '\n') i++
                }
                c == '\'' -> {
                    cur.append(c); inString = !inString; i++
                }
                c == ';' && !inString -> {
                    out.add(cur.toString()); cur.clear(); i++
                }
                else -> {
                    cur.append(c); i++
                }
            }
        }
        out.add(cur.toString())
        return out
    }

    private fun ensureInitialised() {
        if (INITIALISED.compareAndSet(false, true)) {
            val schema = Files.readString(Path.of("sql-init/sqlite/00-schema.sql"))
            BASE.transact { conn ->
                conn.unwrap().createStatement().use { stmt ->
                    for (chunk in splitStatements(schema)) {
                        val s = chunk.trim()
                        if (s.isNotEmpty()) stmt.execute(s)
                    }
                }
            }
        }
    }

    fun <T> apply(f: (Connection) -> T): T {
        ensureInitialised()
        return TRANSACTOR.transact(f)
    }

    fun run(f: (Connection) -> Unit) {
        ensureInitialised()
        TRANSACTOR.transact { conn -> f(conn) }
    }

    fun <T> applyRead(f: (ConnectionRead) -> T): T {
        ensureInitialised()
        return TRANSACTOR.transactRead(f)
    }
}
