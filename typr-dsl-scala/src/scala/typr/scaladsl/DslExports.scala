package typr.scaladsl

import scala.jdk.CollectionConverters._

object DslExports {

  // Type aliases for DSL types
  type Bijection[Wrapper, Underlying] = typr.dsl.Bijection[Wrapper, Underlying]
  type SortOrder[T] = typr.dsl.SortOrder[T]

  // Functional interfaces
  type SqlFunction2[T1, T2, R] = typr.dsl.SqlFunction2[T1, T2, R]
  type SqlFunction3[T1, T2, T3, R] = typr.dsl.SqlFunction3[T1, T2, T3, R]
  type TriFunction[T1, T2, T3, R] = typr.dsl.TriFunction[T1, T2, T3, R]

  // Builder parameter types
  type DeleteParams[Fields] = typr.dsl.DeleteParams[Fields]
  type SelectParams[Fields, Row] = typr.dsl.SelectParams[Fields, Row]
  type UpdateParams[Fields, Row] = typr.dsl.UpdateParams[Fields, Row]

  // Path type
  type Path = typr.dsl.Path

  // Mock builder functions
  def SelectBuilderMock[Fields, Row](
      structure: typr.dsl.RelationStructure[Fields, Row],
      allRowsSupplier: () => List[Row],
      params: SelectParams[Fields, Row]
  ): SelectBuilder[Fields, Row] = {
    new SelectBuilder(
      typr.dsl.SelectBuilderMock(
        structure,
        () => allRowsSupplier().asJava,
        params
      )
    )
  }

  def DeleteBuilderMock[Id, Fields, Row](
      structure: typr.dsl.RelationStructure[Fields, Row],
      allRowsSupplier: () => List[Row],
      params: DeleteParams[Fields],
      idExtractor: Row => Id,
      deleteById: Id => Unit
  ): DeleteBuilder[Fields, Row] = {
    new DeleteBuilder(
      typr.dsl.DeleteBuilderMock(
        structure,
        () => allRowsSupplier().asJava,
        params,
        (row: Row) => idExtractor(row),
        (id: Id) => deleteById(id)
      )
    )
  }

  def UpdateBuilderMock[Fields, Row](
      structure: typr.dsl.RelationStructure[Fields, Row],
      allRowsSupplier: () => List[Row],
      params: UpdateParams[Fields, Row],
      copyRow: Row => Row
  ): UpdateBuilder[Fields, Row] = {
    new UpdateBuilder(
      typr.dsl.UpdateBuilderMock(
        structure,
        () => allRowsSupplier().asJava,
        params,
        (row: Row) => copyRow(row)
      )
    )
  }

  // SortOrder extension methods
  implicit class SqlExprSortOrderOps[T](private val expr: typr.dsl.SqlExpr[T]) extends AnyVal {
    def asc(): SortOrder[T] = typr.dsl.SortOrder.asc(expr)
    def desc(): SortOrder[T] = typr.dsl.SortOrder.desc(expr)
  }
}
