package dev.typr.foundations.scala

import dev.typr.foundations.DbType

import java.util.Optional
import java.util.concurrent.atomic.AtomicInteger
import _root_.scala.jdk.CollectionConverters.*
import _root_.scala.jdk.OptionConverters.*

object SqlExpr {

  /** Bijection between java.lang.Boolean and scala.Boolean (identity at runtime) */
  private val JavaToScalaBool: dev.typr.foundations.dsl.Bijection[java.lang.Boolean, Boolean] =
    dev.typr.foundations.dsl.Bijection.of[java.lang.Boolean, Boolean](
      (jb: java.lang.Boolean) => jb: Boolean,
      (sb: Boolean) => sb: java.lang.Boolean
    )

  /** Convert java.lang.Boolean SqlExpr to scala.Boolean SqlExpr using underlying method */
  def toScalaBool(expr: dev.typr.foundations.dsl.SqlExpr[java.lang.Boolean]): dev.typr.foundations.dsl.SqlExpr[Boolean] =
    expr.underlying(JavaToScalaBool)

  /** Bijection between scala.Boolean and java.lang.Boolean (identity at runtime) */
  private val ScalaToJavaBool: dev.typr.foundations.dsl.Bijection[Boolean, java.lang.Boolean] =
    dev.typr.foundations.dsl.Bijection.of[Boolean, java.lang.Boolean](
      (sb: Boolean) => sb: java.lang.Boolean,
      (jb: java.lang.Boolean) => jb: Boolean
    )

  /** Convert scala.Boolean SqlExpr to java.lang.Boolean SqlExpr using underlying method */
  def toJavaBool(expr: dev.typr.foundations.dsl.SqlExpr[Boolean]): dev.typr.foundations.dsl.SqlExpr[java.lang.Boolean] =
    expr.underlying(ScalaToJavaBool)

  /** Wrapper for SqlExpr.all that accepts and returns scala.Boolean */
  def all(exprs: dev.typr.foundations.dsl.SqlExpr[Boolean]*): dev.typr.foundations.dsl.SqlExpr[Boolean] =
    toScalaBool(dev.typr.foundations.dsl.SqlExpr.all(exprs.map(toJavaBool)*))

  trait FieldLike[T, Row] {
    val underlying: dev.typr.foundations.dsl.SqlExpr.FieldLike[T, Row]

    def path(): List[dev.typr.foundations.dsl.Path] = underlying._path().asScala.toList
    def column(): String = underlying.column()
    def get(row: Row): Optional[T] = underlying.get(row)
    def set(row: Row, value: Optional[T]): dev.typr.foundations.Either[String, Row] = underlying.set(row, value)
    def sqlReadCast(): Optional[String] = underlying.sqlReadCast()
    def sqlWriteCast(): Optional[String] = underlying.sqlWriteCast()
    def pgType(): dev.typr.foundations.DbType[T] = underlying.pgType()
    def render(ctx: dev.typr.foundations.dsl.RenderCtx, counter: AtomicInteger): dev.typr.foundations.Fragment =
      underlying.render(ctx, counter)

    // Comparison operators - all return scala.Boolean
    def isEqual(other: dev.typr.foundations.dsl.SqlExpr[T]): dev.typr.foundations.dsl.SqlExpr[Boolean] =
      toScalaBool(underlying.isEqual(other))

    def isEqual(value: T): dev.typr.foundations.dsl.SqlExpr[Boolean] =
      toScalaBool(underlying.isEqual(value))

    def isNotEqual(other: dev.typr.foundations.dsl.SqlExpr[T]): dev.typr.foundations.dsl.SqlExpr[Boolean] =
      toScalaBool(underlying.isNotEqual(other))

    def isNotEqual(value: T): dev.typr.foundations.dsl.SqlExpr[Boolean] =
      toScalaBool(underlying.isNotEqual(value))

    def greaterThan(other: dev.typr.foundations.dsl.SqlExpr[T]): dev.typr.foundations.dsl.SqlExpr[Boolean] =
      toScalaBool(underlying.greaterThan(other))

    def greaterThan(value: T): dev.typr.foundations.dsl.SqlExpr[Boolean] =
      toScalaBool(underlying.greaterThan(value))

    def greaterThanOrEqual(other: dev.typr.foundations.dsl.SqlExpr[T]): dev.typr.foundations.dsl.SqlExpr[Boolean] =
      toScalaBool(underlying.greaterThanOrEqual(other))

    def greaterThanOrEqual(value: T): dev.typr.foundations.dsl.SqlExpr[Boolean] =
      toScalaBool(underlying.greaterThanOrEqual(value))

    def lessThan(other: dev.typr.foundations.dsl.SqlExpr[T]): dev.typr.foundations.dsl.SqlExpr[Boolean] =
      toScalaBool(underlying.lessThan(other))

    def lessThan(value: T): dev.typr.foundations.dsl.SqlExpr[Boolean] =
      toScalaBool(underlying.lessThan(value))

    def lessThanOrEqual(other: dev.typr.foundations.dsl.SqlExpr[T]): dev.typr.foundations.dsl.SqlExpr[Boolean] =
      toScalaBool(underlying.lessThanOrEqual(other))

    def lessThanOrEqual(value: T): dev.typr.foundations.dsl.SqlExpr[Boolean] =
      toScalaBool(underlying.lessThanOrEqual(value))

    // Logical operators
    def or(other: dev.typr.foundations.dsl.SqlExpr[T], bijection: dev.typr.foundations.dsl.Bijection[T, java.lang.Boolean]): dev.typr.foundations.dsl.SqlExpr[T] =
      underlying.or(other, bijection)

    def and(other: dev.typr.foundations.dsl.SqlExpr[T], bijection: dev.typr.foundations.dsl.Bijection[T, java.lang.Boolean]): dev.typr.foundations.dsl.SqlExpr[T] =
      underlying.and(other, bijection)

    def not(bijection: dev.typr.foundations.dsl.Bijection[T, java.lang.Boolean]): dev.typr.foundations.dsl.SqlExpr[T] =
      underlying.not(bijection)

    // Arithmetic operators
    def plus(other: dev.typr.foundations.dsl.SqlExpr[T]): dev.typr.foundations.dsl.SqlExpr[T] =
      underlying.plus(other)

    def minus(other: dev.typr.foundations.dsl.SqlExpr[T]): dev.typr.foundations.dsl.SqlExpr[T] =
      underlying.minus(other)

    def multiply(other: dev.typr.foundations.dsl.SqlExpr[T]): dev.typr.foundations.dsl.SqlExpr[T] =
      underlying.multiply(other)

    // String operations
    def like(pattern: String, bijection: dev.typr.foundations.dsl.Bijection[T, String]): dev.typr.foundations.dsl.SqlExpr[Boolean] =
      toScalaBool(underlying.like(pattern, bijection))

    def stringAppend(other: dev.typr.foundations.dsl.SqlExpr[T], bijection: dev.typr.foundations.dsl.Bijection[T, String]): dev.typr.foundations.dsl.SqlExpr[T] =
      underlying.stringAppend(other, bijection)

    def lower(bijection: dev.typr.foundations.dsl.Bijection[T, String]): dev.typr.foundations.dsl.SqlExpr[T] =
      underlying.lower(bijection)

    def upper(bijection: dev.typr.foundations.dsl.Bijection[T, String]): dev.typr.foundations.dsl.SqlExpr[T] =
      underlying.upper(bijection)

    def reverse(bijection: dev.typr.foundations.dsl.Bijection[T, String]): dev.typr.foundations.dsl.SqlExpr[T] =
      underlying.reverse(bijection)

    def strpos(substring: dev.typr.foundations.dsl.SqlExpr[String], bijection: dev.typr.foundations.dsl.Bijection[T, String]): dev.typr.foundations.dsl.SqlExpr[java.lang.Integer] =
      underlying.strpos(substring, bijection)

    def strLength(bijection: dev.typr.foundations.dsl.Bijection[T, String]): dev.typr.foundations.dsl.SqlExpr[java.lang.Integer] =
      underlying.strLength(bijection)

    def substring(
        from: dev.typr.foundations.dsl.SqlExpr[java.lang.Integer],
        count: dev.typr.foundations.dsl.SqlExpr[java.lang.Integer],
        bijection: dev.typr.foundations.dsl.Bijection[T, String]
    ): dev.typr.foundations.dsl.SqlExpr[T] =
      underlying.substring(from, count, bijection)

    // Null handling
    def isNull(): dev.typr.foundations.dsl.SqlExpr[Boolean] =
      toScalaBool(underlying.isNull())

    def coalesce(defaultValue: dev.typr.foundations.dsl.SqlExpr[T]): dev.typr.foundations.dsl.SqlExpr[T] =
      underlying.coalesce(defaultValue)

    // Type conversion
    def underlyingValue[TT](bijection: dev.typr.foundations.dsl.Bijection[T, TT]): dev.typr.foundations.dsl.SqlExpr[TT] =
      underlying.underlying(bijection)

    // Array operations
    def in(values: Array[Object], pgType: dev.typr.foundations.DbType[T]): dev.typr.foundations.dsl.SqlExpr[Boolean] =
      toScalaBool(underlying.in(values.asInstanceOf[Array[Object & T]], pgType))

    // Custom operators
    def customBinaryOp[T2](op: String, right: dev.typr.foundations.dsl.SqlExpr[T2], eval: (T, T2) => Boolean): dev.typr.foundations.dsl.SqlExpr[Boolean] =
      toScalaBool(underlying.customBinaryOp(op, right, (a: T, b: T2) => java.lang.Boolean.valueOf(eval(a, b))))

    // Sorting
    def asc: dev.typr.foundations.dsl.SortOrder[T] = dev.typr.foundations.dsl.SortOrder.asc(underlying)
    def desc: dev.typr.foundations.dsl.SortOrder[T] = dev.typr.foundations.dsl.SortOrder.desc(underlying)
  }

  case class Field[T, Row](override val underlying: dev.typr.foundations.dsl.SqlExpr.Field[T, Row]) extends FieldLike[T, Row] {
    def this(
        path: List[dev.typr.foundations.dsl.Path],
        column: String,
        get: Row => T,
        sqlReadCast: Option[String],
        sqlWriteCast: Option[String],
        setter: (Row, T) => Row,
        pgType: DbType[T]
    ) = this(
      dev.typr.foundations.dsl.SqlExpr.Field[T, Row](
        path.asJava,
        column,
        (row: Row) => get(row),
        sqlReadCast.toJava,
        sqlWriteCast.toJava,
        (row: Row, value: T) => setter(row, value),
        pgType
      )
    )

    // Constructor accepting Java List for interface compatibility
    def this(
        path: java.util.List[dev.typr.foundations.dsl.Path],
        column: String,
        get: Row => T,
        sqlReadCast: Option[String],
        sqlWriteCast: Option[String],
        setter: (Row, T) => Row,
        pgType: DbType[T]
    ) = this(path.asScala.toList, column, get, sqlReadCast, sqlWriteCast, setter, pgType)
  }

  case class OptField[T, Row](override val underlying: dev.typr.foundations.dsl.SqlExpr.OptField[T, Row]) extends FieldLike[T, Row] {
    def this(
        path: List[dev.typr.foundations.dsl.Path],
        column: String,
        get: Row => Option[T],
        sqlReadCast: Option[String],
        sqlWriteCast: Option[String],
        setter: (Row, Option[T]) => Row,
        pgType: DbType[T]
    ) = this(
      dev.typr.foundations.dsl.SqlExpr.OptField[T, Row](
        path.asJava,
        column,
        (row: Row) => get(row).toJava,
        sqlReadCast.toJava,
        sqlWriteCast.toJava,
        (row: Row, value: Optional[T]) => setter(row, value.toScala),
        pgType
      )
    )

    // Constructor accepting Java List for interface compatibility
    def this(
        path: java.util.List[dev.typr.foundations.dsl.Path],
        column: String,
        get: Row => Option[T],
        sqlReadCast: Option[String],
        sqlWriteCast: Option[String],
        setter: (Row, Option[T]) => Row,
        pgType: DbType[T]
    ) = this(path.asScala.toList, column, get, sqlReadCast, sqlWriteCast, setter, pgType)

    def getOrNone(row: Row): Option[T] = underlying.get(row).toScala
  }

  case class IdField[T, Row](override val underlying: dev.typr.foundations.dsl.SqlExpr.IdField[T, Row]) extends FieldLike[T, Row] {
    def this(
        path: List[dev.typr.foundations.dsl.Path],
        column: String,
        get: Row => T,
        sqlReadCast: Option[String],
        sqlWriteCast: Option[String],
        setter: (Row, T) => Row,
        pgType: DbType[T]
    ) = this(
      dev.typr.foundations.dsl.SqlExpr.IdField[T, Row](
        path.asJava,
        column,
        (row: Row) => get(row),
        sqlReadCast.toJava,
        sqlWriteCast.toJava,
        (row: Row, value: T) => setter(row, value),
        pgType
      )
    )

    // Constructor accepting Java List for interface compatibility
    def this(
        path: java.util.List[dev.typr.foundations.dsl.Path],
        column: String,
        get: Row => T,
        sqlReadCast: Option[String],
        sqlWriteCast: Option[String],
        setter: (Row, T) => Row,
        pgType: DbType[T]
    ) = this(path.asScala.toList, column, get, sqlReadCast, sqlWriteCast, setter, pgType)
  }

  object CompositeIn {
    def apply[Tuple, Row](
        parts: List[dev.typr.foundations.dsl.SqlExpr.CompositeIn.Part[?, Tuple, Row]],
        tuples: List[Tuple]
    ): dev.typr.foundations.dsl.SqlExpr[Boolean] = {
      toScalaBool(dev.typr.foundations.dsl.SqlExpr.CompositeIn(parts.asJava, tuples.asJava))
    }

    def Part[Id, Tuple, Row](
        field: FieldLike[Id, Row],
        extract: Tuple => Id,
        pgType: dev.typr.foundations.DbType[Id]
    ): dev.typr.foundations.dsl.SqlExpr.CompositeIn.Part[Id, Tuple, Row] = {
      dev.typr.foundations.dsl.SqlExpr.CompositeIn.Part(
        field.underlying,
        (tuple: Tuple) => extract(tuple),
        pgType
      )
    }
  }
}

class ForeignKey[Fields, Row](val underlying: dev.typr.foundations.dsl.ForeignKey[Fields, Row]) {
  def withColumnPair[T](
      thisField: SqlExpr.FieldLike[T, ?],
      otherGetter: Fields => SqlExpr.FieldLike[T, Row]
  ): ForeignKey[Fields, Row] = {
    new ForeignKey(
      underlying.withColumnPair(
        thisField.underlying,
        (fields: Fields) => otherGetter(fields).underlying
      )
    )
  }
}

object ForeignKey {
  def of[Fields, Row](constraintName: String): ForeignKey[Fields, Row] = {
    new ForeignKey(dev.typr.foundations.dsl.ForeignKey.of(constraintName))
  }
}
