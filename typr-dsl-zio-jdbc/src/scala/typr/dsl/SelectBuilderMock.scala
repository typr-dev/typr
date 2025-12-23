package typr.dsl

import typr.dsl.internal.mocks.RowOrdering
import zio.{Chunk, ZIO}
import zio.jdbc.*

final case class SelectBuilderMock[Fields, Row](
    structure: Structure[Fields, Row],
    all: ZIO[ZConnection, Throwable, Chunk[Row]],
    params: SelectParams[Fields, Row]
) extends SelectBuilder[Fields, Row] {
  def withPath(path: Path): SelectBuilderMock[Fields, Row] =
    copy(structure = structure.withPath(path))

  override val renderCtx: RenderCtx = RenderCtx.Empty

  override def withParams(sqlParams: SelectParams[Fields, Row]): SelectBuilder[Fields, Row] =
    copy(params = sqlParams)

  override def toChunk: ZIO[ZConnection, Throwable, Chunk[Row]] =
    all.map(all => SelectBuilderMock.applyParams(structure, all, params))

  override def count: ZIO[ZConnection, Throwable, Int] =
    all.map(all => SelectBuilderMock.applyParams(structure, all, params).length)

  override def joinOn[Fields2, Row2](other: SelectBuilder[Fields2, Row2])(pred: Fields ~ Fields2 => SqlExpr[Boolean]): SelectBuilderMock[Fields ~ Fields2, Row ~ Row2] = {
    val otherMock: SelectBuilderMock[Fields2, Row2] = other match {
      case x: SelectBuilderMock[Fields2, Row2] => x.withPath(Path.RightInJoin)
      case _                                   => sys.error("you cannot mix mock and sql repos")
    }

    val self = this.withPath(Path.LeftInJoin)
    val newStructure = self.structure.join(otherMock.structure)

    val newRows: ZIO[ZConnection, Throwable, Chunk[Row ~ Row2]] =
      for {
        lefts <- self.toChunk
        rights <- otherMock.toChunk
      } yield for {
        left <- lefts
        right <- rights
        newRow = (left, right)
        if newStructure.untypedEval(pred(newStructure.fields), newRow).getOrElse(false)
      } yield newRow

    SelectBuilderMock[Fields ~ Fields2, Row ~ Row2](newStructure, newRows, SelectParams.empty)
  }

  override def leftJoinOn[Fields2, Row2](
      other: SelectBuilder[Fields2, Row2]
  )(pred: Fields ~ Fields2 => SqlExpr[Boolean]): SelectBuilder[Fields ~ Fields2, Row ~ Option[Row2]] = {
    val otherMock: SelectBuilderMock[Fields2, Row2] = other match {
      case x: SelectBuilderMock[Fields2, Row2] => x.withPath(Path.RightInJoin)
      case _                                   => sys.error("you cannot mix mock and sql repos")
    }
    val self = this.withPath(Path.LeftInJoin)

    val newRows: ZIO[ZConnection, Throwable, Chunk[Row ~ Option[Row2]]] = {
      for {
        lefts <- self.toChunk
        rights <- otherMock.toChunk
      } yield {
        lefts.map { left =>
          val maybeRight = rights.find { right =>
            val newStructure = self.structure.join(otherMock.structure)
            newStructure.untypedEval(pred(newStructure.fields), (left, right)).getOrElse(false)
          }
          (left, maybeRight)
        }
      }
    }
    SelectBuilderMock[Fields ~ Fields2, Row ~ Option[Row2]](structure.leftJoin(otherMock.structure), newRows, SelectParams.empty)
  }

  override def sql: Option[SqlFragment] = None
}

object SelectBuilderMock {
  def applyParams[Fields, R](structure: Structure[Fields, R], rows: Chunk[R], params: SelectParams[Fields, R]): Chunk[R] = {
    val (filters, orderBys) = OrderByOrSeek.expand(structure.fields, params)
    implicit val rowOrdering: Ordering[R] = new RowOrdering(structure, orderBys)
    rows
      .filter(row => filters.forall(expr => structure.untypedEval(expr, row).getOrElse(false)))
      .sorted
      .drop(params.offset.getOrElse(0))
      .take(params.limit.getOrElse(Int.MaxValue))
  }
}
