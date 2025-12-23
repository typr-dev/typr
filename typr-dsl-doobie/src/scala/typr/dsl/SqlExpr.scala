package typr.dsl

import cats.implicits.toFoldableOps
import doobie.Fragment
import doobie.implicits.toSqlInterpolator
import doobie.util.{Put, Write}
import typr.dsl.internal.mkFragment.FragmentOps

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

  final infix def in(ts: Array[T])(implicit ev: Put[Array[T]]): SqlExpr[Boolean] =
    SqlExpr.In(this, ts, ev)

  def render(ctx: RenderCtx): Fragment
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
    final def render(ctx: RenderCtx): Fragment = Fragment.const0(value(ctx)) ++ Fragment.const("")
  }

  sealed trait FieldLikeNotId[T, R] extends FieldLike[T, R]

  // connect a field `name` to a type `T` for a relation `R`
  case class Field[T, R](path: List[Path], name: String, sqlReadCast: Option[String], sqlWriteCast: Option[String], getRaw: R => T, setRaw: (R, T) => R) extends FieldLikeNotId[T, R] {
    override val get: R => Option[T] =
      row => Some(getRaw(row))
    override val set: (R, Option[T]) => Either[String, R] = {
      case (row, Some(t)) => Right(setRaw(row, t))
      case (_, None)      => Left(s"Expected non-null value for ${name}")
    }
  }

  case class IdField[T, R](path: List[Path], name: String, sqlReadCast: Option[String], sqlWriteCast: Option[String], getRaw: R => T, setRaw: (R, T) => R) extends FieldLike[T, R] {
    override val get: R => Option[T] =
      row => Some(getRaw(row))
    override val set: (R, Option[T]) => Either[String, R] = {
      case (row, Some(t)) => Right(setRaw(row, t))
      case (_, None)      => Left(s"Expected non-null value for ${name}")
    }
  }

  case class OptField[T, R](path: List[Path], name: String, sqlReadCast: Option[String], sqlWriteCast: Option[String], get: R => Option[T], setRaw: (R, Option[T]) => R) extends FieldLikeNotId[T, R] {
    override val set: (R, Option[T]) => Either[String, R] = (row, ot) => Right(setRaw(row, ot))
  }

  sealed trait Const[T] extends SqlExpr[T]

  case class ConstReq[T](value: T, P: Put[T], W: Write[T]) extends Const[T] {
    override def render(ctx: RenderCtx): Fragment = {
      val cast = P.vendorTypeNames match {
        case Nil       => Fragment.empty
        case head :: _ => Fragment.const0(s"::${head}")
      }
      fr"${W.toFragment(value)}$cast"
    }
  }
  case class ConstOpt[T](value: Option[T], P: Put[T], W: Write[Option[T]]) extends Const[T] {
    override def render(ctx: RenderCtx): Fragment = {
      val cast = P.vendorTypeNames match {
        case Nil       => Fragment.empty
        case head :: _ => Fragment.const0(s"::${head}")
      }
      fr"${W.toFragment(value)}$cast"
    }
  }

  object Const {
    trait As[I, O] {
      def apply(value: I): SqlExpr.Const[O]
    }

    trait As0 {
      implicit def as[T](implicit P: Put[T]): As[T, T] =
        (value: T) => SqlExpr.ConstReq(value, P, Write.fromPut(using P))
    }
    object As extends As0 {
      implicit def asOpt[T](implicit P: Put[T]): As[Option[T], T] =
        (value: Option[T]) => SqlExpr.ConstOpt(value, P, Write.fromPut(using P).toOpt)
    }
  }

  case class ArrayIndex[T](arr: SqlExpr[Array[T]], idx: SqlExpr[Int]) extends SqlExpr[T] {
    override def render(ctx: RenderCtx): Fragment =
      fr"${arr.render(ctx)}[${idx.render(ctx)}]"
  }

  case class Apply1[T1, O](f: SqlFunction1[T1, O], arg1: SqlExpr[T1]) extends SqlExpr[O] {
    override def render(ctx: RenderCtx): Fragment =
      fr"${Fragment.const0(f.name)}(${arg1.render(ctx)})"
  }

  case class Apply2[T1, T2, O](f: SqlFunction2[T1, T2, O], arg1: SqlExpr[T1], arg2: SqlExpr[T2]) extends SqlExpr[O] {
    override def render(ctx: RenderCtx): Fragment =
      fr"${Fragment.const0(f.name)}(${arg1.render(ctx)}, ${arg2.render(ctx)})"
  }

  case class Apply3[T1, T2, T3, O](
      f: SqlFunction3[T1, T2, T3, O],
      arg1: SqlExpr[T1],
      arg2: SqlExpr[T2],
      arg3: SqlExpr[T3]
  ) extends SqlExpr[O] {
    override def render(ctx: RenderCtx): Fragment =
      fr"${Fragment.const0(f.name)}(${arg1.render(ctx)}, ${arg2.render(ctx)}, ${arg3.render(ctx)})"
  }

  case class Binary[T1, T2, O](left: SqlExpr[T1], op: SqlOperator[T1, T2, O], right: SqlExpr[T2]) extends SqlExpr[O] {
    override def render(ctx: RenderCtx): Fragment =
      fr"(${left.render(ctx)} ${Fragment.const0(op.name)} ${right.render(ctx)})"
  }

  case class Underlying[T, TT](expr: SqlExpr[T], bijection: Bijection[T, TT]) extends SqlExpr[TT] {
    override def render(ctx: RenderCtx): Fragment =
      expr.render(ctx)
  }

  case class Coalesce[T](expr: SqlExpr[T], getOrElse: SqlExpr[T]) extends SqlExpr[T] {
    override def render(ctx: RenderCtx): Fragment =
      fr"coalesce(${expr.render(ctx)}, ${getOrElse.render(ctx)})"
  }

  case class In[T](expr: SqlExpr[T], values: Array[T], ev: Put[Array[T]]) extends SqlExpr[Boolean] {
    override def render(ctx: RenderCtx): Fragment =
      fr"${expr.render(ctx)} = ANY(${new Write.Single(ev).toFragment(values)})"
  }

  case class CompositeIn[Tuple, Row](tuples: Array[Tuple])(val parts: CompositeIn.TuplePart[Tuple, ?, Row]*) extends SqlExpr[Boolean] {
    override def render(ctx: RenderCtx): Fragment = {
      val fieldNames: Seq[Fragment] =
        parts.map(part => fr"${part.field.render(ctx)}")

      val unnests: Seq[Fragment] =
        parts.map { case part: CompositeIn.TuplePart[Tuple, t, Row] =>
          val partExpr: Const[Array[t]] = part.asConst(tuples.map(part.extract)(part.CT))
          sql"unnest(${partExpr.render(ctx)})"
        }

      sql"(${fieldNames.mkFragment(fr",")}) in (select ${unnests.mkFragment(fr",")})"
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
    override def render(ctx: RenderCtx): Fragment = fr"(${exprs.map(_.render(ctx)).intercalate(fr",")})"
  }

  final case class IsNull[T](expr: SqlExpr[T]) extends SqlExpr[Boolean] {
    override def render(ctx: RenderCtx): Fragment =
      fr"${expr.render(ctx)} IS NULL"
  }

  case class Not[T](expr: SqlExpr[T], B: Bijection[T, Boolean]) extends SqlExpr[T] {
    override def render(ctx: RenderCtx): Fragment =
      fr"NOT ${expr.render(ctx)}"
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
