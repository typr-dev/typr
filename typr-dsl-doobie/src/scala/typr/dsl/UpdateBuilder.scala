package typr.dsl

import cats.data.NonEmptyList
import cats.syntax.foldable.*
import doobie.ConnectionIO
import doobie.free.connection.delay
import doobie.implicits.toSqlInterpolator
import doobie.util.fragment.Fragment
import doobie.util.{Read, fragments}

trait UpdateBuilder[Fields, Row] {
  protected def params: UpdateParams[Fields, Row]
  protected def withParams(sqlParams: UpdateParams[Fields, Row]): UpdateBuilder[Fields, Row]

  final def where(v: Fields => SqlExpr[Boolean]): UpdateBuilder[Fields, Row] =
    withParams(params.where(v))

  final def setValue[N[_], T](col: Fields => SqlExpr.FieldLikeNotId[T, Row])(value: T)(implicit C: SqlExpr.Const.As[T, T]): UpdateBuilder[Fields, Row] =
    withParams(params.set(col, _ => C(value)))

  final def setComputedValue[T](col: Fields => SqlExpr.FieldLikeNotId[T, Row])(value: SqlExpr.FieldLikeNotId[T, Row] => SqlExpr[T]): UpdateBuilder[Fields, Row] =
    withParams(params.set(col, fields => value(col(fields))))

  final def setComputedValueFromRow[T](col: Fields => SqlExpr.FieldLikeNotId[T, Row])(value: Fields => SqlExpr[T]): UpdateBuilder[Fields, Row] =
    withParams(params.set(col, value))

  def sql(returning: Boolean): Option[Fragment]
  def execute: ConnectionIO[Int]
  def executeReturnChanged: ConnectionIO[List[Row]]
}

object UpdateBuilder {
  def of[Fields, Row](name: String, structure: RelationStructure[Fields, Row], rowParser: Read[Row]): UpdateBuilderSql[Fields, Row] =
    UpdateBuilderSql(name, structure, rowParser, UpdateParams.empty)
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

  override def execute: ConnectionIO[Int] =
    executeReturnChanged.map(_.size)

  override def executeReturnChanged: ConnectionIO[List[Row]] = delay {
    val changed = List.newBuilder[Row]
    map.foreach { case (id, row) =>
      if (params.where.forall(w => structure.untypedEval(w(structure.fields), row).getOrElse(false))) {
        val newRow = params.setters.foldLeft(row) { case (row, set: UpdateParams.Setter[Fields, t, Row]) =>
          val field: SqlExpr.FieldLikeNotId[t, Row] = set.col(structure.fields)
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
    read: Read[Row],
    params: UpdateParams[Fields, Row]
) extends UpdateBuilder[Fields, Row] {
  override def withParams(sqlParams: UpdateParams[Fields, Row]): UpdateBuilder[Fields, Row] =
    copy(params = sqlParams)

  def mkSql(returning: Boolean): Fragment =
    mkSql(RenderCtx.Empty, returning)

  def mkSql(ctx: RenderCtx, returning: Boolean): Fragment = {
    List[Option[Fragment]](
      Some(fr"update ${Fragment.const0(name)}"),
      NonEmptyList.fromList(params.setters) match {
        case None =>
          sys.error("you must specify a columns to set. use `set` method")
        case Some(setters) =>
          val setFragments = setters.map { setter =>
            val fieldExpr = setter.col(structure.fields)
            val valueExpr = setter.value(structure.fields)
            fr"${fieldExpr.render(ctx)} = ${valueExpr.render(ctx)}${fieldExpr.sqlWriteCast.fold(Fragment.empty)(cast => Fragment.const0(s"::$cast"))}"
          }
          Some(fragments.set(setFragments))
      },
      NonEmptyList.fromList(params.where).map { wheres =>
        fragments.whereAnd(
          wheres.map { where =>
            where(structure.fields).render(ctx)
          }
        )
      },
      if (returning) {
        val colFragments = fragments.comma(
          NonEmptyList.fromListUnsafe(structure.columns).map { col =>
            Fragment.const0(col.sqlReadCast.foldLeft("\"" + col.value(ctx) + "\"") { case (acc, cast) => s"$acc::$cast" })
          }
        )
        Some(fr"returning $colFragments")
      } else None
    ).flatten.intercalate(fr" ")
  }

  override def sql(returning: Boolean): Option[Fragment] = {
    Some(mkSql(returning))
  }

  override def execute: ConnectionIO[Int] =
    mkSql(returning = false).update.run

  override def executeReturnChanged: ConnectionIO[List[Row]] = {
    mkSql(returning = true).query(using read).to[List]
  }
}
