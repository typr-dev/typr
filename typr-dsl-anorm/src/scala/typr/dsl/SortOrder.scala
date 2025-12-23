package typr.dsl

import typr.dsl.Fragment.FragmentStringInterpolator

import java.util.concurrent.atomic.AtomicInteger

// sort by a field
final case class SortOrder[T](expr: SqlExpr[T], ascending: Boolean, nullsFirst: Boolean) {
  def withNullsFirst: SortOrder[T] = copy(nullsFirst = true)
  def render(ctx: RenderCtx, counter: AtomicInteger): Fragment =
    List[Fragment](
      expr.render(ctx, counter: AtomicInteger),
      if (ascending) frag"ASC" else frag"DESC",
      if (nullsFirst) frag"NULLS FIRST" else Fragment.empty
    ).mkFragment(" ")
}
