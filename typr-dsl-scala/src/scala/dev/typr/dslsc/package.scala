package dev.typr

package object dslsc {
  // Type aliases for typr.dsl types
  type Dialect = dev.typr.dsl.Dialect
  type RenderCtx = dev.typr.dsl.RenderCtx
  type GroupedBuilder[Fields, Rows] = dev.typr.dsl.GroupedBuilder[Fields, Rows]
  type SortOrder[T] = dev.typr.dsl.SortOrder[T]
  type Path = dev.typr.dsl.Path
  type FieldsBase[Row] = dev.typr.dsl.FieldsBase[Row]
  type FieldsExpr[Row] = dev.typr.dsl.FieldsExpr[Row]
  type Bijection[Wrapper, Underlying] = dev.typr.foundations.Bijection[Wrapper, Underlying]

  object Bijection {
    def of[T, TT](to: java.util.function.Function[T, TT], from: java.util.function.Function[TT, T]): dev.typr.foundations.Bijection[T, TT] =
      dev.typr.foundations.Bijection.of(to, from)
    def identity[T](): dev.typr.foundations.Bijection[T, T] =
      dev.typr.foundations.Bijection.identity[T]()
    val asBool: dev.typr.foundations.Bijection[java.lang.Boolean, java.lang.Boolean] =
      dev.typr.foundations.Bijection.asBool()
    val asString: dev.typr.foundations.Bijection[java.lang.String, java.lang.String] =
      dev.typr.foundations.Bijection.asString()
    def andToTuple[A, B](): dev.typr.foundations.Bijection[dev.typr.foundations.Tuple.Tuple2[A, B], (A, B)] =
      dev.typr.foundations.Bijection.of[dev.typr.foundations.Tuple.Tuple2[A, B], (A, B)](
        (t: dev.typr.foundations.Tuple.Tuple2[A, B]) => (t._1(), t._2()),
        (t: (A, B)) => dev.typr.foundations.Tuple.of(t._1, t._2)
      )
    def leftJoinToTuple[A, B](): dev.typr.foundations.Bijection[dev.typr.foundations.Tuple.Tuple2[A, java.util.Optional[B]], (A, Option[B])] =
      dev.typr.foundations.Bijection.of[dev.typr.foundations.Tuple.Tuple2[A, java.util.Optional[B]], (A, Option[B])](
        (t: dev.typr.foundations.Tuple.Tuple2[A, java.util.Optional[B]]) => (t._1(), _root_.scala.jdk.OptionConverters.RichOptional(t._2()).toScala),
        (t: (A, Option[B])) => dev.typr.foundations.Tuple.of(t._1, _root_.scala.jdk.OptionConverters.RichOption(t._2).toJava)
      )
    def rightJoinToTuple[A, B](): dev.typr.foundations.Bijection[dev.typr.foundations.Tuple.Tuple2[java.util.Optional[A], B], (Option[A], B)] =
      dev.typr.foundations.Bijection.of[dev.typr.foundations.Tuple.Tuple2[java.util.Optional[A], B], (Option[A], B)](
        (t: dev.typr.foundations.Tuple.Tuple2[java.util.Optional[A], B]) => (_root_.scala.jdk.OptionConverters.RichOptional(t._1()).toScala, t._2()),
        (t: (Option[A], B)) => dev.typr.foundations.Tuple.of(_root_.scala.jdk.OptionConverters.RichOption(t._1).toJava, t._2)
      )
    def fullJoinToTuple[A, B](): dev.typr.foundations.Bijection[dev.typr.foundations.Tuple.Tuple2[java.util.Optional[A], java.util.Optional[B]], (Option[A], Option[B])] =
      dev.typr.foundations.Bijection.of[dev.typr.foundations.Tuple.Tuple2[java.util.Optional[A], java.util.Optional[B]], (Option[A], Option[B])](
        (t: dev.typr.foundations.Tuple.Tuple2[java.util.Optional[A], java.util.Optional[B]]) =>
          (_root_.scala.jdk.OptionConverters.RichOptional(t._1()).toScala, _root_.scala.jdk.OptionConverters.RichOptional(t._2()).toScala),
        (t: (Option[A], Option[B])) => dev.typr.foundations.Tuple.of(_root_.scala.jdk.OptionConverters.RichOption(t._1).toJava, _root_.scala.jdk.OptionConverters.RichOption(t._2).toJava)
      )
    def optionalToOption[T](): dev.typr.foundations.Bijection[java.util.Optional[T], Option[T]] =
      dev.typr.foundationssc.Bijections.optionalToOption[T]
    def optionToOptional[T](): dev.typr.foundations.Bijection[Option[T], java.util.Optional[T]] =
      dev.typr.foundationssc.Bijections.optionToOptional[T]
  }

  // Type aliases for dev.typr.foundations types
  type PgType[A] = dev.typr.foundations.PgType[A]
  type PgTypes = dev.typr.foundations.PgTypes

  // Type aliases for mock builders
  type DeleteBuilderMock[Id, Fields, Row] = dev.typr.dsl.DeleteBuilderMock[Id, Fields, Row]
  type SelectBuilderMock[Fields, Row] = dev.typr.dsl.SelectBuilderMock[Fields, Row]
  type UpdateBuilderMock[Fields, Row] = dev.typr.dsl.UpdateBuilderMock[Fields, Row]

  // Type aliases for params types
  type DeleteParams[Fields] = dev.typr.dsl.DeleteParams[Fields]
  type SelectParams[Fields, Row] = dev.typr.dsl.SelectParams[Fields, Row]
  type UpdateParams[Fields, Row] = dev.typr.dsl.UpdateParams[Fields, Row]

  // Companion objects for params types with factory methods
  object DeleteParams {
    def empty[Fields](): dev.typr.dsl.DeleteParams[Fields] = dev.typr.dsl.DeleteParams.empty[Fields]()
  }

  object SelectParams {
    def empty[Fields, Row](): dev.typr.dsl.SelectParams[Fields, Row] = dev.typr.dsl.SelectParams.empty[Fields, Row]()
  }

  object UpdateParams {
    def empty[Fields, Row](): dev.typr.dsl.UpdateParams[Fields, Row] = dev.typr.dsl.UpdateParams.empty[Fields, Row]()
  }

  // Companion objects for mock builders with factory methods
  object DeleteBuilderMock {
    def apply[Id, Fields, Row](
        structure: dev.typr.dsl.RelationStructure[Fields, Row],
        allRowsSupplier: () => List[Row],
        params: DeleteParams[Fields],
        idExtractor: Row => Id,
        deleteById: Id => Unit
    ): DeleteBuilder[Fields, Row] =
      DslExports.DeleteBuilderMock(structure, allRowsSupplier, params, idExtractor, deleteById)
  }

  object SelectBuilderMock {
    def apply[Fields, Row](
        structure: dev.typr.dsl.RelationStructure[Fields, Row],
        allRowsSupplier: () => List[Row],
        params: SelectParams[Fields, Row]
    ): SelectBuilder[Fields, Row] =
      DslExports.SelectBuilderMock(structure, allRowsSupplier, params)
  }

  object UpdateBuilderMock {
    def apply[Fields, Row](
        structure: dev.typr.dsl.RelationStructure[Fields, Row],
        allRowsSupplier: () => List[Row],
        params: UpdateParams[Fields, Row],
        copyRow: Row => Row
    ): UpdateBuilder[Fields, Row] =
      DslExports.UpdateBuilderMock(structure, allRowsSupplier, params, copyRow)
  }

  // Dialect object exposing the Java static fields
  object Dialect {
    val POSTGRESQL: dev.typr.dsl.Dialect = dev.typr.dsl.Dialect.POSTGRESQL
    val MARIADB: dev.typr.dsl.Dialect = dev.typr.dsl.Dialect.MARIADB
    val DUCKDB: dev.typr.dsl.Dialect = dev.typr.dsl.Dialect.DUCKDB
    val ORACLE: dev.typr.dsl.Dialect = dev.typr.dsl.Dialect.ORACLE
    val SQLSERVER: dev.typr.dsl.Dialect = dev.typr.dsl.Dialect.SQLSERVER
    val DB2: dev.typr.dsl.Dialect = dev.typr.dsl.Dialect.DB2
    val SQLITE: dev.typr.dsl.Dialect = dev.typr.dsl.Dialect.SQLITE
  }
}
