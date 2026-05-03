package dev.typr.dslsc

import dev.typr.foundations.data.Json
import dev.typr.foundations.{DbType, Tuple}
import dev.typr.{dsl}

import java.lang
import java.math.BigDecimal as JBigDecimal
import _root_.scala.jdk.CollectionConverters.*
import _root_.scala.jdk.OptionConverters.*

/** Scala ADT for SqlExpr that wraps the Java SqlExpr nodes. Each node corresponds to a node in Java and has an `underlying` field.
  */
sealed trait SqlExpr[T] {
  def underlying: dsl.SqlExpr[T]

  // Comparison operators - return scala.Boolean
  def isEqual(other: SqlExpr[T]): SqlExpr[Boolean] =
    SqlExpr.wrapBool(underlying.isEqual(other.underlying))

  def isNotEqual(other: SqlExpr[T]): SqlExpr[Boolean] =
    SqlExpr.wrapBool(underlying.isNotEqual(other.underlying))

  def greaterThan(other: SqlExpr[T]): SqlExpr[Boolean] =
    SqlExpr.wrapBool(underlying.greaterThan(other.underlying))

  def greaterThanOrEqual(other: SqlExpr[T]): SqlExpr[Boolean] =
    SqlExpr.wrapBool(underlying.greaterThanOrEqual(other.underlying))

  def lessThan(other: SqlExpr[T]): SqlExpr[Boolean] =
    SqlExpr.wrapBool(underlying.lessThan(other.underlying))

  def lessThanOrEqual(other: SqlExpr[T]): SqlExpr[Boolean] =
    SqlExpr.wrapBool(underlying.lessThanOrEqual(other.underlying))

  // Arithmetic operators
  def plus(other: SqlExpr[T]): SqlExpr[T] =
    SqlExpr.wrap(underlying.plus(other.underlying))

  def minus(other: SqlExpr[T]): SqlExpr[T] =
    SqlExpr.wrap(underlying.minus(other.underlying))

  def multiply(other: SqlExpr[T]): SqlExpr[T] =
    SqlExpr.wrap(underlying.multiply(other.underlying))

  // String operations
  def like(pattern: String, bijection: Bijection[T, String]): SqlExpr[Boolean] =
    SqlExpr.wrapBool(underlying.like(pattern, bijection))

  def stringAppend(other: SqlExpr[T], bijection: Bijection[T, String]): SqlExpr[T] =
    SqlExpr.wrap(underlying.stringAppend(other.underlying, bijection))

  def lower(bijection: Bijection[T, String]): SqlExpr[T] =
    SqlExpr.wrap(underlying.lower(bijection))

  def upper(bijection: Bijection[T, String]): SqlExpr[T] =
    SqlExpr.wrap(underlying.upper(bijection))

  def reverse(bijection: Bijection[T, String]): SqlExpr[T] =
    SqlExpr.wrap(underlying.reverse(bijection))

  def strpos(substring: SqlExpr[String], bijection: Bijection[T, String]): SqlExpr[Int] =
    SqlExpr.wrapInt(underlying.strpos(substring.underlying, bijection))

  def strLength(bijection: Bijection[T, String]): SqlExpr[Int] =
    SqlExpr.wrapInt(underlying.strLength(bijection))

  def substring(from: SqlExpr[Int], count: SqlExpr[Int], bijection: Bijection[T, String]): SqlExpr[T] =
    SqlExpr.wrap(underlying.substring(SqlExpr.unwrapInt(from), SqlExpr.unwrapInt(count), bijection))

  // Null handling
  def isNull: SqlExpr[Boolean] =
    SqlExpr.wrapBool(underlying.isNull)

  def isNotNull: SqlExpr[Boolean] =
    SqlExpr.wrapBool(underlying.isNotNull)

  def coalesce(defaultValue: SqlExpr[T]): SqlExpr[T] =
    SqlExpr.wrap(underlying.coalesce(defaultValue.underlying))

  // Type conversion
  def underlyingValue[TT](bijection: Bijection[T, TT]): SqlExpr[TT] =
    SqlExpr.wrap(underlying.underlying(bijection))

  // Range operations
  def between(low: SqlExpr[T], high: SqlExpr[T]): SqlExpr[Boolean] =
    SqlExpr.wrapBool(underlying.between(low.underlying, high.underlying))

  def notBetween(low: SqlExpr[T], high: SqlExpr[T]): SqlExpr[Boolean] =
    SqlExpr.wrapBool(underlying.notBetween(low.underlying, high.underlying))

  // Conditional
  def includeIf(predicate: SqlExpr[Boolean]): SqlExpr[Option[T]] =
    SqlExpr.wrapOpt(underlying.includeIf(SqlExpr.unwrapBool(predicate)))

  // Custom operators
  def customBinaryOp[T2](op: String, right: SqlExpr[T2], eval: (T, T2) => Boolean): SqlExpr[Boolean] =
    SqlExpr.wrapBool(underlying.customBinaryOp(op, right.underlying, (a: T, b: T2) => java.lang.Boolean.valueOf(eval(a, b))))

  // Sorting
  def asc: dsl.SortOrder[T] = dsl.SortOrder.asc(underlying)
  def desc: dsl.SortOrder[T] = dsl.SortOrder.desc(underlying)

  // IN expressions
  /** Check if this expression is IN a list of values */
  def in(values: List[T]): SqlExpr[Boolean] = {
    val rows = dsl.SqlExpr.Rows.of(underlying, values.asJava)
    SqlExpr.wrapBool(new dsl.SqlExpr.In[T, T](underlying, rows))
  }

  // Varargs in/notIn
  def in(values: T*): SqlExpr[Boolean] =
    SqlExpr.wrapBool(underlying.in(values*))

  /** Check if this expression is IN a subquery result */
  def in[F](subquery: SqlExpr.Subquery[F, T]): SqlExpr[Boolean] = {
    val scalaToJavaList = Bijection.of[List[T], java.util.List[T]](_.asJava, _.asScala.toList)
    val javaListExpr = subquery.underlying.underlying(scalaToJavaList)
    SqlExpr.wrapBool(new dsl.SqlExpr.In[T, T](underlying, javaListExpr))
  }

  /** Alias for `in` - reads naturally: `x.among(a, b, c)` */
  def among(values: T*): SqlExpr[Boolean] =
    in(values*)

  /** Alias for in - reads naturally: x.among(a, b, c) */
  def among(values: List[T]): SqlExpr[Boolean] =
    in(values)

  /** Alias for in with subquery */
  def among[F](subquery: SqlExpr.Subquery[F, T]): SqlExpr[Boolean] =
    in(subquery)

  // Tuple creation - returns Scala TupleExpr wrappers
  def tupleWith(): TupleExpr1[T] =
    TupleExpr1(dsl.TupleExpr.of(underlying))

  def tupleWith[T1](e1: SqlExpr[T1]): TupleExpr2[T, T1] =
    TupleExpr2(dsl.TupleExpr.of(underlying, e1.underlying))

  def tupleWith[T1, T2](e1: SqlExpr[T1], e2: SqlExpr[T2]): TupleExpr3[T, T1, T2] =
    TupleExpr3(dsl.TupleExpr.of(underlying, e1.underlying, e2.underlying))

  def tupleWith[T1, T2, T3](e1: SqlExpr[T1], e2: SqlExpr[T2], e3: SqlExpr[T3]): TupleExpr4[T, T1, T2, T3] =
    TupleExpr4(dsl.TupleExpr.of(underlying, e1.underlying, e2.underlying, e3.underlying))

  def tupleWith[T1, T2, T3, T4](e1: SqlExpr[T1], e2: SqlExpr[T2], e3: SqlExpr[T3], e4: SqlExpr[T4]): TupleExpr5[T, T1, T2, T3, T4] =
    TupleExpr5(dsl.TupleExpr.of(underlying, e1.underlying, e2.underlying, e3.underlying, e4.underlying))

  def tupleWith[T1, T2, T3, T4, T5](e1: SqlExpr[T1], e2: SqlExpr[T2], e3: SqlExpr[T3], e4: SqlExpr[T4], e5: SqlExpr[T5]): TupleExpr6[T, T1, T2, T3, T4, T5] =
    TupleExpr6(dsl.TupleExpr.of(underlying, e1.underlying, e2.underlying, e3.underlying, e4.underlying, e5.underlying))
}

object SqlExpr {
  implicit class BooleanOps(private val self: SqlExpr[Boolean]) {

    def and(other: SqlExpr[Boolean]): SqlExpr[Boolean] = {
      val selfUnderlying: dsl.SqlExpr[lang.Boolean] = self.underlying.underlying(SqlExpr.ScalaToJavaBool)
      val otherUnderlying: dsl.SqlExpr[lang.Boolean] = other.underlying.underlying(SqlExpr.ScalaToJavaBool)
      SqlExpr.wrapBool(selfUnderlying.and(otherUnderlying, Bijection.asBool))
    }

    def or(other: SqlExpr[Boolean]): SqlExpr[Boolean] =
      SqlExpr.wrapBool(self.underlying.underlying(SqlExpr.ScalaToJavaBool).or(SqlExpr.unwrapBool(other), Bijection.asBool))

    def not: SqlExpr[Boolean] =
      !self

    def unary_! : SqlExpr[Boolean] =
      SqlExpr.wrapBool(self.underlying.underlying(SqlExpr.ScalaToJavaBool).not(Bijection.asBool))
  }

  implicit class StringOps(private val self: SqlExpr[String]) {

    def like(pattern: String): SqlExpr[Boolean] =
      SqlExpr.wrapBool(self.underlying.like(pattern, Bijection.asString))

    def stringAppend(other: SqlExpr[String]): SqlExpr[String] =
      SqlExpr.wrap(self.underlying.stringAppend(other.underlying, Bijection.asString))

    def stringAppend(value: String): SqlExpr[String] =
      SqlExpr.wrap(self.underlying.asInstanceOf[dsl.SqlExpr.FieldLike[String, ?]].stringAppend(value, Bijection.asString))

    def lower: SqlExpr[String] =
      SqlExpr.wrap(self.underlying.lower(Bijection.asString))

    def upper: SqlExpr[String] =
      SqlExpr.wrap(self.underlying.upper(Bijection.asString))

    def reverse: SqlExpr[String] =
      SqlExpr.wrap(self.underlying.reverse(Bijection.asString))

    def strpos(substring: SqlExpr[String]): SqlExpr[Int] =
      SqlExpr.wrapInt(self.underlying.strpos(substring.underlying, Bijection.asString))

    def strLength: SqlExpr[Int] =
      SqlExpr.wrapInt(self.underlying.strLength(Bijection.asString))

    def substring(from: SqlExpr[Int], count: SqlExpr[Int]): SqlExpr[String] =
      SqlExpr.wrap(self.underlying.substring(SqlExpr.unwrapInt(from), SqlExpr.unwrapInt(count), Bijection.asString))
  }

  // ========== Bijections for primitive type conversion ==========

  val JavaToScalaBool: Bijection[java.lang.Boolean, Boolean] =
    Bijection.of[java.lang.Boolean, Boolean](
      (jb: java.lang.Boolean) => jb: Boolean,
      (sb: Boolean) => sb: java.lang.Boolean
    )

  def JavaToScalaList[T]: Bijection[java.util.List[T], List[T]] =
    Bijection.of[java.util.List[T], List[T]](
      j => j.asScala.toList,
      s => s.asJava
    )

  val ScalaToJavaBool: Bijection[Boolean, java.lang.Boolean] =
    Bijection.of[Boolean, java.lang.Boolean](
      (sb: Boolean) => sb: java.lang.Boolean,
      (jb: java.lang.Boolean) => jb: Boolean
    )

  val JavaToScalaInt: Bijection[java.lang.Integer, Int] =
    Bijection.of[java.lang.Integer, Int](
      (ji: java.lang.Integer) => ji: Int,
      (si: Int) => si: java.lang.Integer
    )

  val ScalaToJavaInt: Bijection[Int, java.lang.Integer] =
    Bijection.of[Int, java.lang.Integer](
      (si: Int) => si: java.lang.Integer,
      (ji: java.lang.Integer) => ji: Int
    )

  val JavaToScalaLong: Bijection[java.lang.Long, Long] =
    Bijection.of[java.lang.Long, Long](
      (jl: java.lang.Long) => jl: Long,
      (sl: Long) => sl: java.lang.Long
    )

  val ScalaToJavaLong: Bijection[Long, java.lang.Long] =
    Bijection.of[Long, java.lang.Long](
      (sl: Long) => sl: java.lang.Long,
      (jl: java.lang.Long) => jl: Long
    )

  val JavaToScalaDouble: Bijection[java.lang.Double, Double] =
    Bijection.of[java.lang.Double, Double](
      (jd: java.lang.Double) => jd: Double,
      (sd: Double) => sd: java.lang.Double
    )

  val ScalaToJavaDouble: Bijection[Double, java.lang.Double] =
    Bijection.of[Double, java.lang.Double](
      (sd: Double) => sd: java.lang.Double,
      (jd: java.lang.Double) => jd: Double
    )

  def optionBijection[T]: Bijection[java.util.Optional[T], Option[T]] =
    Bijection.of[java.util.Optional[T], Option[T]](
      (jo: java.util.Optional[T]) => jo.toScala,
      (so: Option[T]) => so.toJava
    )

  // ========== Wrapping/Unwrapping helpers ==========

  /** Wrap any Java SqlExpr */
  def wrap[T](expr: dsl.SqlExpr[T]): SqlExpr[T] =
    Wrapped(expr)

  /** Wrap a Java SqlExpr[java.lang.Boolean] to Scala SqlExpr[Boolean] */
  def wrapBool(expr: dsl.SqlExpr[java.lang.Boolean]): SqlExpr[Boolean] =
    Wrapped(expr.underlying(JavaToScalaBool))

  /** Unwrap a Scala SqlExpr[Boolean] to Java SqlExpr[java.lang.Boolean] */
  def unwrapBool(expr: SqlExpr[Boolean]): dsl.SqlExpr[java.lang.Boolean] =
    expr.underlying.underlying(ScalaToJavaBool)

  /** Wrap a Java SqlExpr[java.lang.Integer] to Scala SqlExpr[Int] */
  def wrapInt(expr: dsl.SqlExpr[java.lang.Integer]): SqlExpr[Int] =
    Wrapped(expr.underlying(JavaToScalaInt))

  /** Unwrap a Scala SqlExpr[Int] to Java SqlExpr[java.lang.Integer] */
  def unwrapInt(expr: SqlExpr[Int]): dsl.SqlExpr[java.lang.Integer] =
    expr.underlying.underlying(ScalaToJavaInt)

  /** Wrap a Java SqlExpr[java.lang.Long] to Scala SqlExpr[Long] */
  def wrapLong(expr: dsl.SqlExpr[java.lang.Long]): SqlExpr[Long] =
    Wrapped(expr.underlying(JavaToScalaLong))

  /** Unwrap a Scala SqlExpr[Long] to Java SqlExpr[java.lang.Long] */
  def unwrapLong(expr: SqlExpr[Long]): dsl.SqlExpr[java.lang.Long] =
    expr.underlying.underlying(ScalaToJavaLong)

  /** Wrap a Java SqlExpr[java.lang.Double] to Scala SqlExpr[Double] */
  def wrapDouble(expr: dsl.SqlExpr[java.lang.Double]): SqlExpr[Double] =
    Wrapped(expr.underlying(JavaToScalaDouble))

  /** Unwrap a Scala SqlExpr[Double] to Java SqlExpr[java.lang.Double] */
  def unwrapDouble(expr: SqlExpr[Double]): dsl.SqlExpr[java.lang.Double] =
    expr.underlying.underlying(ScalaToJavaDouble)

  /** Wrap a Java SqlExpr[Optional[T]] to Scala SqlExpr[Option[T]] */
  def wrapOpt[T](expr: dsl.SqlExpr[java.util.Optional[T]]): SqlExpr[Option[T]] =
    Wrapped(expr.underlying(optionBijection[T]))

  /** Convert Scala SqlExpr[Boolean] to Java SqlExpr[java.lang.Boolean] - for interop with Java APIs */
  def toJavaBool(expr: SqlExpr[Boolean]): dsl.SqlExpr[java.lang.Boolean] =
    unwrapBool(expr)

  /** Convert Java SqlExpr[java.lang.Boolean] to Scala SqlExpr[Boolean] - for interop with Java APIs */
  def toScalaBool(expr: dsl.SqlExpr[java.lang.Boolean]): SqlExpr[Boolean] =
    wrapBool(expr)

  // ========== Generic wrapper for any Java SqlExpr ==========

  final case class Wrapped[T](underlying: dsl.SqlExpr[T]) extends SqlExpr[T]

  // ========== FieldLike and subclasses ==========

  sealed trait FieldLike[T, Row] extends SqlExpr[T] {
    override def underlying: dsl.SqlExpr.FieldLike[T, Row]

    def path: List[dsl.Path] = underlying._path().asScala.toList
    def column: String = underlying.column()
    def name: String = underlying.name()

    def get(row: Row): Option[T] = underlying.get(row).toScala

    def set(row: Row, value: Option[T]): Either[String, Row] = {
      val result = underlying.set(row, value.toJava)
      result.fold[Either[String, Row]](
        (l: String) => Left(l),
        (r: Row) => Right(r)
      )
    }

    def sqlReadCast: Option[String] = underlying.sqlReadCast().toScala
    def sqlWriteCast: Option[String] = underlying.sqlWriteCast().toScala

    // Value-accepting comparison operators
    def isEqual(value: T): SqlExpr[Boolean] =
      SqlExpr.wrapBool(underlying.isEqual(value))

    def isNotEqual(value: T): SqlExpr[Boolean] =
      SqlExpr.wrapBool(underlying.isNotEqual(value))

    def greaterThan(value: T): SqlExpr[Boolean] =
      SqlExpr.wrapBool(underlying.greaterThan(value))

    def greaterThanOrEqual(value: T): SqlExpr[Boolean] =
      SqlExpr.wrapBool(underlying.greaterThanOrEqual(value))

    def lessThan(value: T): SqlExpr[Boolean] =
      SqlExpr.wrapBool(underlying.lessThan(value))

    def lessThanOrEqual(value: T): SqlExpr[Boolean] =
      SqlExpr.wrapBool(underlying.lessThanOrEqual(value))

    // Value-accepting range operators
    def between(low: T, high: T): SqlExpr[Boolean] =
      SqlExpr.wrapBool(underlying.between(low, high))

    def notBetween(low: T, high: T): SqlExpr[Boolean] =
      SqlExpr.wrapBool(underlying.notBetween(low, high))

    // Value-accepting coalesce
    def coalesce(defaultValue: T): SqlExpr[T] =
      SqlExpr.wrap(underlying.coalesce(defaultValue))

    // Value-accepting arithmetic
    def plus(value: T): SqlExpr[T] =
      SqlExpr.wrap(underlying.plus(value))

    def minus(value: T): SqlExpr[T] =
      SqlExpr.wrap(underlying.minus(value))

    def multiply(value: T): SqlExpr[T] =
      SqlExpr.wrap(underlying.multiply(value))

    // String operations with value
    def stringAppend(value: T, bijection: Bijection[T, String]): SqlExpr[T] =
      SqlExpr.wrap(underlying.stringAppend(value, bijection))
  }

  object FieldLike {

    /** Wrap a Java FieldLike into the appropriate Scala FieldLike subtype */
    def wrap[T, Row](javaFieldLike: dsl.SqlExpr.FieldLike[T, Row]): FieldLike[T, Row] =
      javaFieldLike match {
        case f: dsl.SqlExpr.Field[T, Row]    => Field(f)
        case f: dsl.SqlExpr.OptField[T, Row] => OptField(f)
        case f: dsl.SqlExpr.IdField[T, Row]  => IdField(f)
      }
  }

  final case class Field[T, Row](override val underlying: dsl.SqlExpr.Field[T, Row]) extends FieldLike[T, Row] {
    def this(
        path: java.util.List[dsl.Path],
        column: String,
        get: Row => T,
        sqlReadCast: Option[String],
        sqlWriteCast: Option[String],
        setter: (Row, T) => Row,
        dbType: DbType[T]
    ) = this(
      dsl.SqlExpr.Field[T, Row](
        path,
        column,
        (row: Row) => get(row),
        sqlReadCast.toJava,
        sqlWriteCast.toJava,
        (row: Row, value: T) => setter(row, value),
        dbType
      )
    )
  }

  final case class OptField[T, Row](override val underlying: dsl.SqlExpr.OptField[T, Row]) extends FieldLike[T, Row] {
    def getOrNone(row: Row): Option[T] = underlying.get(row).toScala

    def this(
        path: java.util.List[dsl.Path],
        column: String,
        get: Row => Option[T],
        sqlReadCast: Option[String],
        sqlWriteCast: Option[String],
        setter: (Row, Option[T]) => Row,
        dbType: DbType[T]
    ) = this(
      dsl.SqlExpr.OptField[T, Row](
        path,
        column,
        (row: Row) => get(row).toJava,
        sqlReadCast.toJava,
        sqlWriteCast.toJava,
        (row: Row, value: java.util.Optional[T]) => setter(row, value.toScala),
        dbType
      )
    )
  }

  final case class IdField[T, Row](override val underlying: dsl.SqlExpr.IdField[T, Row]) extends FieldLike[T, Row] {
    def this(
        path: java.util.List[dsl.Path],
        column: String,
        get: Row => T,
        sqlReadCast: Option[String],
        sqlWriteCast: Option[String],
        setter: (Row, T) => Row,
        dbType: DbType[T]
    ) = this(
      dsl.SqlExpr.IdField[T, Row](
        path,
        column,
        (row: Row) => get(row),
        sqlReadCast.toJava,
        sqlWriteCast.toJava,
        (row: Row, value: T) => setter(row, value),
        dbType
      )
    )
  }

  // ========== Const types ==========

  sealed trait Const[T] extends SqlExpr[T] {
    override def underlying: dsl.SqlExpr.Const[T]
  }

  final case class ConstReq[T](override val underlying: dsl.SqlExpr.ConstReq[T]) extends Const[T]

  object ConstReq {
    def apply[T](value: T, dbType: DbType[T]): ConstReq[T] =
      ConstReq(new dsl.SqlExpr.ConstReq(value, dbType))
  }

  final case class ConstOpt[T](override val underlying: dsl.SqlExpr.ConstOpt[T]) extends Const[T] {
    def value: Option[T] = underlying.value().toScala
  }

  object ConstOpt {
    def apply[T](value: Option[T], dbType: DbType[T]): ConstOpt[T] =
      ConstOpt(new dsl.SqlExpr.ConstOpt(value.toJava, dbType))
  }

  // ========== Function applications ==========

  final case class Apply1[T1, O](override val underlying: dsl.SqlExpr.Apply1[T1, O]) extends SqlExpr[O]
  final case class Apply2[T1, T2, O](override val underlying: dsl.SqlExpr.Apply2[T1, T2, O]) extends SqlExpr[O]
  final case class Apply3[T1, T2, T3, O](override val underlying: dsl.SqlExpr.Apply3[T1, T2, T3, O]) extends SqlExpr[O]

  // ========== Operators ==========

  final case class Binary[T1, T2, O](override val underlying: dsl.SqlExpr.Binary[T1, T2, O]) extends SqlExpr[O]
  final case class Not[T](override val underlying: dsl.SqlExpr.Not[T]) extends SqlExpr[T]

  final case class IsNull[T](javaUnderlying: dsl.SqlExpr.IsNull[T]) extends SqlExpr[Boolean] {
    override val underlying: dsl.SqlExpr[Boolean] = javaUnderlying.underlying(JavaToScalaBool)
  }

  final case class Coalesce[T](override val underlying: dsl.SqlExpr.Coalesce[T]) extends SqlExpr[T]
  final case class Underlying[T, TT](override val underlying: dsl.SqlExpr.Underlying[T, TT]) extends SqlExpr[TT]

  // ========== Array/Range operations ==========

  final case class In[T, V <: T](javaUnderlying: dsl.SqlExpr.In[T, V]) extends SqlExpr[Boolean] {
    override val underlying: dsl.SqlExpr[Boolean] = javaUnderlying.underlying(JavaToScalaBool)
  }

  object In {

    /** Create an In expression from lhs TupleExpr and rhs Rows */
    def apply[T <: Tuple, V <: T](
        lhs: TupleExpr[T],
        rhs: dsl.SqlExpr.Rows[V]
    ): In[T, V] =
      In(new dsl.SqlExpr.In(lhs.underlying, rhs))
  }

  final case class Between[T](javaUnderlying: dsl.SqlExpr.Between[T]) extends SqlExpr[Boolean] {
    override val underlying: dsl.SqlExpr[Boolean] = javaUnderlying.underlying(JavaToScalaBool)
  }

  final case class ArrayIndex[T](override val underlying: dsl.SqlExpr.ArrayIndex[T]) extends SqlExpr[T]

  // ========== Row and Composite operations ==========

  // RowExpr returns java List/Optional, need conversion - simplified for now
  final case class RowExpr(javaUnderlying: dsl.SqlExpr.RowExpr) extends SqlExpr[List[Option[Any]]] {
    private val bijection = Bijection.of[java.util.List[java.util.Optional[?]], List[Option[Any]]](
      (jl: java.util.List[java.util.Optional[?]]) => jl.asScala.toList.map(_.toScala),
      (sl: List[Option[Any]]) => sl.map(_.toJava).asJava
    )
    override val underlying: dsl.SqlExpr[List[Option[Any]]] =
      javaUnderlying.underlying(bijection)
  }

  // ========== Subquery operations ==========

  /** Subquery expression for use in IN clauses. Uses sealed trait + Impl pattern to handle Scala/Java type conversion without casts.
    */
  sealed trait Subquery[F, R] extends SqlExpr[List[R]] {}

  object Subquery {

    /** Private implementation with 4 type parameters for proper type tracking. */
    private[dslsc] class Impl[ScalaF, JavaF, ScalaR, JavaR](
        val javaSubquery: dsl.SqlExpr.Subquery[JavaF, JavaR],
        val rowBij: Bijection[ScalaR, JavaR]
    ) extends Subquery[ScalaF, ScalaR] {
      override lazy val underlying: dsl.SqlExpr[List[ScalaR]] = {
        // Convert java.util.List[JavaR] to List[ScalaR]
        val javaToBij: Bijection[java.util.List[JavaR], List[ScalaR]] =
          Bijection.of(
            (jl: java.util.List[JavaR]) => jl.asScala.toList.map(rowBij.from),
            (sl: List[ScalaR]) => sl.map(rowBij.underlying).asJava
          )
        javaSubquery.underlying(javaToBij)
      }
    }

    /** Create a Subquery from a Java subquery with identity bijection. */
    def apply[F, R](javaUnderlying: dsl.SqlExpr.Subquery[F, R]): Subquery[F, R] =
      new Impl(javaUnderlying, Bijection.identity())

    /** Create a Subquery from a SelectBuilder, handling bijection conversion. */
    def fromSelectBuilder[ScalaFields, ScalaRow](sb: SelectBuilder[ScalaFields, ScalaRow]): Subquery[ScalaFields, ScalaRow] = {
      sb match {
        case impl: SelectBuilder.Impl[ScalaFields, jf, ScalaRow, jr] =>
          new Impl[ScalaFields, jf, ScalaRow, jr](impl.javaBuilder.subquery(), impl.rowBij)
      }
    }
  }

  final case class Exists[F, R](javaUnderlying: dsl.SqlExpr.Exists[F, R]) extends SqlExpr[Boolean] {
    override lazy val underlying: dsl.SqlExpr[Boolean] = javaUnderlying.underlying(JavaToScalaBool)
  }

  // ========== Conditional ==========

  final case class IncludeIf[T](javaUnderlying: dsl.SqlExpr.IncludeIf[T]) extends SqlExpr[Option[T]] {
    override lazy val underlying: dsl.SqlExpr[Option[T]] = javaUnderlying.underlying(optionBijection[T])
  }

  // ========== Aggregate functions ==========

  final case class CountStar(javaUnderlying: dsl.SqlExpr.CountStar) extends SqlExpr[Long] {
    override val underlying: dsl.SqlExpr[Long] = javaUnderlying.underlying(JavaToScalaLong)
  }

  object CountStar {
    def apply(): CountStar = CountStar(new dsl.SqlExpr.CountStar())
  }

  final case class Count[T](javaUnderlying: dsl.SqlExpr.Count[T]) extends SqlExpr[Long] {
    override val underlying: dsl.SqlExpr[Long] = javaUnderlying.underlying(JavaToScalaLong)
  }

  final case class CountDistinct[T](javaUnderlying: dsl.SqlExpr.CountDistinct[T]) extends SqlExpr[Long] {
    override val underlying: dsl.SqlExpr[Long] = javaUnderlying.underlying(JavaToScalaLong)
  }

  final case class Sum[T, R](override val underlying: dsl.SqlExpr.Sum[T, R]) extends SqlExpr[R]

  final case class Avg[T](javaUnderlying: dsl.SqlExpr.Avg[T]) extends SqlExpr[Double] {
    override val underlying: dsl.SqlExpr[Double] = javaUnderlying.underlying(JavaToScalaDouble)
  }

  final case class Min[T](override val underlying: dsl.SqlExpr.Min[T]) extends SqlExpr[T]
  final case class Max[T](override val underlying: dsl.SqlExpr.Max[T]) extends SqlExpr[T]
  final case class StringAgg(override val underlying: dsl.SqlExpr.StringAgg) extends SqlExpr[String]

  final case class ArrayAgg[T](javaUnderlying: dsl.SqlExpr.ArrayAgg[T]) extends SqlExpr[List[T]] {
    private val listBijection = Bijection.of[java.util.List[T], List[T]](
      (jl: java.util.List[T]) => jl.asScala.toList,
      (sl: List[T]) => sl.asJava
    )
    override val underlying: dsl.SqlExpr[List[T]] = javaUnderlying.underlying(listBijection)
  }

  final case class JsonAgg[T](override val underlying: dsl.SqlExpr.JsonAgg[T]) extends SqlExpr[Json]

  final case class BoolAnd(javaUnderlying: dsl.SqlExpr.BoolAnd) extends SqlExpr[Boolean] {
    override val underlying: dsl.SqlExpr[Boolean] = javaUnderlying.underlying(JavaToScalaBool)
  }

  final case class BoolOr(javaUnderlying: dsl.SqlExpr.BoolOr) extends SqlExpr[Boolean] {
    override val underlying: dsl.SqlExpr[Boolean] = javaUnderlying.underlying(JavaToScalaBool)
  }

  // ========== Static factory methods ==========

  /** Combine multiple boolean expressions with AND */
  def all(exprs: SqlExpr[Boolean]*): SqlExpr[Boolean] = {
    val javaExprs = exprs.map(unwrapBool).toArray
    wrapBool(dsl.SqlExpr.all(javaExprs*))
  }

  /** Combine multiple boolean expressions with OR */
  def any(exprs: SqlExpr[Boolean]*): SqlExpr[Boolean] = {
    val javaExprs = exprs.map(unwrapBool).toArray
    wrapBool(dsl.SqlExpr.any(javaExprs*))
  }

  /** Check if a subquery returns any rows */
  def exists[F, R](subquery: SelectBuilder[F, R]): SqlExpr[Boolean] =
    wrapBool(dsl.SqlExpr.exists(subquery.javaBuilder))

  /** Check if a subquery returns no rows */
  def notExists[F, R](subquery: SelectBuilder[F, R]): SqlExpr[Boolean] =
    wrapBool(dsl.SqlExpr.notExists(subquery.javaBuilder))

  // ========== Aggregate factory methods ==========

  /** COUNT(*) - count all rows */
  def count(): SqlExpr[Long] = CountStar()

  /** COUNT(expr) - count non-null values */
  def count[T](expr: SqlExpr[T]): SqlExpr[Long] =
    Count(new dsl.SqlExpr.Count(expr.underlying))

  /** COUNT(DISTINCT expr) - count distinct non-null values */
  def countDistinct[T](expr: SqlExpr[T]): SqlExpr[Long] =
    CountDistinct(new dsl.SqlExpr.CountDistinct(expr.underlying))

  /** SUM(expr) for Int - returns Long */
  def sum(expr: SqlExpr[Int]): SqlExpr[Long] = {
    val javaExpr = unwrapInt(expr)
    val javaSum = new dsl.SqlExpr.Sum[java.lang.Integer, java.lang.Long](javaExpr, dev.typr.foundations.PgTypes.int8)
    wrapLong(javaSum)
  }

  /** SUM(expr) for Long - returns Long */
  def sumLong(expr: SqlExpr[Long]): SqlExpr[Long] = {
    val javaExpr = unwrapLong(expr)
    val javaSum = new dsl.SqlExpr.Sum[java.lang.Long, java.lang.Long](javaExpr, dev.typr.foundations.PgTypes.int8)
    wrapLong(javaSum)
  }

  /** SUM(expr) for BigDecimal - returns BigDecimal */
  def sumBigDecimal(expr: SqlExpr[JBigDecimal]): SqlExpr[JBigDecimal] =
    Sum(new dsl.SqlExpr.Sum(expr.underlying, dev.typr.foundations.PgTypes.numeric))

  /** SUM(expr) for Double - returns Double */
  def sumDouble(expr: SqlExpr[Double]): SqlExpr[Double] = {
    val javaExpr = unwrapDouble(expr)
    val javaSum = new dsl.SqlExpr.Sum[java.lang.Double, java.lang.Double](javaExpr, dev.typr.foundations.PgTypes.float8)
    wrapDouble(javaSum)
  }

  /** AVG(expr) - average of numeric values, returns Double */
  def avg[T <: Number](expr: SqlExpr[T]): SqlExpr[Double] =
    Avg(new dsl.SqlExpr.Avg(expr.underlying))

  /** MIN(expr) - minimum value */
  def min[T](expr: SqlExpr[T]): SqlExpr[T] =
    wrap(dsl.SqlExpr.min(expr.underlying))

  /** MAX(expr) - maximum value */
  def max[T](expr: SqlExpr[T]): SqlExpr[T] =
    wrap(dsl.SqlExpr.max(expr.underlying))

  /** STRING_AGG(expr, delimiter) - concatenate strings */
  def stringAgg(expr: SqlExpr[String], delimiter: String): SqlExpr[String] =
    StringAgg(new dsl.SqlExpr.StringAgg(expr.underlying, delimiter))

  /** ARRAY_AGG(expr) - collect values into array */
  def arrayAgg[T](expr: SqlExpr[T], arrayType: DbType[java.util.List[T]]): SqlExpr[List[T]] =
    ArrayAgg(new dsl.SqlExpr.ArrayAgg(expr.underlying, arrayType))

  /** JSON_AGG(expr) - collect values into JSON array */
  def jsonAgg[T](expr: SqlExpr[T]): SqlExpr[Json] =
    JsonAgg(new dsl.SqlExpr.JsonAgg(expr.underlying, dev.typr.foundations.PgTypes.json))

  /** BOOL_AND(expr) - true if all values are true */
  def boolAnd(expr: SqlExpr[Boolean]): SqlExpr[Boolean] =
    BoolAnd(new dsl.SqlExpr.BoolAnd(unwrapBool(expr)))

  /** BOOL_OR(expr) - true if any value is true */
  def boolOr(expr: SqlExpr[Boolean]): SqlExpr[Boolean] =
    BoolOr(new dsl.SqlExpr.BoolOr(unwrapBool(expr)))

  /** Create a constant expression */
  def const[T](value: T, dbType: DbType[T]): SqlExpr[T] =
    ConstReq(value, dbType)

  /** Create an optional constant expression */
  def constOpt[T](value: Option[T], dbType: DbType[T]): SqlExpr[T] =
    ConstOpt(value, dbType)

}

/** Base trait for all Scala TupleExpr wrappers. Extends SqlExpr so tuple expressions can be used anywhere SqlExpr is expected. NOT sealed - generated TupleExprN classes extend this.
  */
trait TupleExpr[T <: Tuple] extends SqlExpr[T] {
  override def underlying: dsl.TupleExpr[T]
}

/** Companion object for TupleExpr - extends generated TupleExprCompanion for factory methods. */
object TupleExpr extends TupleExprCompanion
