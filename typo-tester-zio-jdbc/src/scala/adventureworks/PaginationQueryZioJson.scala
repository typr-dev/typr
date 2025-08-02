package adventureworks

import typo.dsl.pagination.*
import typo.dsl.{SelectBuilder, SortOrder, SqlExpr}
import zio.jdbc.ZConnection
import zio.json.ast.Json
import zio.json.{JsonDecoder, JsonEncoder}
import zio.{Chunk, ZIO}

class PaginationQueryZioJson[Fields, Row](underlying: PaginationQuery[Fields, Row, Json]) {
  def andOn[T, N[_]](v: Fields => SortOrder[T, N])(implicit
      e: JsonEncoder[N[T]],
      d: JsonDecoder[N[T]],
      asConst: SqlExpr.Const.As[T, N]
  ): PaginationQueryZioJson[Fields, Row] =
    new PaginationQueryZioJson(underlying.andOn(v)(PaginationQueryZioJson.abstractCodec)(using asConst))

  def done(limit: Int, continueFrom: Option[ClientCursor[Json]]): Either[String, (SelectBuilder[Fields, Row], ServerCursor[Fields, Row, Json])] =
    underlying.done(limit, continueFrom)

  def toChunk(limit: Int, continueFrom: Option[ClientCursor[Json]]): ZIO[ZConnection, Throwable, (Chunk[Row], Option[ClientCursor[Json]])] =
    underlying.done(limit, continueFrom) match {
      case Left(msg) =>
        ZIO.fail(new IllegalArgumentException(msg))
      case Right((newQuery, cursor)) =>
        newQuery.toChunk.map(rows => (rows, cursor.withNewResults(rows).map(_.clientCursor)))
    }
}

object PaginationQueryZioJson {
  implicit val clientCursorEncoder: JsonEncoder[ClientCursor[Json]] =
    JsonEncoder[Map[String, Json]].contramap(_.parts.map { case (k, v) => (k.expr, v) }.toMap)
  implicit val clientCursorDecoder: JsonDecoder[ClientCursor[Json]] =
    JsonDecoder[Map[String, Json]].map(parts => ClientCursor(parts.map { case (k, v) => (SortOrderRepr(k), v) }))

  implicit class PaginationQuerySyntax[Fields, Row](private val query: SelectBuilder[Fields, Row]) extends AnyVal {
    def seekPaginationOn[T, N[_]](v: Fields => SortOrder[T, N])(implicit
        e: JsonEncoder[N[T]],
        d: JsonDecoder[N[T]],
        asConst: SqlExpr.Const.As[T, N]
    ): PaginationQueryZioJson[Fields, Row] =
      new PaginationQueryZioJson(new PaginationQuery(query, Nil).andOn(v)(PaginationQueryZioJson.abstractCodec))
  }

  def abstractCodec[N[_], T](implicit e: JsonEncoder[N[T]], d: JsonDecoder[N[T]]): AbstractJsonCodec[N[T], Json] =
    new AbstractJsonCodec[N[T], Json](e.toJsonAST(_).getOrElse(???), d.fromJsonAST)
}
