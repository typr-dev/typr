package typr.dsl.pagination

import typr.dsl.{RenderCtx, SortOrderNoHkt}

/** A client cursor is inherently tied to a set of sort orderings.
  *
  * As such we encode them in the cursor itself, and verify them on the way in.
  */
case class SortOrderRepr(expr: String) extends AnyVal

object SortOrderRepr {
  def from[T](x: SortOrderNoHkt[T], ctx: RenderCtx): SortOrderRepr = {
    val internals = x.expr.render(ctx).internals
    // todo: deconstructing the sql string and replacing `?` with the value would yield a more readable result
    val sql = internals.elements match {
      case Nil      => internals.sql
      case nonEmpty => internals.sql + ":" + nonEmpty.mkString(",")
    }
    SortOrderRepr(sql.trim)
  }
}
