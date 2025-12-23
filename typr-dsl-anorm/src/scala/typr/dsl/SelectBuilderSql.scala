package typr.dsl

import anorm.{AnormException, RowParser, SQL, SimpleSql, SqlParser}
import typr.dsl.Fragment.FragmentStringInterpolator

import java.sql.Connection
import java.util.concurrent.atomic.AtomicInteger

sealed trait SelectBuilderSql[Fields, Row] extends SelectBuilder[Fields, Row] {
  def withPath(path: Path): SelectBuilderSql[Fields, Row]
  def instantiate(ctx: RenderCtx, counter: AtomicInteger): SelectBuilderSql.Instantiated[Fields, Row]

  override lazy val renderCtx: RenderCtx = RenderCtx.from(this)

  lazy val sqlAndRowParser: (Fragment, RowParser[Row]) = {
    val instance = this.instantiate(renderCtx, new AtomicInteger(0))

    val cols: List[String] =
      instance.columns.map { case (alias, col) =>
        col.sqlReadCast.foldLeft(s"($alias).\"${col.name}\"") { case (acc, cast) => s"$acc::$cast" }
      }

    val ctes = instance.asCTEs
    val formattedCTEs = ctes.map { cte =>
      frag"""|${Fragment(cte.name)} as (
             |  ${cte.sql}
             |)""".stripMargin
    }

    val frag =
      frag"""|with 
             |${formattedCTEs.mkFragment(",\n")}
             |select ${Fragment(cols.mkString(","))} from ${Fragment(ctes.last.name)}""".stripMargin

    (frag, instance.rowParser(1))
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

  final override def toList(implicit c: Connection): List[Row] = {
    val (frag, rowParser) = sqlAndRowParser
    SimpleSql(SQL(frag.sql), frag.params.map(_.tupled).toMap, RowParser.successful).as(rowParser.*)(c)
  }

  final override def count(implicit c: Connection): Int = {
    val (frag, _) = sqlAndRowParser
    SimpleSql(SQL(s"select count(*) from (${frag.sql}) rows"), frag.params.map(_.tupled).toMap, RowParser.successful).as(SqlParser.int(1).single)(c)
  }
}

object SelectBuilderSql {
  final case class Relation[Fields, Row](
      name: String,
      structure: RelationStructure[Fields, Row],
      rowParser: Int => RowParser[Row],
      params: SelectParams[Fields, Row]
  ) extends SelectBuilderSql[Fields, Row] {
    override def withPath(path: Path): SelectBuilderSql[Fields, Row] =
      copy(structure = structure.withPath(path))

    override def withParams(sqlParams: SelectParams[Fields, Row]): SelectBuilder[Fields, Row] =
      copy(params = sqlParams)

    override def instantiate(ctx: RenderCtx, counter: AtomicInteger): SelectBuilderSql.Instantiated[Fields, Row] = {
      val alias = ctx.alias(structure._path)
      val sql = frag"(select ${Fragment(alias)} from ${Fragment(name)} ${Fragment(alias)} ${SelectParams.render(structure.fields, ctx, counter, params).getOrElse(Fragment.empty)})"

      SelectBuilderSql.Instantiated(
        alias = alias,
        isJoin = false,
        columns = structure.columns.map(c => (alias, c)),
        sqlFrag = sql,
        upstreamCTEs = Nil,
        structure = structure,
        rowParser = rowParser
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

    override def instantiate(ctx: RenderCtx, counter: AtomicInteger): Instantiated[Fields1 ~ Fields2, Row1 ~ Row2] = {
      val alias = ctx.alias(structure._path)
      val leftInstance = left.instantiate(ctx, counter)
      val rightInstance = right.instantiate(ctx, counter)
      val newStructure = leftInstance.structure.join(rightInstance.structure)
      val ctes = leftInstance.asCTEs ++ rightInstance.asCTEs
      val sql =
        frag"""|select ${ctes.filterNot(_.isJoin).map(cte => Fragment(cte.name)).mkFragment(", ")}
               |  from ${Fragment(leftInstance.alias)}
               |  join ${Fragment(rightInstance.alias)}
               |  on ${pred(newStructure.fields).render(ctx, counter)}
               |  ${SelectParams.render(newStructure.fields, ctx, counter, params).getOrElse(Fragment.empty)}""".stripMargin
      SelectBuilderSql.Instantiated(
        alias = alias,
        isJoin = true,
        columns = leftInstance.columns ++ rightInstance.columns,
        sqlFrag = sql,
        upstreamCTEs = ctes,
        structure = newStructure,
        rowParser = (i: Int) =>
          for {
            r1 <- leftInstance.rowParser(i)
            r2 <- rightInstance.rowParser(i + leftInstance.columns.size)
          } yield (r1, r2)
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

    override def instantiate(ctx: RenderCtx, counter: AtomicInteger): Instantiated[Fields1 ~ Fields2, Row1 ~ Option[Row2]] = {
      val alias = ctx.alias(structure._path)
      val leftInstance = left.instantiate(ctx, counter)
      val rightInstance = right.instantiate(ctx, counter)

      val joinedStructure = leftInstance.structure.join(rightInstance.structure)
      val newStructure = leftInstance.structure.leftJoin(rightInstance.structure)
      val ctes = leftInstance.asCTEs ++ rightInstance.asCTEs
      val sql =
        frag"""|select ${ctes.filterNot(_.isJoin).map(cte => Fragment(cte.name)).mkFragment(", ")}
               |  from ${Fragment(leftInstance.alias)}
               |  left join ${Fragment(rightInstance.alias)}
               |  on ${pred(joinedStructure.fields).render(ctx, counter)}
               |  ${SelectParams.render(newStructure.fields, ctx, counter, params).getOrElse(Fragment.empty)}""".stripMargin

      SelectBuilderSql.Instantiated(
        alias = alias,
        isJoin = true,
        columns = leftInstance.columns ++ rightInstance.columns,
        sqlFrag = sql,
        upstreamCTEs = ctes,
        structure = newStructure,
        rowParser = (i: Int) =>
          for {
            r1 <- leftInstance.rowParser(i)
            /** note, `RowParser` has a `?` combinator, but it doesn't work. fails with exception instead of [[anorm.Error]] */
            r2 <- RowParser[Option[Row2]] { row =>
              try rightInstance.rowParser(i + leftInstance.columns.size)(row).map(Some.apply)
              catch {
                case x: AnormException if x.message.contains("not found, available columns") => anorm.Success(None)
              }
            }
          } yield (r1, r2)
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
      rowParser: Int => RowParser[Row]
  ) {
    def asCTEs: List[CTE] = upstreamCTEs :+ CTE(alias, sqlFrag, isJoin)
  }
  case class CTE(name: String, sql: Fragment, isJoin: Boolean)
}
