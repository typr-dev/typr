package typr.dsl

import zio.jdbc.*
import zio.{Chunk, ZIO}

sealed trait SelectBuilderSql[Fields, Row] extends SelectBuilder[Fields, Row] {
  def withPath(path: Path): SelectBuilderSql[Fields, Row]
  def instantiate(ctx: RenderCtx): SelectBuilderSql.Instantiated[Fields, Row]

  override lazy val renderCtx: RenderCtx = RenderCtx.from(this)

  lazy val sqlAndRowParser: (SqlFragment, JdbcDecoder[Row]) = {
    val instance = this.instantiate(renderCtx)
    val cols = instance.columns.map { case (alias, x) =>
      x.sqlReadCast.foldLeft(s"($alias).\"${x.name}\"") { case (acc, cast) => s"$acc::$cast" }
    }

    val ctes = instance.asCTEs
    val formattedCTEs = ctes.map { cte =>
      sql"""${SqlFragment(cte.name)} as (
  ${cte.sql}
)"""
    }

    val frag =
      sql"""with
${formattedCTEs.mkFragment(SqlFragment(",\n"))}
select ${SqlFragment(cols.mkString(","))} from ${SqlFragment(ctes.last.name)}"""

    (frag, instance.read)
  }

  override lazy val sql: Option[SqlFragment] = Some(sqlAndRowParser._1)

  final override def joinOn[Fields2, Row2](other: SelectBuilder[Fields2, Row2])(pred: Fields ~ Fields2 => SqlExpr[Boolean]): SelectBuilder[Fields ~ Fields2, Row ~ Row2] =
    other match {
      case otherSql: SelectBuilderSql[Fields2, Row2] =>
        new SelectBuilderSql.TableJoin[Fields, Fields2, Row, Row2](this.withPath(Path.LeftInJoin), otherSql.withPath(Path.RightInJoin), pred, SelectParams.empty)
      case _ => sys.error("you cannot mix mock and sql repos")
    }

  final override def leftJoinOn[Fields2, Row2](
      other: SelectBuilder[Fields2, Row2]
  )(pred: Fields ~ Fields2 => SqlExpr[Boolean]): SelectBuilder[Fields ~ Fields2, Row ~ Option[Row2]] =
    other match {
      case otherSql: SelectBuilderSql[Fields2, Row2] =>
        SelectBuilderSql.TableLeftJoin(this.withPath(Path.LeftInJoin), otherSql.withPath(Path.RightInJoin), pred, SelectParams.empty)
      case _ => sys.error("you cannot mix mock and sql repos")
    }

  final override def toChunk: ZIO[ZConnection, Throwable, Chunk[Row]] = {
    val (frag, read) = sqlAndRowParser
    frag.query(using read).selectAll
  }
  final override def count: ZIO[ZConnection, Throwable, Int] = {
    val (frag, _) = sqlAndRowParser
    sql"select count(*) from ($frag) rows".query[Int].selectOne.map(_.get)
  }
}

object SelectBuilderSql {
  final case class Relation[Fields, Row](
      name: String,
      structure: RelationStructure[Fields, Row],
      read: JdbcDecoder[Row],
      params: SelectParams[Fields, Row]
  ) extends SelectBuilderSql[Fields, Row] {
    override def withPath(path: Path): SelectBuilderSql[Fields, Row] =
      copy(structure = structure.withPath(path))

    override def withParams(sqlParams: SelectParams[Fields, Row]): SelectBuilder[Fields, Row] =
      copy(params = sqlParams)

    override def instantiate(ctx: RenderCtx): SelectBuilderSql.Instantiated[Fields, Row] = {
      val alias = ctx.alias(structure._path)
      val sql = sql"(select ${SqlFragment(alias)} from ${SqlFragment(name)} ${SqlFragment(alias)} ${SelectParams.render(structure.fields, ctx, params).getOrElse[SqlFragment](SqlFragment.empty)})"

      SelectBuilderSql.Instantiated(
        alias = alias,
        isJoin = false,
        columns = structure.columns.map(c => (alias, c)),
        sqlFrag = sql,
        upstreamCTEs = Nil,
        structure = structure,
        read = read
      )
    }
  }

  final case class TableJoin[Fields1, Fields2, Row1, Row2](
      left: SelectBuilderSql[Fields1, Row1],
      right: SelectBuilderSql[Fields2, Row2],
      pred: Fields1 ~ Fields2 => SqlExpr[Boolean],
      params: SelectParams[Fields1 ~ Fields2, Row1 ~ Row2]
  ) extends SelectBuilderSql[Fields1 ~ Fields2, Row1 ~ Row2] {
    override lazy val structure: Structure[(Fields1, Fields2), Row1 ~ Row2] =
      left.structure.join(right.structure)

    override def withPath(path: Path): SelectBuilderSql[Fields1 ~ Fields2, Row1 ~ Row2] =
      copy(left = left.withPath(path), right = right.withPath(path))

    override def withParams(sqlParams: SelectParams[Fields1 ~ Fields2, Row1 ~ Row2]): SelectBuilder[Fields1 ~ Fields2, Row1 ~ Row2] =
      copy(params = sqlParams)

    override def instantiate(ctx: RenderCtx): Instantiated[Fields1 ~ Fields2, Row1 ~ Row2] = {
      val alias = ctx.alias(structure._path)
      val leftInstance = left.instantiate(ctx)
      val rightInstance = right.instantiate(ctx)
      val newStructure = leftInstance.structure.join(rightInstance.structure)
      val ctes = leftInstance.asCTEs ++ rightInstance.asCTEs
      val renderedCtes = ctes.filterNot(_.isJoin).map(cte => SqlFragment(cte.name)).mkFragment(SqlFragment(", "))
      val sql =
        sql"""select $renderedCtes
  from ${SqlFragment(leftInstance.alias)}
  join ${SqlFragment(rightInstance.alias)}
  on ${pred(newStructure.fields).render(ctx)}
  ${SelectParams.render(newStructure.fields, ctx, params).getOrElse[SqlFragment](SqlFragment.empty)}"""

      SelectBuilderSql.Instantiated(
        alias = alias,
        isJoin = true,
        columns = leftInstance.columns ++ rightInstance.columns,
        sqlFrag = sql,
        upstreamCTEs = ctes,
        structure = newStructure,
        read = JdbcDecoder.tuple2Decoder(leftInstance.read, rightInstance.read)
      )
    }
  }

  final case class TableLeftJoin[Fields1, Fields2, Row1, Row2](
      left: SelectBuilderSql[Fields1, Row1],
      right: SelectBuilderSql[Fields2, Row2],
      pred: Fields1 ~ Fields2 => SqlExpr[Boolean],
      params: SelectParams[Fields1 ~ Fields2, Row1 ~ Option[Row2]]
  ) extends SelectBuilderSql[Fields1 ~ Fields2, Row1 ~ Option[Row2]] {
    override lazy val structure: Structure[Fields1 ~ Fields2, Row1 ~ Option[Row2]] =
      left.structure.leftJoin(right.structure)

    override def withPath(path: Path): SelectBuilderSql[Fields1 ~ Fields2, Row1 ~ Option[Row2]] =
      copy(left = left.withPath(path), right = right.withPath(path))

    override def withParams(sqlParams: SelectParams[Fields1 ~ Fields2, Row1 ~ Option[Row2]]): SelectBuilder[Fields1 ~ Fields2, Row1 ~ Option[Row2]] =
      copy(params = sqlParams)

    override def instantiate(ctx: RenderCtx): Instantiated[Fields1 ~ Fields2, Row1 ~ Option[Row2]] = {
      val alias = ctx.alias(structure._path)
      val leftInstance = left.instantiate(ctx)
      val rightInstance = right.instantiate(ctx)

      val joinedStructure = leftInstance.structure.join(rightInstance.structure)
      val newStructure = leftInstance.structure.leftJoin(rightInstance.structure)
      val ctes = leftInstance.asCTEs ++ rightInstance.asCTEs
      val sql =
        sql"""select ${ctes.filterNot(_.isJoin).map(cte => SqlFragment(cte.name)).mkFragment(SqlFragment(", "))}
  from ${SqlFragment(leftInstance.alias)}
  left join ${SqlFragment(rightInstance.alias)}
  on ${pred(joinedStructure.fields).render(ctx)}
  ${SelectParams.render(newStructure.fields, ctx, params).getOrElse[SqlFragment](SqlFragment.empty)}"""

      SelectBuilderSql.Instantiated(
        alias = alias,
        isJoin = true,
        columns = leftInstance.columns ++ rightInstance.columns,
        sqlFrag = sql,
        upstreamCTEs = ctes,
        structure = newStructure,
        read = JdbcDecoder.tuple2Decoder(leftInstance.read, JdbcDecoder.optionDecoder(rightInstance.read))
      )
    }
  }

  /** Need this intermediate data structure to generate aliases for tables (and prefixes for column selections) when we have a tree of joined tables. Need to start from the root after the user has
    * constructed the tree
    */
  final case class Instantiated[Fields, Row](
      alias: String,
      isJoin: Boolean,
      columns: List[(String, SqlExpr.FieldLike[?, ?])],
      sqlFrag: SqlFragment,
      upstreamCTEs: List[CTE],
      structure: Structure[Fields, Row],
      read: JdbcDecoder[Row]
  ) {
    def asCTEs: List[CTE] = upstreamCTEs :+ CTE(alias, sqlFrag, isJoin)
  }
  case class CTE(name: String, sql: SqlFragment, isJoin: Boolean)
}
