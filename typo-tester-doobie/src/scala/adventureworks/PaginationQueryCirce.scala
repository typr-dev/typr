package adventureworks

import doobie.free.connection.{ConnectionIO, raiseError}
import io.circe.*
import typo.dsl.pagination.*
import typo.dsl.{SelectBuilder, SortOrder, SqlExpr}

class PaginationQueryCirce[Fields, Row](underlying: PaginationQuery[Fields, Row, Json]) {
  def andOn[T, N[_]](v: Fields => SortOrder[T, N])(implicit
      e: Encoder[N[T]],
      d: Decoder[N[T]],
      asConst: SqlExpr.Const.As[T, N]
  ): PaginationQueryCirce[Fields, Row] =
    new PaginationQueryCirce(underlying.andOn(v)(PaginationQueryCirce.abstractCodec)(using asConst))

  def done(limit: Int, continueFrom: Option[ClientCursor[Json]]): Either[String, (SelectBuilder[Fields, Row], ServerCursor[Fields, Row, Json])] =
    underlying.done(limit, continueFrom)

  def toList(limit: Int, continueFrom: Option[ClientCursor[Json]]): ConnectionIO[(List[Row], Option[ClientCursor[Json]])] =
    underlying.done(limit, continueFrom) match {
      case Left(msg) =>
        raiseError(new IllegalArgumentException(msg))
      case Right((newQuery, cursor)) =>
        newQuery.toList.map(rows => (rows, cursor.withNewResults(rows).map(_.clientCursor)))
    }
}

object PaginationQueryCirce {
  implicit val clientCursorEncoder: Encoder[ClientCursor[Json]] =
    Encoder[Map[String, Json]].contramap(_.parts.map { case (k, v) => (k.expr, v) }.toMap)
  implicit val clientCursorDecoder: Decoder[ClientCursor[Json]] =
    Decoder[Map[String, Json]].map(parts => ClientCursor(parts.map { case (k, v) => (SortOrderRepr(k), v) }))

  implicit class PaginationQuerySyntax[Fields, Row](private val query: SelectBuilder[Fields, Row]) extends AnyVal {
    def seekPaginationOn[T, N[_]](v: Fields => SortOrder[T, N])(implicit
        e: Encoder[N[T]],
        d: Decoder[N[T]],
        asConst: SqlExpr.Const.As[T, N]
    ): PaginationQueryCirce[Fields, Row] =
      new PaginationQueryCirce(new PaginationQuery(query, Nil).andOn(v)(PaginationQueryCirce.abstractCodec))
  }

  def abstractCodec[N[_], T](implicit e: Encoder[N[T]], d: Decoder[N[T]]): AbstractJsonCodec[N[T], Json] =
    new AbstractJsonCodec[N[T], Json](e.apply, json => d.decodeJson(json).left.map(_.message))
}
