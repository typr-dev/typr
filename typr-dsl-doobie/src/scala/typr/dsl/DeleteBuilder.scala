package typr.dsl

import doobie.ConnectionIO
import doobie.free.connection.delay
import doobie.implicits.toSqlInterpolator
import doobie.util.Read
import doobie.util.fragment.Fragment

import scala.annotation.nowarn

trait DeleteBuilder[Fields, Row] {
  protected def params: DeleteParams[Fields]
  protected def withParams(sqlParams: DeleteParams[Fields]): DeleteBuilder[Fields, Row]

  final def where(v: Fields => SqlExpr[Boolean]): DeleteBuilder[Fields, Row] =
    whereStrict(f => v(f).coalesce(false))

  final def whereStrict(v: Fields => SqlExpr[Boolean]): DeleteBuilder[Fields, Row] =
    withParams(params.where(v))

  def sql: Option[Fragment]
  def execute: ConnectionIO[Int]
  def executeReturning: ConnectionIO[List[Row]]
}

object DeleteBuilder {
  def of[Fields, Row](name: String, structure: RelationStructure[Fields, Row], read: Read[Row]): DeleteBuilderSql[Fields, Row] =
    DeleteBuilderSql(name, structure, read, DeleteParams.empty)
}

final case class DeleteBuilderSql[Fields, Row](
    name: String,
    structure: RelationStructure[Fields, Row],
    read: Read[Row],
    params: DeleteParams[Fields]
) extends DeleteBuilder[Fields, Row] {
  override def withParams(sqlParams: DeleteParams[Fields]): DeleteBuilder[Fields, Row] =
    copy(params = sqlParams)

  def mkSql(ctx: RenderCtx, returning: Boolean): Fragment = {
    val cols = structure.columns
      .map(x => x.sqlReadCast.foldLeft("\"" + x.value(ctx) + "\"") { case (acc, cast) => s"$acc::$cast" })
      .mkString(",")

    List[Iterable[Fragment]](
      Some(fr"delete from ${Fragment.const0(name)}"),
      params.where
        .map(w => w(structure.fields))
        .reduceLeftOption(_.and(_))
        .map { where => fr" where " ++ where.render(ctx: RenderCtx) },
      if (returning) Some(fr" returning ${Fragment.const0(cols)}") else None
    ).flatten.reduce(_ ++ _)
  }

  override def sql: Option[Fragment] =
    Some(mkSql(RenderCtx.Empty, returning = false))

  override def execute: ConnectionIO[Int] =
    mkSql(RenderCtx.Empty, returning = false).update.run

  override def executeReturning: ConnectionIO[List[Row]] =
    mkSql(RenderCtx.Empty, returning = true).query(using read).to[List]
}

final case class DeleteBuilderMock[Id, Fields, Row](
    params: DeleteParams[Fields],
    structure: Structure[Fields, Row],
    map: scala.collection.mutable.Map[Id, Row]
) extends DeleteBuilder[Fields, Row] {
  override def withParams(sqlParams: DeleteParams[Fields]): DeleteBuilder[Fields, Row] =
    copy(params = sqlParams)

  override def sql: Option[Fragment] =
    None

  override def execute: ConnectionIO[Int] =
    executeReturning.map(_.size)

  override def executeReturning: ConnectionIO[List[Row]] = delay {
    val changed = List.newBuilder[Row]
    map.foreach { case (id, row) =>
      if (params.where.forall(w => structure.untypedEval(w(structure.fields), row).getOrElse(false))) {
        map.remove(id): @nowarn
        changed += row
      }
    }
    changed.result()
  }
}
