package typr.scaladsl

import java.sql.ResultSet
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

/** Scala wrapper for typr.runtime.RowParser that provides Scala-native methods.
  *
  * This class has the same API surface as the Java RowParser but returns Scala types (Option[T]) instead of Java types (Optional[T]).
  */
class RowParser[Row](val underlying: typr.runtime.RowParser[Row]) {

  /** Parse all rows from a ResultSet. Returns Scala List instead of java.util.List.
    */
  def all(): ResultSetParser[List[Row]] = {
    val javaParser = underlying.all()
    new ResultSetParser(new typr.runtime.ResultSetParser[List[Row]] {
      override def apply(rs: ResultSet): List[Row] = javaParser.apply(rs).asScala.toList
    })
  }

  /** Parse exactly one row from a ResultSet. Returns Row directly (throws if not exactly one row).
    */
  def exactlyOne(): ResultSetParser[Row] = {
    new ResultSetParser(underlying.exactlyOne())
  }

  /** Parse the first row from a ResultSet or None if empty. Returns Option[Row] instead of Optional[Row].
    */
  def first(): ResultSetParser[Option[Row]] = {
    val javaParser = new typr.runtime.ResultSetParser.First(underlying)
    new ResultSetParser(new typr.runtime.ResultSetParser[Option[Row]] {
      override def apply(rs: ResultSet): Option[Row] = javaParser.apply(rs).toScala
    })
  }

  /** Parse the first row from a ResultSet or None if empty. Alias for first() to match Java API.
    */
  def firstOrNone(): ResultSetParser[Option[Row]] = first()

  /** Parse at most one row from a ResultSet or None. Returns Option[Row] instead of Optional[Row].
    */
  def maxOne(): ResultSetParser[Option[Row]] = {
    val javaParser = new typr.runtime.ResultSetParser.MaxOne(underlying)
    new ResultSetParser(new typr.runtime.ResultSetParser[Option[Row]] {
      override def apply(rs: ResultSet): Option[Row] = javaParser.apply(rs).toScala
    })
  }

  /** Parse at most one row from a ResultSet or None. Alias for maxOne() to match Scala conventions.
    */
  def maxOneOrNone(): ResultSetParser[Option[Row]] = maxOne()

  /** Parse a single row from the current position in ResultSet.
    */
  def parse(rs: ResultSet): Row = underlying.parse(rs)
}
