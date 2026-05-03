package oracledb

import dev.typr.foundationssc.*
import dev.typr.foundationssc.connect.OracleConfig

object withConnection {
  private val config = OracleConfig.builder("localhost", 1521, "FREEPDB1", "typr", "typr_password").serviceName("FREEPDB1").build()
  private val transactor = Transactor.create(config).rollbackOnly()

  def apply[T](f: Connection ?=> T): T = transactor.transact(f)
}
