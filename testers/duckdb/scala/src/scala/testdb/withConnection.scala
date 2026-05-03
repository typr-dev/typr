package testdb

import dev.typr.foundationssc.*
import dev.typr.foundationssc.connect.{ConnectionSettings, DuckDbConfig}

import java.nio.file.{Files, Path}

object withConnection {
  private lazy val schemaSQL: String = Files.readString(Path.of("sql-init/duckdb/00-schema.sql"))
  private val config = DuckDbConfig.inMemory().build()
  private val settings = ConnectionSettings.builder().connectionInitSql(schemaSQL).build()
  private val transactor = Transactor.create(config, settings).rollbackOnly()

  def apply[T](f: Connection ?=> T): T = transactor.transact(f)
}
