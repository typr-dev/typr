package adventureworks

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import doobie.*
import doobie.syntax.all.*

object withConnection {
  val xa = Transactor.fromDriverManager[IO](
    "org.postgresql.Driver",
    "jdbc:postgresql://localhost:6432/Adventureworks?user=postgres&password=password",
    logHandler = None
  )
  val testXa = Transactor.after.set(xa, HC.rollback)

  def apply[T](f: ConnectionIO[T]): T =
    f.transact(testXa).unsafeRunSync()
}
