package dev.typr.foundations.scala

import _root_.scala.jdk.CollectionConverters._

object DslExports {

  // Type aliases for DSL types
  type Bijection[Wrapper, Underlying] = dev.typr.foundations.dsl.Bijection[Wrapper, Underlying]
  type SortOrder[T] = dev.typr.foundations.dsl.SortOrder[T]

  // Functional interfaces
  type SqlFunction2[T1, T2, R] = dev.typr.foundations.dsl.SqlFunction2[T1, T2, R]
  type SqlFunction3[T1, T2, T3, R] = dev.typr.foundations.dsl.SqlFunction3[T1, T2, T3, R]
  type TriFunction[T1, T2, T3, R] = dev.typr.foundations.dsl.TriFunction[T1, T2, T3, R]

  // Builder parameter types
  type DeleteParams[Fields] = dev.typr.foundations.dsl.DeleteParams[Fields]
  type SelectParams[Fields, Row] = dev.typr.foundations.dsl.SelectParams[Fields, Row]
  type UpdateParams[Fields, Row] = dev.typr.foundations.dsl.UpdateParams[Fields, Row]

  // Path type
  type Path = dev.typr.foundations.dsl.Path

  // Mock builder functions
  def SelectBuilderMock[Fields, Row](
      structure: dev.typr.foundations.dsl.RelationStructure[Fields, Row],
      allRowsSupplier: () => List[Row],
      params: SelectParams[Fields, Row]
  ): SelectBuilder[Fields, Row] = {
    new SelectBuilder(
      dev.typr.foundations.dsl.SelectBuilderMock(
        structure,
        () => allRowsSupplier().asJava,
        params
      )
    )
  }

  def DeleteBuilderMock[Id, Fields, Row](
      structure: dev.typr.foundations.dsl.RelationStructure[Fields, Row],
      allRowsSupplier: () => List[Row],
      params: DeleteParams[Fields],
      idExtractor: Row => Id,
      deleteById: Id => Unit
  ): DeleteBuilder[Fields, Row] = {
    new DeleteBuilder(
      dev.typr.foundations.dsl.DeleteBuilderMock(
        structure,
        () => allRowsSupplier().asJava,
        params,
        (row: Row) => idExtractor(row),
        (id: Id) => deleteById(id)
      )
    )
  }

  def UpdateBuilderMock[Fields, Row](
      structure: dev.typr.foundations.dsl.RelationStructure[Fields, Row],
      allRowsSupplier: () => List[Row],
      params: UpdateParams[Fields, Row],
      copyRow: Row => Row
  ): UpdateBuilder[Fields, Row] = {
    new UpdateBuilder(
      dev.typr.foundations.dsl.UpdateBuilderMock(
        structure,
        () => allRowsSupplier().asJava,
        params,
        (row: Row) => copyRow(row)
      )
    )
  }

  // SortOrder extension methods
  implicit class SqlExprSortOrderOps[T](private val expr: dev.typr.foundations.dsl.SqlExpr[T]) extends AnyVal {
    def asc(): SortOrder[T] = dev.typr.foundations.dsl.SortOrder.asc(expr)
    def desc(): SortOrder[T] = dev.typr.foundations.dsl.SortOrder.desc(expr)
  }
}
