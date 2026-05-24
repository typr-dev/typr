package testdb

import dev.typr.foundationssc.*
import dev.typr.foundationssc.connect.{ConnectionSettings, SqliteConfig}

import java.nio.file.{Files, Path}
import java.util.concurrent.atomic.AtomicBoolean

object withConnection {
  private lazy val schemaSQL: String = Files.readString(Path.of("sql-init/sqlite/00-schema.sql"))
  private val config = SqliteConfig.inMemory().foreignKeys(true).build()
  private val base = Transactor.create(config, ConnectionSettings.EMPTY)
  private val transactor = base.rollbackOnly()
  private val initialised = new AtomicBoolean(false)

  /** Split a multi-statement SQL string on `;` outside of `'...'` literals, stripping `-- ...` line comments. */
  private def splitStatements(sql: String): List[String] = {
    val out = List.newBuilder[String]
    val cur = new StringBuilder
    var inString = false
    var i = 0
    while (i < sql.length) {
      val c = sql.charAt(i)
      if (!inString && c == '-' && i + 1 < sql.length && sql.charAt(i + 1) == '-') {
        while (i < sql.length && sql.charAt(i) != '\n') i += 1
      } else if (c == '\'') {
        cur.append(c)
        inString = !inString
        i += 1
      } else if (c == ';' && !inString) {
        out += cur.toString
        cur.clear()
        i += 1
      } else {
        cur.append(c)
        i += 1
      }
    }
    out += cur.toString
    out.result()
  }

  private def ensureInitialised(): Unit =
    if (initialised.compareAndSet(false, true)) {
      base.transact {
        (conn: Connection) ?=>
        val stmt = summon[Connection].unwrap().createStatement()
        try
          splitStatements(schemaSQL).foreach { s =>
            val t = s.trim
            if (t.nonEmpty) stmt.execute(t)
          }
        finally stmt.close()
      }
    }

  def apply[T](f: Connection ?=> T): T = {
    ensureInitialised()
    transactor.transact(f)
  }
}
