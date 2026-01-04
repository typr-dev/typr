package testdb

import dev.typr.foundations.{SqlFunction, Transactor}
import dev.typr.foundations.connect.sqlserver.{SqlServerConfig, SqlServerEncrypt}

object withConnection {
  private val config = SqlServerConfig
    .builder("localhost", 1433, "typr", "sa", "YourStrong@Passw0rd")
    .encrypt(SqlServerEncrypt.FALSE)
    .build()
  private val transactor = new Transactor(config, Transactor.testStrategy())

  def apply[T](f: java.sql.Connection => T): T = {
    transactor.execute[T]((conn => f(conn)): SqlFunction[java.sql.Connection, T])
  }
}
