package typr.dsl

import cats.syntax.apply.*
import cats.syntax.foldable.*
import doobie.free.connection.ConnectionIO
import doobie.implicits.toSqlInterpolator
import doobie.util.Read
import doobie.util.fragment.Fragment
import typr.dsl.internal.mkFragment.*

sealed trait SelectBuilderSql[Fields, Row] extends SelectBuilder[Fields, Row] {
  def withPath(path: Path): SelectBuilderSql[Fields, Row]
  def instantiate(ctx: RenderCtx): SelectBuilderSql.Instantiated[Fields, Row]

  override lazy val renderCtx: RenderCtx = RenderCtx.from(this)

  lazy val sqlAndRowParser: (Fragment, Read[Row]) = {
    val instance = this.instantiate(renderCtx)
    val cols = instance.columns.map { case (alias, col) =>
      col.sqlReadCast.foldLeft(s"($alias).\"${col.name}\"") { case (acc, cast) => s"$acc::$cast" }
    }

    val ctes = instance.asCTEs
    val formattedCTEs = ctes.map { cte =>
      fr"""|${Fragment.const0(cte.name)} as (
           |  ${cte.sql}
           |)""".stripMargin
    }

    val frag =
      fr"""|with 
             |${formattedCTEs.mkFragment(Fragment.const0(",\n"))}
             |select ${Fragment.const0(cols.mkString(","))} from ${Fragment.const0(ctes.last.name)}""".stripMargin

    (frag, instance.read)
  }

  override lazy val sql: Option[Fragment] = Some(sqlAndRowParser._1)

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

  final override def toList: ConnectionIO[List[Row]] = {
    val (frag, read) = sqlAndRowParser
    frag.query(using read).to[List]
  }
  final override def count: ConnectionIO[Int] = {
    val (frag, _) = sqlAndRowParser
    fr"select count(*) from ($frag) rows".query[Int].unique
  }
}

object SelectBuilderSql {
  final case class Relation[Fields, Row](
      name: String,
      structure: RelationStructure[Fields, Row],
      read: Read[Row],
      params: SelectParams[Fields, Row]
  ) extends SelectBuilderSql[Fields, Row] {
    override def withPath(path: Path): SelectBuilderSql[Fields, Row] =
      copy(structure = structure.withPath(path))

    override def withParams(sqlParams: SelectParams[Fields, Row]): SelectBuilder[Fields, Row] =
      copy(params = sqlParams)

    override def instantiate(ctx: RenderCtx): SelectBuilderSql.Instantiated[Fields, Row] = {
      val alias = ctx.alias(structure._path)
      val sql = fr"(select ${Fragment.const0(alias)} from ${Fragment.const0(name)} ${Fragment.const0(alias)} ${SelectParams.render(structure.fields, ctx, params).getOrElse(Fragment.empty)})"

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
      val sql =
        fr"""|select ${ctes.filterNot(_.isJoin).map(cte => Fragment.const0(cte.name)).mkFragment(Fragment.const0(", "))}
               |  from ${Fragment.const0(leftInstance.alias)}
               |  join ${Fragment.const0(rightInstance.alias)}
               |  on ${pred(newStructure.fields).render(ctx)}
               |  ${SelectParams.render(newStructure.fields, ctx, params).getOrElse(Fragment.empty)}""".stripMargin
      SelectBuilderSql.Instantiated(
        alias = alias,
        isJoin = true,
        columns = leftInstance.columns ++ rightInstance.columns,
        sqlFrag = sql,
        upstreamCTEs = ctes,
        structure = newStructure,
        read = (leftInstance.read, rightInstance.read).tupled
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
        fr"""|select ${ctes.filterNot(_.isJoin).map(cte => Fragment.const0(cte.name)).mkFragment(Fragment.const0(", "))}
               |  from ${Fragment.const0(leftInstance.alias)}
               |  left join ${Fragment.const0(rightInstance.alias)}
               |  on ${pred(joinedStructure.fields).render(ctx)}
               |  ${SelectParams.render(newStructure.fields, ctx, params).getOrElse(Fragment.empty)}""".stripMargin

      SelectBuilderSql.Instantiated(
        alias = alias,
        isJoin = true,
        columns = leftInstance.columns ++ rightInstance.columns,
        sqlFrag = sql,
        upstreamCTEs = ctes,
        structure = newStructure,
        read = (leftInstance.read, rightInstance.read.toOpt).tupled
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
      sqlFrag: Fragment,
      upstreamCTEs: List[CTE],
      structure: Structure[Fields, Row],
      read: Read[Row]
  ) {
    def asCTEs: List[CTE] = upstreamCTEs :+ CTE(alias, sqlFrag, isJoin)
  }
  case class CTE(name: String, sql: Fragment, isJoin: Boolean)
}
