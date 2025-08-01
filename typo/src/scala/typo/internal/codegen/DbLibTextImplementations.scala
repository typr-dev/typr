package typo
package internal
package codegen

import typo.ImplicitOrUsing.{Implicit, Using}
import typo.sc.Code

/** Put implementations of `Text` and `streamingInsert` for anorm and zio-jdbc here instead of upstreaming them for now
  */
object DbLibTextImplementations {
  def streamingInsertAnorm(Text: sc.Type.Qualified, implicitOrUsing: ImplicitOrUsing) =
    code"""|import org.postgresql.PGConnection
           |import org.postgresql.util.PSQLException
           |
           |import java.sql.Connection
           |import scala.util.control.NonFatal
           |
           |object streamingInsert {
           |  def apply[T](copyCommand: String, batchSize: Int, rows: Iterator[T])($implicitOrUsing text: $Text[T], c: Connection): Long = {
           |    val copyManager = c.unwrap(classOf[PGConnection]).getCopyAPI
           |
           |    val in = copyManager.copyIn(copyCommand)
           |
           |    try {
           |      rows.grouped(batchSize).foreach { group =>
           |        val sb = new StringBuilder
           |        group.foreach { t =>
           |          $Text[T].unsafeEncode(t, sb)
           |          sb ++= "\\n"
           |        }
           |        val bytes = sb.result().getBytes("UTF-8")
           |        in.writeToCopy(bytes, 0, bytes.length)
           |      }
           |      in.endCopy()
           |    } catch {
           |      case NonFatal(th) =>
           |        try in.cancelCopy()
           |        catch {
           |          case x: PSQLException if x.getMessage == "Tried to cancel an inactive copy operation" => // ignore
           |        }
           |        throw th
           |    }
           |  }
           |}
           |""".stripMargin

  def streamingInsertZio(Text: sc.Type.Qualified, implicitOrUsing: ImplicitOrUsing) =
    code"""|import org.postgresql.PGConnection
           |import org.postgresql.copy.CopyIn
           |import org.postgresql.util.PSQLException
           |import zio.*
           |import zio.jdbc.ZConnection
           |import zio.stream.{ZSink, ZStream}
           |
           |object streamingInsert {
           |  def apply[T](copyCommand: String, batchSize: Int, rows: ZStream[ZConnection, Throwable, T])($implicitOrUsing text: $Text[T]): ZIO[ZConnection, Throwable, Long] = ZIO.scoped {
           |    def startCopy(c: ZConnection): Task[CopyIn] =
           |      c.access(_.unwrap(classOf[PGConnection])).flatMap(c => ZIO.attemptBlocking(c.getCopyAPI.copyIn(copyCommand)))
           |
           |    def cancelCopy(copyIn: CopyIn): ZIO[Any, Throwable, Unit] =
           |      ZIO.attemptBlocking(copyIn.cancelCopy()).catchAll {
           |        case x: PSQLException if x.getMessage == "Tried to cancel an inactive copy operation" => ZIO.unit
           |        case x                                                                                => ZIO.fail(x)
           |      }
           |
           |    for {
           |      c <- ZIO.environment[ZConnection].map(_.get)
           |      copyIn <- ZIO.acquireReleaseExit(startCopy(c)) {
           |        case (copyIn, Exit.Failure(_)) =>
           |          cancelCopy(copyIn).orDie
           |        case (copyIn, Exit.Success(_)) =>
           |          ZIO.attemptBlocking(copyIn.endCopy()).onError(_ => cancelCopy(copyIn).orDie).orDie
           |      }
           |      numRows <- rows
           |        .grouped(batchSize)
           |        .map { group =>
           |          val sb = new StringBuilder
           |          group.foreach { t =>
           |            $Text[T].unsafeEncode(t, sb)
           |            sb ++= "\\n"
           |          }
           |          (group.size, sb.result().getBytes("UTF-8"))
           |        }
           |        .mapZIO { case (num, bytes) =>
           |          ZIO.attemptBlocking(copyIn.writeToCopy(bytes, 0, bytes.length)).as(num)
           |        }
           |        .run(ZSink.sum)
           |    } yield numRows.toLong
           |  }
           |}
           |""".stripMargin

  def Text(implicitOrUsing: ImplicitOrUsing): Code = {
    val implicitValOrGiven: String = implicitOrUsing match {
      case Implicit => "implicit val"
      case Using    => "given"
    }
    val implicitDefOrGiven: String = implicitOrUsing match {
      case Implicit => "implicit def"
      case Using    => "given"
    }

    val paramUsingOrUsing: String = implicitOrUsing match {
      case Implicit => "implicit"
      case Using    => "using"
    }

    raw"""|/** This is `Text` ported from doobie.
          |  *
          |  * It is used to encode rows in string format for the COPY command.
          |  *
          |  * The generic derivation part of the code is stripped, along with comments.
          |  */
          |trait Text[A] { outer =>
          |  def unsafeEncode(a: A, sb: StringBuilder): Unit
          |  def unsafeArrayEncode(a: A, sb: StringBuilder): Unit = unsafeEncode(a, sb)
          |
          |  final def contramap[B](f: B => A): Text[B] =
          |    new Text[B] {
          |      override def unsafeArrayEncode(a: B, sb: StringBuilder): Unit = outer.unsafeArrayEncode(f(a), sb)
          |      override def unsafeEncode(a: B, sb: StringBuilder): Unit = outer.unsafeEncode(f(a), sb)
          |    }
          |}
          |
          |object Text {
          |  def apply[A]($implicitOrUsing ev: Text[A]): ev.type = ev
          |
          |  val DELIMETER: Char = '\t'
          |  val NULL: String = "\\N"
          |
          |  def instance[A](f: (A, StringBuilder) => Unit): Text[A] = (sb, a) => f(sb, a)
          |
          |  // String encoder escapes any embedded `QUOTE` characters.
          |  $implicitValOrGiven stringInstance: Text[String] =
          |    new Text[String] {
          |      // Standard char encodings that don't differ in array context
          |      def stdChar(c: Char, sb: StringBuilder): StringBuilder =
          |        c match {
          |          case '\b' => sb.append("\\b")
          |          case '\f' => sb.append("\\f")
          |          case '\n' => sb.append("\\n")
          |          case '\r' => sb.append("\\r")
          |          case '\t' => sb.append("\\t")
          |          case 0x0b => sb.append("\\v")
          |          case c    => sb.append(c)
          |        }
          |
          |      def unsafeEncode(s: String, sb: StringBuilder): Unit =
          |        s.foreach {
          |          case '\\' => sb.append("\\\\") // backslash must be doubled
          |          case c    => stdChar(c, sb)
          |        }
          |
          |      // I am not confident about this encoder. Postgres seems not to be able to cope with low
          |      // control characters or high whitespace characters so these are simply filtered out in the
          |      // tests. It should accommodate arrays of non-pathological strings but it would be nice to
          |      // have a complete specification of what's actually happening.
          |      override def unsafeArrayEncode(s: String, sb: StringBuilder): Unit = {
          |        sb.append('"')
          |        s.foreach {
          |          case '\"' => sb.append("\\\\\"")
          |          case '\\' => sb.append("\\\\\\\\") // srsly
          |          case c    => stdChar(c, sb)
          |        }
          |        sb.append('"')
          |        ()
          |      }
          |    }
          |
          |  $implicitValOrGiven charInstance: Text[Char] = instance { (n, sb) => sb.append(n.toString); () }
          |  $implicitValOrGiven intInstance: Text[Int] = instance { (n, sb) => sb.append(n); () }
          |  $implicitValOrGiven shortInstance: Text[Short] = instance { (n, sb) => sb.append(n); () }
          |  $implicitValOrGiven longInstance: Text[Long] = instance { (n, sb) => sb.append(n); () }
          |  $implicitValOrGiven floatInstance: Text[Float] = instance { (n, sb) => sb.append(n); () }
          |  $implicitValOrGiven doubleInstance: Text[Double] = instance { (n, sb) => sb.append(n); () }
          |  $implicitValOrGiven bigDecimalInstance: Text[BigDecimal] = instance { (n, sb) => sb.append(n); () }
          |  $implicitValOrGiven booleanInstance: Text[Boolean] = instance { (n, sb) => sb.append(n); () }
          |  $implicitValOrGiven byteArrayInstance: Text[Array[Byte]] = instance { (bs, sb) =>
          |    sb.append("\\\\x")
          |    if (bs.length > 0) {
          |      val hex = BigInt(1, bs).toString(16)
          |      val pad = bs.length * 2 - hex.length
          |      0.until(pad).foreach { _ => sb.append("0") }
          |      sb.append(hex)
          |      ()
          |    }
          |  }
          |
          |  $implicitDefOrGiven option[A]($paramUsingOrUsing A: Text[A]): Text[Option[A]] = instance {
          |    case (Some(a), sb) => A.unsafeEncode(a, sb)
          |    case (None, sb) =>
          |      sb.append(Text.NULL)
          |      ()
          |  }
          |  $implicitDefOrGiven iterableInstance[F[_], A]($paramUsingOrUsing ev: Text[A], f: F[A] => Iterable[A]): Text[F[A]] = instance { (as, sb) =>
          |    var first = true
          |    sb.append("{")
          |    f(as).foreach { a =>
          |      if (first) first = false
          |      else sb.append(',')
          |      ev.unsafeArrayEncode(a, sb)
          |    }
          |    sb.append('}')
          |    ()
          |  }
          |}
          |""".stripMargin
  }

}
