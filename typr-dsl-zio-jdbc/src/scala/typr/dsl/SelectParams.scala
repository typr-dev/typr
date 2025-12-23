package typr.dsl

import zio.NonEmptyChunk
import zio.jdbc.*

final case class SelectParams[Fields, Row](
    where: List[Fields => SqlExpr[Boolean]],
    orderBy: List[OrderByOrSeek[Fields, ?]],
    offset: Option[Int],
    limit: Option[Int]
) {
  def where(v: Fields => SqlExpr[Boolean]): SelectParams[Fields, Row] = copy(where = where :+ v)
  def orderBy[T](f: Fields => SortOrder[T]): SelectParams[Fields, Row] = copy(orderBy = orderBy :+ OrderByOrSeek.OrderBy(f))
  def seek[T](f: Fields => SortOrder[T], value: SqlExpr.Const[T]): SelectParams[Fields, Row] = copy(orderBy = orderBy :+ OrderByOrSeek.Seek(f, value))
  def offset(v: Int): SelectParams[Fields, Row] = copy(offset = Some(v))
  def limit(v: Int): SelectParams[Fields, Row] = copy(limit = Some(v))
}

object SelectParams {
  def empty[Fields, R]: SelectParams[Fields, R] =
    SelectParams[Fields, R](List.empty, List.empty, None, None)

  def render[Fields, R](fields: Fields, ctx: RenderCtx, params: SelectParams[Fields, R]): Option[SqlFragment] = {
    val (filters, orderBys) = OrderByOrSeek.expand(fields, params)
    List[Option[SqlFragment]](
      NonEmptyChunk.fromIterableOption(filters.map(f => f.render(ctx))).map(fs => fs.mkFragment(" WHERE ", " AND ", "")),
      NonEmptyChunk.fromIterableOption(orderBys.map(f => f.render(ctx))).map(fs => fs.mkFragment(" ORDER BY ", ", ", "")),
      params.offset.map(value => sql" offset $value"),
      params.limit.map(value => sql" limit $value")
    ).flatten.reduceOption(_ ++ _)
  }
}
