package typr.dsl

import anorm.{ResultSetParser, RowParser, SQL, SimpleSql}
import typr.dsl.Fragment.FragmentStringInterpolator

import java.sql.Connection
import java.util.concurrent.atomic.AtomicInteger
import scala.annotation.nowarn

trait UpdateBuilder[Fields, Row] {
  protected def params: UpdateParams[Fields, Row]
  protected def withParams(sqlParams: UpdateParams[Fields, Row]): UpdateBuilder[Fields, Row]

  final def whereStrict(v: Fields => SqlExpr[Boolean]): UpdateBuilder[Fields, Row] =
    withParams(params.where(v))

  final def where(v: Fields => SqlExpr[Boolean]): UpdateBuilder[Fields, Row] =
    withParams(params.where(f => v(f).coalesce(false)))

  final def setValue[N[_], T](col: Fields => SqlExpr.FieldLikeNotId[T, Row])(value: T)(implicit C: SqlExpr.Const.As[T, T]): UpdateBuilder[Fields, Row] =
    withParams(params.set(col, _ => C(value)))

  final def setComputedValue[T](col: Fields => SqlExpr.FieldLikeNotId[T, Row])(value: SqlExpr.FieldLike[T, Row] => SqlExpr[T]): UpdateBuilder[Fields, Row] =
    withParams(params.set(col, fields => value(col(fields))))

  final def setComputedValueFromRow[T](col: Fields => SqlExpr.FieldLikeNotId[T, Row])(value: Fields => SqlExpr[T]): UpdateBuilder[Fields, Row] =
    withParams(params.set(col, value))

  def sql(returning: Boolean): Option[Fragment]
  def execute()(implicit c: Connection): Int
  def executeReturnChanged()(implicit c: Connection): List[Row]
}

object UpdateBuilder {
  def of[Fields, Row](name: String, structure: RelationStructure[Fields, Row], resultSetParser: ResultSetParser[List[Row]]): UpdateBuilderSql[Fields, Row] =
    UpdateBuilderSql(name, structure, resultSetParser, UpdateParams.empty)
}

final case class UpdateBuilderMock[Id, Fields, Row](
    params: UpdateParams[Fields, Row],
    structure: Structure[Fields, Row],
    map: scala.collection.mutable.Map[Id, Row]
) extends UpdateBuilder[Fields, Row] {
  override def withParams(sqlParams: UpdateParams[Fields, Row]): UpdateBuilder[Fields, Row] =
    copy(params = sqlParams)

  override def sql(returning: Boolean): Option[Fragment] =
    None

  override def execute()(implicit c: Connection): Int =
    executeReturnChanged().size

  override def executeReturnChanged()(implicit @nowarn c: Connection): List[Row] = {
    val changed = List.newBuilder[Row]
    map.foreach { case (id, row) =>
      if (params.where.forall(w => structure.untypedEval(w(structure.fields), row).getOrElse(false))) {
        val newRow = params.setters.foldLeft(row) { case (row, set: UpdateParams.Setter[Fields, t, Row]) =>
          val field: SqlExpr.FieldLike[t, Row] = set.col(structure.fields)
          val newValue: Option[t] = structure.untypedEval(set.value(structure.fields), row)
          field.set(row, newValue) match {
            case Left(msg)    => sys.error(msg)
            case Right(value) => value
          }
        }
        map.update(id, newRow)
        changed += newRow
      }
    }
    changed.result()
  }
}

final case class UpdateBuilderSql[Fields, Row](
    name: String,
    structure: RelationStructure[Fields, Row],
    resultSetParser: ResultSetParser[List[Row]],
    params: UpdateParams[Fields, Row]
) extends UpdateBuilder[Fields, Row] {
  override def withParams(sqlParams: UpdateParams[Fields, Row]): UpdateBuilder[Fields, Row] =
    copy(params = sqlParams)

  def mkSql(returning: Boolean): Fragment =
    mkSql(RenderCtx.Empty, new AtomicInteger(0), returning)

  def mkSql(ctx: RenderCtx, counter: AtomicInteger, returning: Boolean): Fragment = {
    params.setters match {
      case Nil => sys.error("you must specify a columns to set. use `set` method")
      case _   => ()
    }
    List[Iterable[Fragment]](
      Some(frag"update ${Fragment(name)}"),
      params.setters.zipWithIndex.map { case (setter, idx) =>
        val fieldExpr = setter.col(structure.fields)
        val valueExpr = setter.value(structure.fields)
        val cast = fieldExpr.sqlWriteCast.fold(Fragment.empty)(cast => Fragment(s"::$cast"))
        Fragment(if (idx == 0) " set " else ", ") ++ fieldExpr.render(ctx, counter) ++ frag" = " ++ valueExpr.render(ctx, counter) ++ cast
      },
      params.where
        .map(w => w(structure.fields))
        .reduceLeftOption(_.and(_))
        .map { where => Fragment(" where ") ++ where.render(ctx, counter) },
      if (returning) {
        val cols = structure.columns
          .map(x => x.sqlReadCast.foldLeft("\"" + x.value(ctx) + "\"") { case (acc, cast) => s"$acc::$cast" })
          .mkString(",")

        Some(frag" returning ${Fragment(cols)}")
      } else None
    ).flatten.reduce(_ ++ _)
  }

  override def sql(returning: Boolean): Option[Fragment] =
    Some(mkSql(returning))

  override def execute()(implicit c: Connection): Int = {
    val frag = mkSql(returning = false)
    SimpleSql(SQL(frag.sql), frag.params.map(_.tupled).toMap, RowParser.successful).executeUpdate()
  }

  override def executeReturnChanged()(implicit c: Connection): List[Row] = {
    val frag = mkSql(returning = true)
    SimpleSql(SQL(frag.sql), frag.params.map(_.tupled).toMap, RowParser.successful).as(resultSetParser)
  }
}
