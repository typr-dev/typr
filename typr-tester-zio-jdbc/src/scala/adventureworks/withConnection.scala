package adventureworks

import zio.jdbc.*
import zio.{Schedule, Unsafe, ZIO, ZLayer, durationInt}

object withConnection {
  lazy val connectionPool: ZLayer[Any, Throwable, ZConnectionPool] = {
    ZLayer.succeed(
      // See https://github.com/zio/zio-jdbc/pull/174/
      ZConnectionPoolConfig.default.copy(retryPolicy = Schedule.recurs(10) && Schedule.exponential(15.millis))
    ) >>>
      ZConnectionPool.postgres(
        "localhost",
        6432,
        "Adventureworks",
        Map(
          "user" -> "postgres",
          "password" -> "password"
        )
      )
  }

  def apply[T](f: ZIO[ZConnection, Throwable, T]): T = {
    Unsafe
      .unsafe { implicit u =>
        zio.Runtime.default.unsafe
          .run {
            ZIO
              .scoped[ZConnectionPool] {
                for {
                  connectionPool <- ZIO.service[ZConnectionPool]
                  c <- connectionPool.transaction.build
                  _ <- c.get.access(_.setAutoCommit(false))
                  r <- f.provideEnvironment(c)
                  _ <- c.get.rollback
                } yield r
              }
              .provideLayer(connectionPool)
          }
          .getOrThrow()
      }

  }
}
