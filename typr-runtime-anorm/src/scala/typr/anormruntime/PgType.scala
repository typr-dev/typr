package typr.anormruntime

import typr.runtime.*

import java.util.Optional
import java.util.function.Function
import scala.jdk.OptionConverters.*

sealed trait PgType[A] {
  val read: PgRead[A]
  val write: PgWrite[A]
  val text: PgText[A]
  val typename: PgTypename[A]

  def opt: PgType[Option[A]] =
    PgType.Option(this).bimap(_.toScala, _.toJava)

  def bimap[B](f: SqlFunction[A, B], g: Function[B, A]): PgType[B] =
    PgType.Mapped(f, g, this)
}

object PgType {
  case class Base[A](read: PgRead[A], write: PgWrite[A], text: PgText[A], typename: PgTypename[A]) extends PgType[A]

  case class Mapped[A, B](f: SqlFunction[A, B], g: Function[B, A], pgType: PgType[A]) extends PgType[B] {
    val read: PgRead[B] = pgType.read.map(f)
    val write: PgWrite[B] = pgType.write.contramap(g)
    val text: PgText[B] = pgType.text.contramap(g)
    val typename: PgTypename[B] = pgType.typename.as()
  }

  case class Option[A](pgType: PgType[A]) extends PgType[Optional[A]] {
    val read: PgRead[Optional[A]] = pgType.read.opt()
    val write: PgWrite[Optional[A]] = pgType.write.opt(pgType.typename)
    val text: PgText[Optional[A]] = pgType.text.opt()
    val typename: PgTypename[Optional[A]] = pgType.typename.opt()
  }
}
