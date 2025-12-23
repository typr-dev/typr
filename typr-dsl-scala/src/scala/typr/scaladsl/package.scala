package typr

package object scaladsl {
  // Type aliases for typr.dsl types
  type Dialect = typr.dsl.Dialect
  type RenderCtx = typr.dsl.RenderCtx
  type GroupedBuilder[Fields, Rows] = typr.dsl.GroupedBuilder[Fields, Rows]
  type SortOrder[T] = typr.dsl.SortOrder[T]
  type Path = typr.dsl.Path
  type FieldsExpr[Row] = typr.dsl.FieldsExpr[Row]
  type Bijection[Wrapper, Underlying] = typr.dsl.Bijection[Wrapper, Underlying]

  // SqlExpr type alias (the object is defined separately in SqlExpr.scala)
  type SqlExpr[T] = typr.dsl.SqlExpr[T]

  // Type aliases for typr.runtime types
  type PgType[A] = typr.runtime.PgType[A]
  type PgTypes = typr.runtime.PgTypes

  // Type aliases for mock builders
  type DeleteBuilderMock[Id, Fields, Row] = typr.dsl.DeleteBuilderMock[Id, Fields, Row]
  type SelectBuilderMock[Fields, Row] = typr.dsl.SelectBuilderMock[Fields, Row]
  type UpdateBuilderMock[Fields, Row] = typr.dsl.UpdateBuilderMock[Fields, Row]

  // Type aliases for params types
  type DeleteParams[Fields] = typr.dsl.DeleteParams[Fields]
  type SelectParams[Fields, Row] = typr.dsl.SelectParams[Fields, Row]
  type UpdateParams[Fields, Row] = typr.dsl.UpdateParams[Fields, Row]

  // Companion objects for params types with factory methods
  object DeleteParams {
    def empty[Fields](): typr.dsl.DeleteParams[Fields] = typr.dsl.DeleteParams.empty[Fields]()
  }

  object SelectParams {
    def empty[Fields, Row](): typr.dsl.SelectParams[Fields, Row] = typr.dsl.SelectParams.empty[Fields, Row]()
  }

  object UpdateParams {
    def empty[Fields, Row](): typr.dsl.UpdateParams[Fields, Row] = typr.dsl.UpdateParams.empty[Fields, Row]()
  }

  // Companion objects for mock builders with factory methods
  object DeleteBuilderMock {
    def apply[Id, Fields, Row](
        structure: typr.dsl.RelationStructure[Fields, Row],
        allRowsSupplier: () => List[Row],
        params: DeleteParams[Fields],
        idExtractor: Row => Id,
        deleteById: Id => Unit
    ): DeleteBuilder[Fields, Row] =
      DslExports.DeleteBuilderMock(structure, allRowsSupplier, params, idExtractor, deleteById)
  }

  object SelectBuilderMock {
    def apply[Fields, Row](
        structure: typr.dsl.RelationStructure[Fields, Row],
        allRowsSupplier: () => List[Row],
        params: SelectParams[Fields, Row]
    ): SelectBuilder[Fields, Row] =
      DslExports.SelectBuilderMock(structure, allRowsSupplier, params)
  }

  object UpdateBuilderMock {
    def apply[Fields, Row](
        structure: typr.dsl.RelationStructure[Fields, Row],
        allRowsSupplier: () => List[Row],
        params: UpdateParams[Fields, Row],
        copyRow: Row => Row
    ): UpdateBuilder[Fields, Row] =
      DslExports.UpdateBuilderMock(structure, allRowsSupplier, params, copyRow)
  }

  // Dialect object exposing the Java static fields
  object Dialect {
    val POSTGRESQL: typr.dsl.Dialect = typr.dsl.Dialect.POSTGRESQL
    val MARIADB: typr.dsl.Dialect = typr.dsl.Dialect.MARIADB
    val DUCKDB: typr.dsl.Dialect = typr.dsl.Dialect.DUCKDB
    val ORACLE: typr.dsl.Dialect = typr.dsl.Dialect.ORACLE
    val SQLSERVER: typr.dsl.Dialect = typr.dsl.Dialect.SQLSERVER
  }

  // Bijection companion object with factory methods
  object Bijection {
    def apply[T, TT](unwrap: T => TT)(wrap: TT => T): typr.dsl.Bijection[T, TT] =
      typr.dsl.Bijection.of[T, TT](t => unwrap(t), tt => wrap(tt))

    def of[T, TT](unwrap: T => TT, wrap: TT => T): typr.dsl.Bijection[T, TT] =
      typr.dsl.Bijection.of[T, TT](t => unwrap(t), tt => wrap(tt))

    def identity[T](): typr.dsl.Bijection[T, T] =
      typr.dsl.Bijection.identity[T]()

    def asString: typr.dsl.Bijection[String, String] =
      typr.dsl.Bijection.asString()

    def asBool: typr.dsl.Bijection[java.lang.Boolean, java.lang.Boolean] =
      typr.dsl.Bijection.asBool()
  }
}
