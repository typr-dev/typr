package typr.dsl.pagination

import typr.dsl.{RenderCtx, SortOrderNoHkt}
import zio.jdbc.SqlFragment

/** A client cursor is inherently tied to a set of sort orderings.
  *
  * As such we encode them in the cursor itself, and verify them on the way in.
  */
case class SortOrderRepr(expr: String) extends AnyVal

object SortOrderRepr {
  def from[T](x: SortOrderNoHkt[T], ctx: RenderCtx): SortOrderRepr = {
    val sql = new StringBuilder()

    // no usable way to just get the sql without parameters in zio-jdbc :(
    def recAdd(x: SqlFragment): Unit =
      x.segments.foreach {
        case SqlFragment.Segment.Empty           => ()
        case SqlFragment.Segment.Syntax(value)   => sql.append(value)
        case SqlFragment.Segment.Param(value, _) => sql.append(value)
        case SqlFragment.Segment.Nested(sql)     => recAdd(sql)
      }

    // note `x.expr`! the value is independent of ascending/descending and nulls first/last
    recAdd(x.expr.render(ctx))

    SortOrderRepr(sql.result().trim)
  }
}
