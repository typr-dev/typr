package typr.dsl

import typr.dsl.Fragment.FragmentStringInterpolator

import java.util.concurrent.atomic.AtomicInteger

final case class SelectParams[Fields, Row](
    where: List[Fields => SqlExpr[Boolean]],
    orderBy: List[OrderByOrSeek[Fields, ?]],
    offset: Option[Int],
    limit: Option[Int]
) {
  def where(f: Fields => SqlExpr[Boolean]): SelectParams[Fields, Row] = copy(where = where :+ f)
  def orderBy[T](f: Fields => SortOrder[T]): SelectParams[Fields, Row] = copy(orderBy = orderBy :+ OrderByOrSeek.OrderBy(f))
  def seek[T](f: Fields => SortOrder[T], value: SqlExpr.Const[T]): SelectParams[Fields, Row] = copy(orderBy = orderBy :+ OrderByOrSeek.Seek(f, value))
  def offset(v: Int): SelectParams[Fields, Row] = copy(offset = Some(v))
  def limit(v: Int): SelectParams[Fields, Row] = copy(limit = Some(v))
}

object SelectParams {
  def empty[Fields, R]: SelectParams[Fields, R] =
    SelectParams[Fields, R](List.empty, List.empty, None, None)

  def render[Fields, R](fields: Fields, ctx: RenderCtx, counter: AtomicInteger, params: SelectParams[Fields, R]): Option[Fragment] = {
    val (filters, orderBys) = OrderByOrSeek.expand(fields, params)

    List[Option[Fragment]](
      filters.reduceLeftOption(_.and(_)).map { where =>
        Fragment("where ") ++ where.render(ctx, counter)
      },
      orderBys match {
        case Nil      => None
        case nonEmpty => Some(frag"order by ${nonEmpty.map(x => x.render(ctx, counter)).mkFragment(", ")}")
      },
      params.offset.map(value => Fragment("offset " + value)),
      params.limit.map { value => Fragment("limit " + value) }
    ).flatten.reduceOption(_ ++ Fragment(" ") ++ _)
  }
}
