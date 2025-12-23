package typr.dsl

import zio.{Chunk, ZIO}
import zio.jdbc.*

import scala.annotation.nowarn

trait DeleteBuilder[Fields, Row] {
  protected def params: DeleteParams[Fields]
  protected def withParams(sqlParams: DeleteParams[Fields]): DeleteBuilder[Fields, Row]

  final def where(v: Fields => SqlExpr[Boolean]): DeleteBuilder[Fields, Row] =
    withParams(params.where(v))

  def sql: Option[SqlFragment]
  def execute: ZIO[ZConnection, Throwable, Long]
  def executeReturning: ZIO[ZConnection, Throwable, Chunk[Row]]
}

object DeleteBuilder {
  def of[Fields, Row](name: String, structure: RelationStructure[Fields, Row], jdbcDecoder: JdbcDecoder[Row]): DeleteBuilderSql[Fields, Row] =
    DeleteBuilderSql(name, structure, jdbcDecoder, DeleteParams.empty)
}

final case class DeleteBuilderSql[Fields, Row](
    name: String,
    structure: RelationStructure[Fields, Row],
    jdbcDecoder: JdbcDecoder[Row],
    params: DeleteParams[Fields]
) extends DeleteBuilder[Fields, Row] {
  override def withParams(sqlParams: DeleteParams[Fields]): DeleteBuilder[Fields, Row] =
    copy(params = sqlParams)

  def mkSql(ctx: RenderCtx, returning: Boolean): SqlFragment = {
    val cols = structure.columns
      .map(x => x.sqlReadCast.foldLeft("\"" + x.value(ctx) + "\"") { case (acc, cast) => s"$acc::$cast" })
      .mkString(",")

    List[Iterable[SqlFragment]](
      Some(SqlFragment.deleteFrom(name)),
      params.where
        .map(w => w(structure.fields))
        .reduceLeftOption(_.and(_))
        .map { where => sql" where " ++ where.render(ctx) },
      if (returning) Some(sql" returning ${SqlFragment(cols)}") else None
    ).flatten.reduce(_ ++ _)
  }

  override def sql: Option[SqlFragment] =
    Some(mkSql(RenderCtx.Empty, returning = false))

  override def execute: ZIO[ZConnection, Throwable, Long] =
    mkSql(RenderCtx.Empty, returning = false).update

  override def executeReturning: ZIO[ZConnection, Throwable, Chunk[Row]] =
    mkSql(RenderCtx.Empty, returning = true).query(using jdbcDecoder).selectAll
}

final case class DeleteBuilderMock[Id, Fields, Row](
    params: DeleteParams[Fields],
    structure: Structure[Fields, Row],
    map: scala.collection.mutable.Map[Id, Row]
) extends DeleteBuilder[Fields, Row] {
  override def withParams(sqlParams: DeleteParams[Fields]): DeleteBuilder[Fields, Row] =
    copy(params = sqlParams)

  override def sql: Option[SqlFragment] = None

  override def execute: ZIO[ZConnection, Throwable, Long] =
    executeReturning.map(_.size.toLong)

  override def executeReturning: ZIO[ZConnection, Throwable, Chunk[Row]] = ZIO.succeed {
    val changed = Chunk.newBuilder[Row]
    map.foreach { case (id, row) =>
      if (params.where.forall(w => structure.untypedEval(w(structure.fields), row).getOrElse(false))) {
        map.remove(id): @nowarn
        changed += row
      }
    }
    changed.result()
  }
}
