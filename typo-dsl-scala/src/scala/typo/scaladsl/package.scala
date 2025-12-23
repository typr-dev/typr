package typo

package object scaladsl {
  // Type aliases for typo.dsl types
  type Dialect = typo.dsl.Dialect
  type RenderCtx = typo.dsl.RenderCtx
  type GroupedBuilder[Fields, Rows] = typo.dsl.GroupedBuilder[Fields, Rows]
  type SortOrder[T] = typo.dsl.SortOrder[T]
  type Path = typo.dsl.Path
  type FieldsExpr[Row] = typo.dsl.FieldsExpr[Row]
  type Bijection[Wrapper, Underlying] = typo.dsl.Bijection[Wrapper, Underlying]

  // SqlExpr type alias (the object is defined separately in SqlExpr.scala)
  type SqlExpr[T] = typo.dsl.SqlExpr[T]

  // Type aliases for typo.runtime types
  type PgType[A] = typo.runtime.PgType[A]
  type PgTypes = typo.runtime.PgTypes

  // Type aliases for mock builders
  type DeleteBuilderMock[Id, Fields, Row] = typo.dsl.DeleteBuilderMock[Id, Fields, Row]
  type SelectBuilderMock[Fields, Row] = typo.dsl.SelectBuilderMock[Fields, Row]
  type UpdateBuilderMock[Fields, Row] = typo.dsl.UpdateBuilderMock[Fields, Row]

  // Type aliases for params types
  type DeleteParams[Fields] = typo.dsl.DeleteParams[Fields]
  type SelectParams[Fields, Row] = typo.dsl.SelectParams[Fields, Row]
  type UpdateParams[Fields, Row] = typo.dsl.UpdateParams[Fields, Row]

  // Companion objects for params types with factory methods
  object DeleteParams {
    def empty[Fields](): typo.dsl.DeleteParams[Fields] = typo.dsl.DeleteParams.empty[Fields]()
  }

  object SelectParams {
    def empty[Fields, Row](): typo.dsl.SelectParams[Fields, Row] = typo.dsl.SelectParams.empty[Fields, Row]()
  }

  object UpdateParams {
    def empty[Fields, Row](): typo.dsl.UpdateParams[Fields, Row] = typo.dsl.UpdateParams.empty[Fields, Row]()
  }

  // Companion objects for mock builders with factory methods
  object DeleteBuilderMock {
    def apply[Id, Fields, Row](
        structure: typo.dsl.RelationStructure[Fields, Row],
        allRowsSupplier: () => List[Row],
        params: DeleteParams[Fields],
        idExtractor: Row => Id,
        deleteById: Id => Unit
    ): DeleteBuilder[Fields, Row] =
      DslExports.DeleteBuilderMock(structure, allRowsSupplier, params, idExtractor, deleteById)
  }

  object SelectBuilderMock {
    def apply[Fields, Row](
        structure: typo.dsl.RelationStructure[Fields, Row],
        allRowsSupplier: () => List[Row],
        params: SelectParams[Fields, Row]
    ): SelectBuilder[Fields, Row] =
      DslExports.SelectBuilderMock(structure, allRowsSupplier, params)
  }

  object UpdateBuilderMock {
    def apply[Fields, Row](
        structure: typo.dsl.RelationStructure[Fields, Row],
        allRowsSupplier: () => List[Row],
        params: UpdateParams[Fields, Row],
        copyRow: Row => Row
    ): UpdateBuilder[Fields, Row] =
      DslExports.UpdateBuilderMock(structure, allRowsSupplier, params, copyRow)
  }

  // Dialect object exposing the Java static fields
  object Dialect {
    val POSTGRESQL: typo.dsl.Dialect = typo.dsl.Dialect.POSTGRESQL
    val MARIADB: typo.dsl.Dialect = typo.dsl.Dialect.MARIADB
    val DUCKDB: typo.dsl.Dialect = typo.dsl.Dialect.DUCKDB
    val ORACLE: typo.dsl.Dialect = typo.dsl.Dialect.ORACLE
    val SQLSERVER: typo.dsl.Dialect = typo.dsl.Dialect.SQLSERVER
  }

  // Bijection companion object with factory methods
  object Bijection {
    def apply[T, TT](unwrap: T => TT)(wrap: TT => T): typo.dsl.Bijection[T, TT] =
      typo.dsl.Bijection.of[T, TT](t => unwrap(t), tt => wrap(tt))

    def of[T, TT](unwrap: T => TT, wrap: TT => T): typo.dsl.Bijection[T, TT] =
      typo.dsl.Bijection.of[T, TT](t => unwrap(t), tt => wrap(tt))

    def identity[T](): typo.dsl.Bijection[T, T] =
      typo.dsl.Bijection.identity[T]()

    def asString: typo.dsl.Bijection[String, String] =
      typo.dsl.Bijection.asString()

    def asBool: typo.dsl.Bijection[java.lang.Boolean, java.lang.Boolean] =
      typo.dsl.Bijection.asBool()
  }
}
