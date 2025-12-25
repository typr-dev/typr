package dev.typr.foundations.kotlin

// ================================
// DSL Type Aliases
// ================================

// Core DSL types
typealias Bijection<Wrapper, Underlying> = dev.typr.foundations.dsl.Bijection<Wrapper, Underlying>
typealias Tuple2<A, B> = dev.typr.foundations.dsl.Tuple2<A, B>
// Note: SqlExpr and Structure are defined as objects below for nested type access
typealias SortOrder<T> = dev.typr.foundations.dsl.SortOrder<T>
typealias Dialect = dev.typr.foundations.dsl.Dialect
typealias FieldsExpr<Row> = dev.typr.foundations.dsl.FieldsExpr<Row>

// Functional interfaces (for SQL expressions with multiple parameters)
typealias SqlFunction2<T1, T2, R> = dev.typr.foundations.dsl.SqlFunction2<T1, T2, R>
typealias SqlFunction3<T1, T2, T3, R> = dev.typr.foundations.dsl.SqlFunction3<T1, T2, T3, R>
typealias TriFunction<T1, T2, T3, R> = dev.typr.foundations.dsl.TriFunction<T1, T2, T3, R>

// Builder parameter types (used by generated code)
typealias DeleteParams<Fields> = dev.typr.foundations.dsl.DeleteParams<Fields>
typealias SelectParams<Fields, Row> = dev.typr.foundations.dsl.SelectParams<Fields, Row>
typealias UpdateParams<Fields, Row> = dev.typr.foundations.dsl.UpdateParams<Fields, Row>

// Top-level mock constructor functions (Kotlin can't import companion object members)
// These forward to the companion object functions to maintain Java DSL structure

fun <Fields, Row> SelectBuilderMock(
    structure: dev.typr.foundations.dsl.RelationStructure<Fields, Row>,
    allRowsSupplier: () -> List<Row>,
    params: SelectParams<Fields, Row>
): SelectBuilder<Fields, Row> = SelectBuilder(
    dev.typr.foundations.dsl.SelectBuilderMock(
        structure,
        java.util.function.Supplier { allRowsSupplier() },
        params
    )
)

fun <Id, Fields, Row> DeleteBuilderMock(
    structure: dev.typr.foundations.dsl.RelationStructure<Fields, Row>,
    allRowsSupplier: () -> List<Row>,
    params: DeleteParams<Fields>,
    idExtractor: (Row) -> Id,
    deleteById: (Id) -> Unit
): DeleteBuilder<Fields, Row> = DeleteBuilder(
    dev.typr.foundations.dsl.DeleteBuilderMock(
        structure,
        java.util.function.Supplier { allRowsSupplier() },
        params,
        java.util.function.Function { row -> idExtractor(row) },
        java.util.function.Consumer { id -> deleteById(id) }
    )
)

fun <Fields, Row> UpdateBuilderMock(
    structure: dev.typr.foundations.dsl.RelationStructure<Fields, Row>,
    allRowsSupplier: () -> List<Row>,
    params: UpdateParams<Fields, Row>,
    copyRow: (Row) -> Row
): UpdateBuilder<Fields, Row> = UpdateBuilder(
    dev.typr.foundations.dsl.UpdateBuilderMock(
        structure,
        java.util.function.Supplier { allRowsSupplier() },
        params,
        java.util.function.Function { row -> copyRow(row) }
    )
)

// Path type
typealias Path = dev.typr.foundations.dsl.Path

// Note: ForeignKey wrapper class is defined in SqlExpr.kt
// Note: SqlExpr and Structure objects are defined in separate files
// SqlExpr.kt and Structure.kt for better organization

// ================================
// SortOrder Extensions
// ================================

/**
 * Create ascending sort order from SqlExpr.
 * Usage: field.asc()
 */
fun <T> dev.typr.foundations.dsl.SqlExpr<T>.asc(): SortOrder<T> = dev.typr.foundations.dsl.SortOrder.asc(this)

/**
 * Create descending sort order from SqlExpr.
 * Usage: field.desc()
 */
fun <T> dev.typr.foundations.dsl.SqlExpr<T>.desc(): SortOrder<T> = dev.typr.foundations.dsl.SortOrder.desc(this)
