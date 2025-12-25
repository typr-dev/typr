package dev.typr.foundations

package object scala {
  // Type aliases for typr.dsl types
  type Dialect = dev.typr.foundations.dsl.Dialect
  type RenderCtx = dev.typr.foundations.dsl.RenderCtx
  type GroupedBuilder[Fields, Rows] = dev.typr.foundations.dsl.GroupedBuilder[Fields, Rows]
  type SortOrder[T] = dev.typr.foundations.dsl.SortOrder[T]
  type Path = dev.typr.foundations.dsl.Path
  type FieldsExpr[Row] = dev.typr.foundations.dsl.FieldsExpr[Row]
  type Bijection[Wrapper, Underlying] = dev.typr.foundations.dsl.Bijection[Wrapper, Underlying]

  // SqlExpr type alias (the object is defined separately in SqlExpr.scala)
  type SqlExpr[T] = dev.typr.foundations.dsl.SqlExpr[T]

  // Type aliases for dev.typr.foundations types
  type PgType[A] = dev.typr.foundations.PgType[A]
  type PgTypes = dev.typr.foundations.PgTypes

  // Type aliases for mock builders
  type DeleteBuilderMock[Id, Fields, Row] = dev.typr.foundations.dsl.DeleteBuilderMock[Id, Fields, Row]
  type SelectBuilderMock[Fields, Row] = dev.typr.foundations.dsl.SelectBuilderMock[Fields, Row]
  type UpdateBuilderMock[Fields, Row] = dev.typr.foundations.dsl.UpdateBuilderMock[Fields, Row]

  // Type aliases for params types
  type DeleteParams[Fields] = dev.typr.foundations.dsl.DeleteParams[Fields]
  type SelectParams[Fields, Row] = dev.typr.foundations.dsl.SelectParams[Fields, Row]
  type UpdateParams[Fields, Row] = dev.typr.foundations.dsl.UpdateParams[Fields, Row]

  // Companion objects for params types with factory methods
  object DeleteParams {
    def empty[Fields](): dev.typr.foundations.dsl.DeleteParams[Fields] = dev.typr.foundations.dsl.DeleteParams.empty[Fields]()
  }

  object SelectParams {
    def empty[Fields, Row](): dev.typr.foundations.dsl.SelectParams[Fields, Row] = dev.typr.foundations.dsl.SelectParams.empty[Fields, Row]()
  }

  object UpdateParams {
    def empty[Fields, Row](): dev.typr.foundations.dsl.UpdateParams[Fields, Row] = dev.typr.foundations.dsl.UpdateParams.empty[Fields, Row]()
  }

  // Companion objects for mock builders with factory methods
  object DeleteBuilderMock {
    def apply[Id, Fields, Row](
        structure: dev.typr.foundations.dsl.RelationStructure[Fields, Row],
        allRowsSupplier: () => List[Row],
        params: DeleteParams[Fields],
        idExtractor: Row => Id,
        deleteById: Id => Unit
    ): DeleteBuilder[Fields, Row] =
      DslExports.DeleteBuilderMock(structure, allRowsSupplier, params, idExtractor, deleteById)
  }

  object SelectBuilderMock {
    def apply[Fields, Row](
        structure: dev.typr.foundations.dsl.RelationStructure[Fields, Row],
        allRowsSupplier: () => List[Row],
        params: SelectParams[Fields, Row]
    ): SelectBuilder[Fields, Row] =
      DslExports.SelectBuilderMock(structure, allRowsSupplier, params)
  }

  object UpdateBuilderMock {
    def apply[Fields, Row](
        structure: dev.typr.foundations.dsl.RelationStructure[Fields, Row],
        allRowsSupplier: () => List[Row],
        params: UpdateParams[Fields, Row],
        copyRow: Row => Row
    ): UpdateBuilder[Fields, Row] =
      DslExports.UpdateBuilderMock(structure, allRowsSupplier, params, copyRow)
  }

  // Dialect object exposing the Java static fields
  object Dialect {
    val POSTGRESQL: dev.typr.foundations.dsl.Dialect = dev.typr.foundations.dsl.Dialect.POSTGRESQL
    val MARIADB: dev.typr.foundations.dsl.Dialect = dev.typr.foundations.dsl.Dialect.MARIADB
    val DUCKDB: dev.typr.foundations.dsl.Dialect = dev.typr.foundations.dsl.Dialect.DUCKDB
    val ORACLE: dev.typr.foundations.dsl.Dialect = dev.typr.foundations.dsl.Dialect.ORACLE
    val SQLSERVER: dev.typr.foundations.dsl.Dialect = dev.typr.foundations.dsl.Dialect.SQLSERVER
  }

  // Bijection companion object with factory methods
  object Bijection {
    def apply[T, TT](unwrap: T => TT)(wrap: TT => T): dev.typr.foundations.dsl.Bijection[T, TT] =
      dev.typr.foundations.dsl.Bijection.of[T, TT](t => unwrap(t), tt => wrap(tt))

    def of[T, TT](unwrap: T => TT, wrap: TT => T): dev.typr.foundations.dsl.Bijection[T, TT] =
      dev.typr.foundations.dsl.Bijection.of[T, TT](t => unwrap(t), tt => wrap(tt))

    def identity[T](): dev.typr.foundations.dsl.Bijection[T, T] =
      dev.typr.foundations.dsl.Bijection.identity[T]()

    def asString: dev.typr.foundations.dsl.Bijection[String, String] =
      dev.typr.foundations.dsl.Bijection.asString()

    def asBool: dev.typr.foundations.dsl.Bijection[java.lang.Boolean, java.lang.Boolean] =
      dev.typr.foundations.dsl.Bijection.asBool()
  }
}
