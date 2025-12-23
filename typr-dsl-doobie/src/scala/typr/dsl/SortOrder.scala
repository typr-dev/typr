package typr.dsl

import cats.syntax.foldable.*
import doobie.implicits.toSqlInterpolator
import doobie.util.fragment.Fragment

sealed trait SortOrderNoHkt[T] {
  val expr: SqlExpr[T]
  val ascending: Boolean
  val nullsFirst: Boolean

  def withNullsFirst: SortOrderNoHkt[T]

  def render(ctx: RenderCtx): Fragment =
    List(
      expr.render(ctx),
      if (ascending) fr"ASC" else fr"DESC",
      if (nullsFirst) fr"NULLS FIRST" else Fragment.empty
    ).intercalate(fr" ")
}

// sort by a field
final case class SortOrder[T](expr: SqlExpr[T], ascending: Boolean, nullsFirst: Boolean) extends SortOrderNoHkt[T] {
  def withNullsFirst: SortOrder[T] = copy(nullsFirst = true)
}
