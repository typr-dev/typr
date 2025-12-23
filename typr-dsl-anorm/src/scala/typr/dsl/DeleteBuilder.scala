package typr.dsl

import anorm.{ResultSetParser, RowParser, SQL, SimpleSql}
import typr.dsl.Fragment.FragmentStringInterpolator

import java.sql.Connection
import java.util.concurrent.atomic.AtomicInteger
import scala.annotation.nowarn

trait DeleteBuilder[Fields, Row] {
  protected def params: DeleteParams[Fields]
  protected def withParams(sqlParams: DeleteParams[Fields]): DeleteBuilder[Fields, Row]

  final def where(v: Fields => SqlExpr[Boolean]): DeleteBuilder[Fields, Row] =
    whereStrict(f => v(f).coalesce(false))

  final def whereStrict(v: Fields => SqlExpr[Boolean]): DeleteBuilder[Fields, Row] =
    withParams(params.where(v))

  def sql: Option[Fragment]
  def execute()(implicit c: Connection): Int
  def executeReturning()(implicit c: Connection): List[Row]
}

object DeleteBuilder {
  def of[Fields, Row](name: String, structure: RelationStructure[Fields, Row], resultSetParser: ResultSetParser[List[Row]]): DeleteBuilderSql[Fields, Row] =
    DeleteBuilderSql(name, structure, resultSetParser, DeleteParams.empty)
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

  override def execute()(implicit c: Connection): Int =
    executeReturning().size

  override def executeReturning()(implicit @nowarn c: Connection): List[Row] = {
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

final case class DeleteBuilderSql[Fields, Row](
    name: String,
    structure: RelationStructure[Fields, Row],
    resultSetParser: ResultSetParser[List[Row]],
    params: DeleteParams[Fields]
) extends DeleteBuilder[Fields, Row] {
  override def withParams(sqlParams: DeleteParams[Fields]): DeleteBuilder[Fields, Row] =
    copy(params = sqlParams)

  def mkSql(ctx: RenderCtx, returning: Boolean): Fragment = {
    val cols = structure.columns
      .map(x => x.sqlReadCast.foldLeft("\"" + x.value(ctx) + "\"") { case (acc, cast) => s"$acc::$cast" })
      .mkString(",")

    List[Iterable[Fragment]](
      Some(frag"delete from ${Fragment(name)}"),
      params.where
        .map(w => w(structure.fields))
        .reduceLeftOption(_.and(_))
        .map { where => Fragment(" where ") ++ where.render(ctx, new AtomicInteger(0)) },
      if (returning) Some(frag" returning ${Fragment(cols)}") else None
    ).flatten.reduce(_ ++ _)
  }

  override def sql: Option[Fragment] =
    Some(mkSql(RenderCtx.Empty, returning = false))

  override def execute()(implicit c: Connection): Int = {
    val frag = mkSql(RenderCtx.Empty, returning = false)
    SimpleSql(SQL(frag.sql), frag.params.map(_.tupled).toMap, RowParser.successful).executeUpdate()
  }

  override def executeReturning()(implicit c: Connection): List[Row] = {
    val frag = mkSql(RenderCtx.Empty, returning = true)
    SimpleSql(SQL(frag.sql), frag.params.map(_.tupled).toMap, RowParser.successful).as(resultSetParser)
  }
}
