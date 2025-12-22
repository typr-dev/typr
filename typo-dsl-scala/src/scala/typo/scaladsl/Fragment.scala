package typo.scaladsl

import scala.jdk.CollectionConverters.*
import java.sql.PreparedStatement
import java.util.concurrent.atomic.AtomicInteger

/** Scala wrapper for typo.runtime.Fragment with Scala-native APIs.
  *
  * This class wraps the Java Fragment interface and provides Scala-friendly methods that use Scala collections and types.
  */
class Fragment(val underlying: typo.runtime.Fragment) extends AnyVal {

  def render(): String = underlying.render()

  def render(sb: java.lang.StringBuilder): Unit = underlying.render(sb)

  def set(stmt: PreparedStatement): Unit = underlying.set(stmt)

  def set(stmt: PreparedStatement, idx: AtomicInteger): Unit = underlying.set(stmt, idx)

  def append(other: Fragment): Fragment = new Fragment(underlying.append(other.underlying))

  def ++(other: Fragment): Fragment = append(other)

  def query[T](parser: ResultSetParser[T]): Operation.Query[T] =
    Operation.Query(this, parser)

  def update(): Operation.Update =
    Operation.Update(this)

  def updateReturning[T](parser: ResultSetParser[T]): Operation.UpdateReturning[T] =
    Operation.UpdateReturning(this, parser)

  def updateMany[Row](parser: RowParser[Row], rows: Iterator[Row]): Operation.UpdateMany[Row] =
    Operation.UpdateMany(this, parser, rows)

  def updateManyReturning[Row](parser: RowParser[Row], rows: Iterator[Row]): Operation.UpdateManyReturning[Row] =
    Operation.UpdateManyReturning(this, parser, rows)

  def updateReturningEach[Row](parser: RowParser[Row], rows: Iterator[Row]): Operation.UpdateReturningEach[Row] =
    Operation.UpdateReturningEach(this, parser, rows)

  /** Oracle-specific: Update with generated keys (for databases that don't support RETURNING clause) */
  def updateReturningGeneratedKeys[T](columnNames: Array[String], parser: ResultSetParser[T]): Operation.UpdateReturningGeneratedKeys[T] =
    Operation.UpdateReturningGeneratedKeys(this, columnNames, parser)
}

object Fragment {
  val EMPTY: Fragment = new Fragment(typo.runtime.Fragment.EMPTY)

  def lit(value: String): Fragment = new Fragment(typo.runtime.Fragment.lit(value))

  def empty(): Fragment = EMPTY

  def quotedDouble(value: String): Fragment = new Fragment(typo.runtime.Fragment.quotedDouble(value))

  def quotedSingle(value: String): Fragment = new Fragment(typo.runtime.Fragment.quotedSingle(value))

  def value[A](value: A, dbType: typo.runtime.DbType[A]): Fragment =
    new Fragment(typo.runtime.Fragment.value(value, dbType))

  /** Encode a value into a SQL fragment using the provided database type. */
  def encode[A](dbType: typo.runtime.DbType[A], value: A): Fragment =
    new Fragment(typo.runtime.Fragment.encode(dbType, value))

  def and(fragments: Fragment*): Fragment =
    new Fragment(typo.runtime.Fragment.and(fragments.map(_.underlying)*))

  def and(fragments: List[Fragment]): Fragment =
    new Fragment(typo.runtime.Fragment.and(fragments.map(_.underlying).asJava))

  def or(fragments: Fragment*): Fragment =
    new Fragment(typo.runtime.Fragment.or(fragments.map(_.underlying)*))

  def or(fragments: List[Fragment]): Fragment =
    new Fragment(typo.runtime.Fragment.or(fragments.map(_.underlying).asJava))

  def whereAnd(fragments: Fragment*): Fragment =
    new Fragment(typo.runtime.Fragment.whereAnd(fragments.map(_.underlying)*))

  def whereAnd(fragments: List[Fragment]): Fragment =
    new Fragment(typo.runtime.Fragment.whereAnd(fragments.map(_.underlying).asJava))

  def whereOr(fragments: Fragment*): Fragment =
    new Fragment(typo.runtime.Fragment.whereOr(fragments.map(_.underlying)*))

  def whereOr(fragments: List[Fragment]): Fragment =
    new Fragment(typo.runtime.Fragment.whereOr(fragments.map(_.underlying).asJava))

  def set(fragments: Fragment*): Fragment =
    new Fragment(typo.runtime.Fragment.set(fragments.map(_.underlying)*))

  def set(fragments: List[Fragment]): Fragment =
    new Fragment(typo.runtime.Fragment.set(fragments.map(_.underlying).asJava))

  def parentheses(fragment: Fragment): Fragment =
    new Fragment(typo.runtime.Fragment.parentheses(fragment.underlying))

  def comma(fragments: Fragment*): Fragment =
    new Fragment(typo.runtime.Fragment.comma(fragments.map(_.underlying)*))

  def comma(fragments: Iterable[Fragment]): Fragment =
    new Fragment(typo.runtime.Fragment.comma(fragments.map(_.underlying).toList.asJava))

  def orderBy(fragments: Fragment*): Fragment =
    new Fragment(typo.runtime.Fragment.orderBy(fragments.map(_.underlying)*))

  def orderBy(fragments: List[Fragment]): Fragment =
    new Fragment(typo.runtime.Fragment.orderBy(fragments.map(_.underlying).asJava))

  def join(fragments: List[Fragment], separator: Fragment): Fragment =
    new Fragment(typo.runtime.Fragment.join(fragments.map(_.underlying).asJava, separator.underlying))

  def concat(fragments: Fragment*): Fragment =
    new Fragment(typo.runtime.Fragment.concat(fragments.map(_.underlying)*))

  def in[T <: Object](column: String, values: Array[T], dbType: typo.runtime.DbType[T]): Fragment =
    new Fragment(typo.runtime.Fragment.in(column, values, dbType))

  def compositeIn(columns: Array[String], tuples: List[Array[Object]], types: Array[typo.runtime.DbType[?]]): Fragment =
    new Fragment(typo.runtime.Fragment.compositeIn(columns, tuples.asJava, types))

  /** Scala string interpolator for creating SQL Fragments.
    *
    * Usage:
    * {{{
    * import typo.scaladsl.Fragment.interpolate
    *
    * val name = Fragment.value("Alice", PgTypes.text)
    * val query = interpolate"SELECT * FROM users WHERE name = $name"
    * }}}
    */
  extension (sc: StringContext) {
    def sql(args: Fragment*): Fragment = {
      val parts = sc.parts.iterator
      val frags = new scala.collection.mutable.ListBuffer[typo.runtime.Fragment]()

      // Add first string part
      if (parts.hasNext) {
        val first = parts.next()
        if (first.nonEmpty) {
          frags += typo.runtime.Fragment.lit(first)
        }
      }

      // Interleave remaining parts with args
      val argsIt = args.iterator
      while (parts.hasNext && argsIt.hasNext) {
        frags += argsIt.next().underlying
        val part = parts.next()
        if (part.nonEmpty) {
          frags += typo.runtime.Fragment.lit(part)
        }
      }

      // Handle any remaining args (shouldn't happen with valid interpolation)
      while (argsIt.hasNext) {
        frags += argsIt.next().underlying
      }

      frags.result() match {
        case Nil           => Fragment.empty()
        case single :: Nil => new Fragment(single)
        case multiple =>
          val javaList = new java.util.ArrayList[typo.runtime.Fragment](multiple.size)
          multiple.foreach(javaList.add)
          new Fragment(new typo.runtime.Fragment.Concat(javaList))
      }
    }
  }

  /** Builder for creating Fragments with a fluent API */
  class Builder(private val underlying: typo.runtime.Fragment.Builder) {
    def sql(s: String): Builder = {
      underlying.sql(s)
      this
    }

    def param[T](dbType: typo.runtime.DbType[T], value: T): Builder = {
      underlying.param(dbType, value)
      this
    }

    def param(fragment: Fragment): Builder = {
      underlying.param(fragment.underlying)
      this
    }

    def done(): Fragment = new Fragment(underlying.done())
  }

  def interpolate(initial: String): Builder =
    new Builder(typo.runtime.Fragment.interpolate(initial))

  def interpolate(fragments: Fragment*): Fragment =
    new Fragment(typo.runtime.Fragment.interpolate(fragments.map(_.underlying)*))
}
