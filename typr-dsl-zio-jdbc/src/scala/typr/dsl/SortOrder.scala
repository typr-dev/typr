package typr.dsl

import zio.Chunk
import zio.jdbc.*

sealed trait SortOrderNoHkt[T] {
  val expr: SqlExpr[T]
  val ascending: Boolean
  val nullsFirst: Boolean

  def withNullsFirst: SortOrderNoHkt[T]

  final def render(ctx: RenderCtx): SqlFragment = {
    Chunk(
      expr.render(ctx),
      if (ascending) sql"ASC" else sql"DESC",
      if (nullsFirst) sql"NULLS FIRST" else SqlFragment.empty
    ).mkFragment(" ")
  }
}

// sort by a field
final case class SortOrder[T](expr: SqlExpr[T], ascending: Boolean, nullsFirst: Boolean) extends SortOrderNoHkt[T] {
  def withNullsFirst: SortOrder[T] = copy(nullsFirst = true)
}
