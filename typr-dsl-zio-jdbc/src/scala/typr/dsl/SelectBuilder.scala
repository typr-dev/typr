package typr.dsl

import zio.{Chunk, ZIO}
import zio.jdbc.*

trait SelectBuilder[Fields, Row] {
  def renderCtx: RenderCtx
  def structure: Structure[Fields, Row]

  /** Add a where clause to the query.
    *
    * Consecutive calls to where will be combined with `AND`
    *
    * {{{
    *   productRepo.select
    *    .where(_.`class` === "H")
    *    .where(x => x.daystomanufacture > 25 or x.daystomanufacture <= 0)
    *    .where(x => x.productline === "foo")
    * }}}
    */
  final def where(v: Fields => SqlExpr[Boolean]): SelectBuilder[Fields, Row] =
    withParams(params.where(v))

  final def maybeWhere[T](ot: Option[T])(v: (Fields, T) => SqlExpr[Boolean]): SelectBuilder[Fields, Row] =
    ot match {
      case Some(t) => where(fields => v(fields, t))
      case None    => this
    }

  /** Add an order by clause to the query.
    *
    * Consecutive calls to orderBy will be combined and order kept
    *
    * {{{
    *      productRepo.select
    *        .join(unitmeasureRepo.select.where(_.name.like("name%")))
    *        .on { case (p, um) => p.sizeunitmeasurecode === um.unitmeasurecode }
    *        .join(projectModelRepo.select)
    *        .leftOn { case ((product, _), productModel) => product.productmodelid === productModel.productmodelid }
    *        .orderBy { case ((product, _), _) => product.productmodelid.asc }
    *        .orderBy { case ((_, _), productModel) => productModel(_.name).desc.withNullsFirst }
    * }}}
    */
  final def orderBy[T](v: Fields => SortOrder[T]): SelectBuilder[Fields, Row] =
    withParams(params.orderBy(v))

  final def seek[T](v: Fields => SortOrder[T])(value: SqlExpr.Const[T]): SelectBuilder[Fields, Row] =
    withParams(params.seek(v, value))

  final def maybeSeek[T](v: Fields => SortOrder[T])(maybeValue: Option[T])(implicit asConst: SqlExpr.Const.As[T, T]): SelectBuilder[Fields, Row] =
    maybeValue match {
      case Some(value) => seek(v)(asConst(value))
      case None        => orderBy(v)
    }

  final def offset(v: Int): SelectBuilder[Fields, Row] =
    withParams(params.offset(v))
  final def limit(v: Int): SelectBuilder[Fields, Row] =
    withParams(params.limit(v))

  /** Execute the query and return the results as a list */
  def toChunk: ZIO[ZConnection, Throwable, Chunk[Row]]

  def count: ZIO[ZConnection, Throwable, Int]

  /** Return sql for debugging. [[None]] if backed by a mock repository */
  def sql: Option[SqlFragment]

  final def joinFk[Fields2, Row2](f: Fields => ForeignKey[Fields2, Row2])(other: SelectBuilder[Fields2, Row2]): SelectBuilder[Fields ~ Fields2, Row ~ Row2] =
    joinOn[Fields2, Row2](other) { case (thisFields, thatFields) =>
      val fk: ForeignKey[Fields2, Row2] = f(thisFields)

      fk.columnPairs
        .map { case columnPair: ForeignKey.ColumnPair[t, Fields2] =>
          val left: SqlExpr[t] = columnPair.thisField
          val right: SqlExpr[t] = columnPair.thatField(thatFields)
          left === right
        }
        .reduce(_.and(_))
    }

  /** start constructing a join */
  final def join[F2, Row2](other: SelectBuilder[F2, Row2]): PartialJoin[F2, Row2] =
    new PartialJoin[F2, Row2](other)

  final class PartialJoin[Fields2, Row2](other: SelectBuilder[Fields2, Row2]) {
    def onFk(f: Fields => ForeignKey[Fields2, Row2]): SelectBuilder[Fields ~ Fields2, Row ~ Row2] =
      joinFk(f)(other)

    /** inner join with the given predicate
      * {{{
      *  val query = productRepo.select
      *   .join(unitmeasureRepo.select.where(_.name.like("name%")))
      *   .on { case (p, um) => p.sizeunitmeasurecode === um.unitmeasurecode }
      * }}}
      */
    def on(pred: Fields ~ Fields2 => SqlExpr[Boolean]): SelectBuilder[Fields ~ Fields2, Row ~ Row2] =
      joinOn(other)(pred)

    /** left join with the given predicate
      * {{{
      * val leftJoined = productRepo.select
      * .join(projectModelRepo.select)
      * .leftOn { case (p, pm) => p.productmodelid === pm.productmodelid }
      * }}}
      */
    def leftOn(pred: Fields ~ Fields2 => SqlExpr[Boolean]): SelectBuilder[Fields ~ Fields2, Row ~ Option[Row2]] =
      leftJoinOn(other)(pred)
  }

  protected def params: SelectParams[Fields, Row]

  protected def withParams(sqlParams: SelectParams[Fields, Row]): SelectBuilder[Fields, Row]

  def joinOn[Fields2, Row2](other: SelectBuilder[Fields2, Row2])(pred: Fields ~ Fields2 => SqlExpr[Boolean]): SelectBuilder[Fields ~ Fields2, Row ~ Row2]

  def leftJoinOn[Fields2, Row2](other: SelectBuilder[Fields2, Row2])(
      pred: Fields ~ Fields2 => SqlExpr[Boolean]
  ): SelectBuilder[Fields ~ Fields2, Row ~ Option[Row2]]
}

object SelectBuilder {
  def of[Fields, Row](
      name: String,
      structure: RelationStructure[Fields, Row],
      rowParser: JdbcDecoder[Row]
  ): SelectBuilderSql[Fields, Row] =
    SelectBuilderSql.Relation(name, structure, rowParser, SelectParams.empty)
}
