package dev.typr.dslsc

import _root_.scala.jdk.CollectionConverters.*

object DslExports {

  // Type aliases for DSL types
  type Bijection[Wrapper, Underlying] = dev.typr.foundations.Bijection[Wrapper, Underlying]
  type SortOrder[T] = dev.typr.dsl.SortOrder[T]

  // Functional interfaces
  type SqlFunction2[T1, T2, R] = dev.typr.dsl.SqlFunction2[T1, T2, R]
  type SqlFunction3[T1, T2, T3, R] = dev.typr.dsl.SqlFunction3[T1, T2, T3, R]
  type TriFunction[T1, T2, T3, R] = dev.typr.dsl.TriFunction[T1, T2, T3, R]

  // Builder parameter types
  type DeleteParams[Fields] = dev.typr.dsl.DeleteParams[Fields]
  type SelectParams[Fields, Row] = dev.typr.dsl.SelectParams[Fields, Row]
  type UpdateParams[Fields, Row] = dev.typr.dsl.UpdateParams[Fields, Row]

  // Path type
  type Path = dev.typr.dsl.Path

  // Mock builder functions
  def SelectBuilderMock[Fields, Row](
      structure: dev.typr.dsl.RelationStructure[Fields, Row],
      allRowsSupplier: () => List[Row],
      params: SelectParams[Fields, Row]
  ): SelectBuilder[Fields, Row] = {
    SelectBuilder(
      dev.typr.dsl.SelectBuilderMock(
        structure,
        () => allRowsSupplier().asJava,
        params
      )
    )
  }

  def DeleteBuilderMock[Id, Fields, Row](
      structure: dev.typr.dsl.RelationStructure[Fields, Row],
      allRowsSupplier: () => List[Row],
      params: DeleteParams[Fields],
      idExtractor: Row => Id,
      deleteById: Id => Unit
  ): DeleteBuilder[Fields, Row] = {
    new DeleteBuilder(
      dev.typr.dsl.DeleteBuilderMock(
        structure,
        () => allRowsSupplier().asJava,
        params,
        (row: Row) => idExtractor(row),
        (id: Id) => deleteById(id)
      )
    )
  }

  def UpdateBuilderMock[Fields, Row](
      structure: dev.typr.dsl.RelationStructure[Fields, Row],
      allRowsSupplier: () => List[Row],
      params: UpdateParams[Fields, Row],
      copyRow: Row => Row
  ): UpdateBuilder[Fields, Row] = {
    new UpdateBuilder(
      dev.typr.dsl.UpdateBuilderMock(
        structure,
        () => allRowsSupplier().asJava,
        params,
        (row: Row) => copyRow(row)
      )
    )
  }

  // SortOrder extension methods
  implicit class SqlExprSortOrderOps[T](private val expr: dev.typr.dsl.SqlExpr[T]) extends AnyVal {
    def asc(): SortOrder[T] = dev.typr.dsl.SortOrder.asc(expr)
    def desc(): SortOrder[T] = dev.typr.dsl.SortOrder.desc(expr)
  }
}
