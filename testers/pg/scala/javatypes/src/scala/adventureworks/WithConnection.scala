package adventureworks

import dev.typr.foundations.{SqlFunction, Transactor}
import dev.typr.foundations.connect.postgres.PostgresConfig
import java.sql.Connection

object WithConnection {
  private val config = PostgresConfig.builder("localhost", 6432, "Adventureworks", "postgres", "password").build()
  private val transactor = config.transactor(Transactor.testStrategy())

  def apply[T](f: Connection ?=> T): T = {
    val op: SqlFunction[Connection, T] = conn => {
      given Connection = conn
      f
    }
    transactor.execute(op)
  }
}
