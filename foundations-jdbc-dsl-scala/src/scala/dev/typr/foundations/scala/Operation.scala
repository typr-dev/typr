package dev.typr.foundations.scala

import _root_.scala.jdk.CollectionConverters.*
import java.sql.{Connection, SQLException}

/** Scala wrapper for dev.typr.foundations.Operation with Scala-native return types.
  *
  * This class wraps the Java Operation interface and provides Scala-friendly methods.
  */
sealed trait Operation[Out] {
  def underlying: dev.typr.foundations.Operation[?]

  def run(conn: Connection): Out

  def runUnchecked(conn: Connection): Out = {
    try {
      run(conn)
    } catch {
      case e: SQLException => throw new RuntimeException(e)
    }
  }
}

object Operation {

  /** Query operation that returns a parsed result */
  class Query[Out](val underlying: dev.typr.foundations.Operation.Query[Out]) extends Operation[Out] {
    override def run(conn: Connection): Out = underlying.run(conn)
  }

  object Query {
    def apply[Out](query: Fragment, parser: ResultSetParser[Out]): Query[Out] =
      new Query(new dev.typr.foundations.Operation.Query(query.underlying, parser.underlying))
  }

  /** Update operation that returns the number of affected rows */
  class Update(val underlying: dev.typr.foundations.Operation.Update) extends Operation[Int] {
    override def run(conn: Connection): Int = underlying.run(conn)
  }

  object Update {
    def apply(query: Fragment): Update =
      new Update(new dev.typr.foundations.Operation.Update(query.underlying))
  }

  /** Update operation with RETURNING clause */
  class UpdateReturning[Out](val underlying: dev.typr.foundations.Operation.UpdateReturning[Out]) extends Operation[Out] {
    override def run(conn: Connection): Out = underlying.run(conn)
  }

  object UpdateReturning {
    def apply[Out](query: Fragment, parser: ResultSetParser[Out]): UpdateReturning[Out] =
      new UpdateReturning(new dev.typr.foundations.Operation.UpdateReturning(query.underlying, parser.underlying))
  }

  /** Update operation with generated keys (Oracle-specific for databases without RETURNING clause) */
  class UpdateReturningGeneratedKeys[Out](val underlying: dev.typr.foundations.Operation.UpdateReturningGeneratedKeys[Out]) extends Operation[Out] {
    override def run(conn: Connection): Out = underlying.run(conn)
  }

  object UpdateReturningGeneratedKeys {
    def apply[Out](query: Fragment, columnNames: Array[String], parser: ResultSetParser[Out]): UpdateReturningGeneratedKeys[Out] =
      new UpdateReturningGeneratedKeys(new dev.typr.foundations.Operation.UpdateReturningGeneratedKeys(query.underlying, columnNames, parser.underlying))
  }

  /** Batch update operation that returns an array of update counts */
  class UpdateMany[Row](val underlying: dev.typr.foundations.Operation.UpdateMany[Row]) extends Operation[Array[Int]] {
    override def run(conn: Connection): Array[Int] = underlying.run(conn)
  }

  object UpdateMany {
    def apply[Row](query: Fragment, parser: RowParser[Row], rows: Iterator[Row]): UpdateMany[Row] =
      new UpdateMany(new dev.typr.foundations.Operation.UpdateMany(query.underlying, parser.underlying, rows.asJava))
  }

  /** Batch update operation with RETURNING clause that returns a list of rows */
  class UpdateManyReturning[Row](val underlying: dev.typr.foundations.Operation.UpdateManyReturning[Row]) extends Operation[List[Row]] {
    override def run(conn: Connection): List[Row] = underlying.run(conn).asScala.toList
  }

  object UpdateManyReturning {
    def apply[Row](query: Fragment, parser: RowParser[Row], rows: Iterator[Row]): UpdateManyReturning[Row] =
      new UpdateManyReturning(new dev.typr.foundations.Operation.UpdateManyReturning(query.underlying, parser.underlying, rows.asJava))
  }

  /** Update each row individually with RETURNING clause (for MariaDB) */
  class UpdateReturningEach[Row](val underlying: dev.typr.foundations.Operation.UpdateReturningEach[Row]) extends Operation[List[Row]] {
    override def run(conn: Connection): List[Row] = underlying.run(conn).asScala.toList
  }

  object UpdateReturningEach {
    def apply[Row](query: Fragment, parser: RowParser[Row], rows: Iterator[Row]): UpdateReturningEach[Row] =
      new UpdateReturningEach(new dev.typr.foundations.Operation.UpdateReturningEach(query.underlying, parser.underlying, rows.asJava))
  }
}
