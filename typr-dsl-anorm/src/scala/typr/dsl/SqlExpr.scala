package typr.dsl

import anorm.{NamedParameter, ParameterMetaData, ParameterValue, ToParameterValue}
import typr.dsl.Fragment.FragmentStringInterpolator

import java.util.concurrent.atomic.AtomicInteger
import scala.reflect.ClassTag

sealed trait SqlExpr[T] {
  final def customBinaryOp[T2](op: String, right: SqlExpr[T2])(f: (T, T2) => Boolean): SqlExpr[Boolean] =
    SqlExpr.Binary[T, T2, Boolean](this, new SqlOperator(op, f), right)

  final def isEqual(t: SqlExpr[T]): SqlExpr[Boolean] =
    this === t

  final def isNotEqual(t: SqlExpr[T]): SqlExpr[Boolean] =
    this !== t

  final def ===(t: SqlExpr[T]): SqlExpr[Boolean] =
    SqlExpr.Binary(this, SqlOperator.eq[T], t)

  final def !==(t: SqlExpr[T]): SqlExpr[Boolean] =
    SqlExpr.Binary(this, SqlOperator.neq[T], t)

  final def >(t: SqlExpr[T]): SqlExpr[Boolean] =
    SqlExpr.Binary(this, SqlOperator.gt[T], t)

  final def >=(t: SqlExpr[T]): SqlExpr[Boolean] =
    SqlExpr.Binary(this, SqlOperator.gte[T], t)

  final def <(t: SqlExpr[T]): SqlExpr[Boolean] =
    SqlExpr.Binary(this, SqlOperator.lt[T], t)

  final def <=(t: SqlExpr[T]): SqlExpr[Boolean] =
    SqlExpr.Binary(this, SqlOperator.lte[T], t)

  final infix def or(other: SqlExpr[T])(implicit B: Bijection[T, Boolean]): SqlExpr[T] =
    SqlExpr.Binary(this, SqlOperator.or[T], other)

  final infix def and(other: SqlExpr[T])(implicit B: Bijection[T, Boolean]): SqlExpr[T] =
    SqlExpr.Binary(this, SqlOperator.and[T], other)

  def unary_!(implicit B: Bijection[T, Boolean]): SqlExpr[T] =
    SqlExpr.Not(this, B)

  final def +(t: SqlExpr[T])(implicit Num: Numeric[T]): SqlExpr[T] =
    plus(t)
  final def plus(t: SqlExpr[T])(implicit Num: Numeric[T]): SqlExpr[T] =
    SqlExpr.Binary(this, SqlOperator.plus, t)

  final def -(t: SqlExpr[T])(implicit Num: Numeric[T]): SqlExpr[T] =
    sub(t)
  final def sub(t: SqlExpr[T])(implicit Num: Numeric[T]): SqlExpr[T] =
    SqlExpr.Binary(this, SqlOperator.minus, t)

  final def *(t: SqlExpr[T])(implicit Num: Numeric[T]): SqlExpr[T] =
    mul(t)
  final def mul(t: SqlExpr[T])(implicit Num: Numeric[T]): SqlExpr[T] =
    SqlExpr.Binary(this, SqlOperator.mul, t)

  final def underlying[TT](implicit B: Bijection[T, TT]): SqlExpr[TT] =
    SqlExpr.Underlying(this, B)

  final def like(str: String)(implicit B: Bijection[T, String]): SqlExpr[Boolean] =
    SqlExpr.Binary(this, SqlOperator.like, str)

  final def ||(other: SqlExpr[T])(implicit B: Bijection[T, String]): SqlExpr[T] =
    stringAppend(other)
  final def stringAppend(other: SqlExpr[T])(implicit B: Bijection[T, String]): SqlExpr[T] =
    SqlExpr.Binary(this, SqlOperator.strAdd, other)

  final def lower(implicit B: Bijection[T, String]): SqlExpr[T] =
    SqlExpr.Apply1(SqlFunction1.lower, this)

  final def reverse(implicit B: Bijection[T, String]): SqlExpr[T] =
    SqlExpr.Apply1(SqlFunction1.reverse, this)

  final def upper(implicit B: Bijection[T, String]): SqlExpr[T] =
    SqlExpr.Apply1(SqlFunction1.upper, this)

  final def strpos(substring: SqlExpr[String])(implicit B: Bijection[T, String]): SqlExpr[Int] =
    SqlExpr.Apply2(SqlFunction2.strpos, this, substring)

  final def strLength(implicit B: Bijection[T, String]): SqlExpr[Int] =
    SqlExpr.Apply1(SqlFunction1.length, this)

  final def substring(from: SqlExpr[Int], count: SqlExpr[Int])(implicit
      B: Bijection[T, String]
  ): SqlExpr[T] =
    SqlExpr.Apply3(SqlFunction3.substring[T](), this, from, count)

  final infix def in(ts: Array[T])(implicit ev: ToParameterValue[Array[T]]): SqlExpr[Boolean] =
    SqlExpr.In(this, ts, ev)

  def render(ctx: RenderCtx, counter: AtomicInteger): Fragment
}

object SqlExpr {

  /** Combine multiple boolean expressions with AND. Returns TRUE if all expressions are true. */
  def all(exprs: SqlExpr[Boolean]*): SqlExpr[Boolean] =
    if (exprs.isEmpty) ConstReq(true, implicitly, implicitly)
    else exprs.reduce((a, b) => Binary(a, SqlOperator.and[Boolean], b))

  /** Combine multiple boolean expressions with OR. Returns TRUE if any expression is true. */
  def any(exprs: SqlExpr[Boolean]*): SqlExpr[Boolean] =
    if (exprs.isEmpty) ConstReq(false, implicitly, implicitly)
    else exprs.reduce((a, b) => Binary(a, SqlOperator.or[Boolean], b))

  sealed trait FieldLike[T, R] extends SqlExpr[T] {
    val path: List[Path]
    val name: String
    val get: R => Option[T]
    val set: (R, Option[T]) => Either[String, R]
    val sqlReadCast: Option[String]
    val sqlWriteCast: Option[String]

    final def value(ctx: RenderCtx): String = ctx.alias.get(path).fold("")(alias => s"($alias).") + name
    final def render(ctx: RenderCtx, counter: AtomicInteger): Fragment = Fragment(value(ctx)) ++ Fragment(" ")
  }

  sealed trait FieldLikeNotId[T, R] extends FieldLike[T, R]

  // connect a field `name` to a type `T` for a relation `R`
  case class Field[T, R](path: List[Path], name: String, sqlReadCast: Option[String], sqlWriteCast: Option[String], getRaw: R => T, setRaw: (R, T) => R) extends FieldLikeNotId[T, R] {
    override val get: R => Option[T] =
      row => Some(getRaw(row))
    override val set: (R, Option[T]) => Either[String, R] = {
      case (row, Some(t)) => Right(setRaw(row, t))
      case (_, None)      => Left(s"Expected non-null value for $name")
    }
  }

  case class IdField[T, R](path: List[Path], name: String, sqlReadCast: Option[String], sqlWriteCast: Option[String], getRaw: R => T, setRaw: (R, T) => R) extends FieldLike[T, R] {
    override val get: R => Option[T] =
      row => Some(getRaw(row))
    override val set: (R, Option[T]) => Either[String, R] = {
      case (row, Some(t)) => Right(setRaw(row, t))
      case (_, None)      => Left(s"Expected non-null value for $name")
    }
  }

  case class OptField[T, R](path: List[Path], name: String, sqlReadCast: Option[String], sqlWriteCast: Option[String], get: R => Option[T], setRaw: (R, Option[T]) => R) extends FieldLikeNotId[T, R] {
    override val set: (R, Option[T]) => Either[String, R] = (row, ot) => Right(setRaw(row, ot))
  }

  sealed trait Const[T] extends SqlExpr[T]

  case class ConstReq[T](value: T, T: ToParameterValue[T], P: ParameterMetaData[T]) extends Const[T] {
    override def render(ctx: RenderCtx, counter: AtomicInteger): Fragment = {
      val paramName = s"param${counter.incrementAndGet()}"
      Fragment(List(NamedParameter(paramName, T(value))), s"{$paramName}::${P.sqlType}")
    }
  }
  case class ConstOpt[T](value: Option[T], T: ToParameterValue[Option[T]], P: ParameterMetaData[T]) extends Const[T] {
    override def render(ctx: RenderCtx, counter: AtomicInteger): Fragment = {
      val paramName = s"param${counter.incrementAndGet()}"
      Fragment(List(NamedParameter(paramName, T(value))), s"{$paramName}::${P.sqlType}")
    }
  }

  object Const {
    trait As[I, O] {
      def apply(value: I): SqlExpr.Const[O]
    }

    trait As0 {
      implicit def as[T](implicit T: ToParameterValue[T], P: ParameterMetaData[T]): As[T, T] =
        (value: T) => SqlExpr.ConstReq(value, T, P)
    }

    object As extends As0 {
      implicit def asOpt[T](implicit T: ToParameterValue[Option[T]], P: ParameterMetaData[T]): As[Option[T], T] =
        (value: Option[T]) => SqlExpr.ConstOpt(value, T, P)
    }
  }

  case class ArrayIndex[T](arr: SqlExpr[Array[T]], idx: SqlExpr[Int]) extends SqlExpr[T] {
    override def render(ctx: RenderCtx, counter: AtomicInteger): Fragment =
      frag"${arr.render(ctx, counter)}[${idx.render(ctx, counter)}]"
  }

  case class Apply1[T1, O](f: SqlFunction1[T1, O], arg1: SqlExpr[T1]) extends SqlExpr[O] {
    override def render(ctx: RenderCtx, counter: AtomicInteger): Fragment =
      frag"${Fragment(f.name)}(${arg1.render(ctx, counter)})"
  }

  case class Apply2[T1, T2, O](f: SqlFunction2[T1, T2, O], arg1: SqlExpr[T1], arg2: SqlExpr[T2]) extends SqlExpr[O] {
    override def render(ctx: RenderCtx, counter: AtomicInteger): Fragment =
      frag"${Fragment(f.name)}(${arg1.render(ctx, counter)}, ${arg2.render(ctx, counter)})"
  }

  case class Apply3[T1, T2, T3, O](
      f: SqlFunction3[T1, T2, T3, O],
      arg1: SqlExpr[T1],
      arg2: SqlExpr[T2],
      arg3: SqlExpr[T3]
  ) extends SqlExpr[O] {
    override def render(ctx: RenderCtx, counter: AtomicInteger): Fragment =
      frag"${Fragment(f.name)}(${arg1.render(ctx, counter)}, ${arg2.render(ctx, counter)}, ${arg3.render(ctx, counter)})"
  }

  case class Binary[T1, T2, O](left: SqlExpr[T1], op: SqlOperator[T1, T2, O], right: SqlExpr[T2]) extends SqlExpr[O] {
    override def render(ctx: RenderCtx, counter: AtomicInteger): Fragment =
      frag"(${left.render(ctx, counter)} ${Fragment(op.name)} ${right.render(ctx, counter)})"
  }

  case class Underlying[T, TT](expr: SqlExpr[T], bijection: Bijection[T, TT]) extends SqlExpr[TT] {
    override def render(ctx: RenderCtx, counter: AtomicInteger): Fragment =
      expr.render(ctx, counter)
  }

  case class Coalesce[T](expr: SqlExpr[T], getOrElse: SqlExpr[T]) extends SqlExpr[T] {
    override def render(ctx: RenderCtx, counter: AtomicInteger): Fragment =
      frag"coalesce(${expr.render(ctx, counter)}, ${getOrElse.render(ctx, counter)})"
  }

  case class In[T](expr: SqlExpr[T], values: Array[T], ev: ToParameterValue[Array[T]]) extends SqlExpr[Boolean] {
    override def render(ctx: RenderCtx, counter: AtomicInteger): Fragment = {
      val paramName = s"param${counter.incrementAndGet()}"
      val np = NamedParameter(paramName, ParameterValue.from(values)(ev))
      frag"${expr.render(ctx, counter)} = ANY({${Fragment(List(np), paramName)}})"
    }
  }

  case class CompositeIn[Tuple, Row](tuples: Array[Tuple])(val parts: CompositeIn.TuplePart[Tuple, ?, Row]*) extends SqlExpr[Boolean] {
    override def render(ctx: RenderCtx, counter: AtomicInteger): Fragment = {
      val fieldNames: Seq[Fragment] =
        parts.map(part => frag"${part.field.render(ctx, counter)}")

      val unnests: Seq[Fragment] =
        parts.map { case part: CompositeIn.TuplePart[Tuple, t, Row] =>
          val partExpr: Const[Array[t]] = part.asConst(tuples.map(part.extract)(using part.CT))
          frag"unnest(${partExpr.render(ctx, counter)})"
        }

      frag"(${fieldNames.mkFragment(", ")}) in (select ${unnests.mkFragment(", ")})"
    }
  }

  object CompositeIn {

    /** @param ev
      *   not used. must have a value to write an `AnyVal`
      */
    class TuplePart0[Tuple](private val ev: Boolean) extends AnyVal {
      def apply[T, Row](field: FieldLike[T, Row])(extract: Tuple => T)(implicit asConst: Const.As[Array[T], Array[T]], CT: ClassTag[T]) =
        new TuplePart[Tuple, T, Row](field, extract, asConst, CT)
    }

    object TuplePart {
      def apply[Tuple] = new TuplePart0[Tuple](false)
    }

    class TuplePart[Tuple, T, Row](val field: FieldLike[T, Row], val extract: Tuple => T, val asConst: Const.As[Array[T], Array[T]], val CT: ClassTag[T])
  }

  case class RowExpr(exprs: List[SqlExpr[?]]) extends SqlExpr[List[Option[?]]] {
    override def render(ctx: RenderCtx, counter: AtomicInteger): Fragment =
      frag"(" ++ exprs.map(_.render(ctx, counter)).mkFragment(",") ++ frag")"
  }

  final case class IsNull[T](expr: SqlExpr[T]) extends SqlExpr[Boolean] {
    override def render(ctx: RenderCtx, counter: AtomicInteger): Fragment =
      frag"${expr.render(ctx, counter)} IS NULL"
  }

  case class Not[T](expr: SqlExpr[T], B: Bijection[T, Boolean]) extends SqlExpr[T] {
    override def render(ctx: RenderCtx, counter: AtomicInteger): Fragment =
      frag"NOT ${expr.render(ctx, counter)}"
  }

  // automatically put values in a constant expression
  implicit def asConst[I, O](t: I)(implicit C: Const.As[I, O]): SqlExpr.Const[O] =
    C(t)

  // some syntax to construct field sort order
  implicit class SqlExprSortSyntax[T](private val expr: SqlExpr[T]) extends AnyVal {
    def asc: SortOrder[T] = SortOrder(expr, ascending = true, nullsFirst = false)
    def desc: SortOrder[T] = SortOrder(expr, ascending = false, nullsFirst = false)
  }

  implicit class SqlExprArraySyntax[T](private val expr: SqlExpr[Array[T]]) extends AnyVal {

    /** look up an element in an array at index `idx` */
    def arrayIndex(idx: SqlExpr[Int]): SqlExpr[T] =
      SqlExpr.ArrayIndex[T](expr, idx)

    /** concatenate two arrays */
    def arrayConcat(other: SqlExpr[Array[T]])(implicit C: ClassTag[T]): SqlExpr[Array[T]] =
      SqlExpr.Binary(expr, SqlOperator.arrayConcat, other)

    /** does arrays have elements in common */
    def arrayOverlaps(other: SqlExpr[Array[T]]): SqlExpr[Boolean] =
      SqlExpr.Binary(expr, SqlOperator.arrayOverlaps[Array[T], T], other)
  }

  implicit class SqlExprOptionalSyntax[T](private val expr: SqlExpr[T]) extends AnyVal {
    def isNull: SqlExpr[Boolean] =
      SqlExpr.IsNull(expr)
    def coalesce(orElse: SqlExpr[T]): SqlExpr[T] =
      SqlExpr.Coalesce(expr, orElse)
  }
}
