package typo.kotlindsl

import typo.runtime.DbType
import java.util.Optional

/**
 * Kotlin wrapper for typo.dsl.SqlExpr that provides Kotlin-idiomatic API.
 * Wraps Java DSL types to convert Optional<T> to T? where appropriate.
 */
object SqlExpr {
    // Forward static methods from Java SqlExpr
    fun all(vararg exprs: typo.dsl.SqlExpr<Boolean>): typo.dsl.SqlExpr<Boolean> =
        typo.dsl.SqlExpr.all(*exprs)

    /**
     * Base interface for field-like expressions.
     * Note: This wraps the Java FieldLike - we don't extend it because it's sealed in Java
     * and Kotlin 2.2 prohibits extending sealed classes from other modules.
     */
    interface FieldLike<T, Row> {
        val underlying: typo.dsl.SqlExpr.FieldLike<T, Row>

        fun path(): List<typo.dsl.Path> = underlying._path()
        fun column(): String = underlying.column()
        fun get(row: Row): Optional<T> = underlying.get(row)
        fun set(row: Row, value: Optional<T>): typo.runtime.Either<String, Row> =
            underlying.set(row, value)
        fun sqlReadCast(): Optional<String> = underlying.sqlReadCast()
        fun sqlWriteCast(): Optional<String> = underlying.sqlWriteCast()
        fun pgType(): DbType<T> = underlying.pgType()
        fun render(ctx: typo.dsl.RenderCtx, counter: java.util.concurrent.atomic.AtomicInteger): typo.runtime.Fragment =
            underlying.render(ctx, counter)

        // Comparison operators
        fun isEqual(other: typo.dsl.SqlExpr<T>): typo.dsl.SqlExpr<Boolean> =
            underlying.isEqual(other)

        fun isEqual(value: T): typo.dsl.SqlExpr<Boolean> =
            underlying.isEqual(value)

        fun isNotEqual(other: typo.dsl.SqlExpr<T>): typo.dsl.SqlExpr<Boolean> =
            underlying.isNotEqual(other)

        fun isNotEqual(value: T): typo.dsl.SqlExpr<Boolean> =
            underlying.isNotEqual(value)

        fun greaterThan(other: typo.dsl.SqlExpr<T>): typo.dsl.SqlExpr<Boolean> =
            underlying.greaterThan(other)

        fun greaterThan(value: T): typo.dsl.SqlExpr<Boolean> =
            underlying.greaterThan(value)

        fun greaterThanOrEqual(other: typo.dsl.SqlExpr<T>): typo.dsl.SqlExpr<Boolean> =
            underlying.greaterThanOrEqual(other)

        fun greaterThanOrEqual(value: T): typo.dsl.SqlExpr<Boolean> =
            underlying.greaterThanOrEqual(value)

        fun lessThan(other: typo.dsl.SqlExpr<T>): typo.dsl.SqlExpr<Boolean> =
            underlying.lessThan(other)

        fun lessThan(value: T): typo.dsl.SqlExpr<Boolean> =
            underlying.lessThan(value)

        fun lessThanOrEqual(other: typo.dsl.SqlExpr<T>): typo.dsl.SqlExpr<Boolean> =
            underlying.lessThanOrEqual(other)

        fun lessThanOrEqual(value: T): typo.dsl.SqlExpr<Boolean> =
            underlying.lessThanOrEqual(value)

        // Logical operators
        fun or(other: typo.dsl.SqlExpr<T>, bijection: Bijection<T, Boolean>): typo.dsl.SqlExpr<T> =
            underlying.or(other, bijection)

        fun and(other: typo.dsl.SqlExpr<T>, bijection: Bijection<T, Boolean>): typo.dsl.SqlExpr<T> =
            underlying.and(other, bijection)

        fun not(bijection: Bijection<T, Boolean>): typo.dsl.SqlExpr<T> =
            underlying.not(bijection)

        // Arithmetic operators
        fun plus(other: typo.dsl.SqlExpr<T>): typo.dsl.SqlExpr<T> =
            underlying.plus(other)

        fun minus(other: typo.dsl.SqlExpr<T>): typo.dsl.SqlExpr<T> =
            underlying.minus(other)

        fun multiply(other: typo.dsl.SqlExpr<T>): typo.dsl.SqlExpr<T> =
            underlying.multiply(other)

        // String operations
        fun like(pattern: String, bijection: Bijection<T, String>): typo.dsl.SqlExpr<Boolean> =
            underlying.like(pattern, bijection)

        fun stringAppend(other: typo.dsl.SqlExpr<T>, bijection: Bijection<T, String>): typo.dsl.SqlExpr<T> =
            underlying.stringAppend(other, bijection)

        fun lower(bijection: Bijection<T, String>): typo.dsl.SqlExpr<T> =
            underlying.lower(bijection)

        fun upper(bijection: Bijection<T, String>): typo.dsl.SqlExpr<T> =
            underlying.upper(bijection)

        fun reverse(bijection: Bijection<T, String>): typo.dsl.SqlExpr<T> =
            underlying.reverse(bijection)

        fun strpos(substring: typo.dsl.SqlExpr<String>, bijection: Bijection<T, String>): typo.dsl.SqlExpr<Int> =
            underlying.strpos(substring, bijection)

        fun strLength(bijection: Bijection<T, String>): typo.dsl.SqlExpr<Int> =
            underlying.strLength(bijection)

        fun substring(from: typo.dsl.SqlExpr<Int>, count: typo.dsl.SqlExpr<Int>, bijection: Bijection<T, String>): typo.dsl.SqlExpr<T> =
            underlying.substring(from, count, bijection)

        // Null handling
        fun isNull(): typo.dsl.SqlExpr<Boolean> =
            underlying.isNull()

        fun coalesce(defaultValue: typo.dsl.SqlExpr<T>): typo.dsl.SqlExpr<T> =
            underlying.coalesce(defaultValue)

        // Type conversion
        fun <TT> underlying(bijection: Bijection<T, TT>): typo.dsl.SqlExpr<TT> =
            underlying.underlying(bijection)

        // Array operations
        fun `in`(values: Array<T>, pgType: typo.runtime.DbType<T>): typo.dsl.SqlExpr<Boolean> =
            underlying.`in`(values, pgType)

        // Custom operators
        fun <T2> customBinaryOp(op: String, right: typo.dsl.SqlExpr<T2>, eval: (T, T2) -> Boolean): typo.dsl.SqlExpr<Boolean> =
            underlying.customBinaryOp(op, right, java.util.function.BiFunction { a, b -> eval(a, b) })

        // Sorting
        fun asc(): typo.dsl.SortOrder<T> = underlying.asc()
        fun desc(): typo.dsl.SortOrder<T> = underlying.desc()
    }

    /**
     * Wrapper for non-nullable field.
     * The underlying Java Field has Function<R, T> so it returns T directly.
     */
    data class Field<T, Row>(
        override val underlying: typo.dsl.SqlExpr.Field<T, Row>
    ) : FieldLike<T, Row> {
        // Secondary constructor that builds the Java Field
        constructor(
            path: List<typo.dsl.Path>,
            column: String,
            get: (Row) -> T,
            sqlReadCast: String?,
            sqlWriteCast: String?,
            setter: (Row, T) -> Row,
            pgType: DbType<T>
        ) : this(
            typo.dsl.SqlExpr.Field(
                path,
                column,
                java.util.function.Function { row -> get(row) },
                Optional.ofNullable(sqlReadCast),
                Optional.ofNullable(sqlWriteCast),
                java.util.function.BiFunction { row, value -> setter(row, value) },
                pgType
            )
        )
    }

    /**
     * Wrapper for nullable field.
     * The underlying Java OptField has Function<R, Optional<T>>.
     * In Kotlin, we can expose this as T? instead of Optional<T>.
     */
    data class OptField<T, Row>(
        override val underlying: typo.dsl.SqlExpr.OptField<T, Row>
    ) : FieldLike<T, Row> {
        // Secondary constructor that builds the Java OptField
        constructor(
            path: List<typo.dsl.Path>,
            column: String,
            get: (Row) -> T?,
            sqlReadCast: String?,
            sqlWriteCast: String?,
            setter: (Row, T?) -> Row,
            pgType: DbType<T>
        ) : this(
            typo.dsl.SqlExpr.OptField<T, Row>(
                path,
                column,
                java.util.function.Function<Row, Optional<T>> { row ->
                    val value: T? = get(row)
                    Optional.ofNullable(value) as Optional<T>
                },
                Optional.ofNullable(sqlReadCast),
                Optional.ofNullable(sqlWriteCast),
                java.util.function.BiFunction<Row, Optional<T>, Row> { row, value -> setter(row, value.orElse(null)) },
                pgType
            )
        )

        // Kotlin-friendly nullable getter
        fun getOrNull(row: Row): T? = underlying.get(row).orElse(null)
    }

    /**
     * Wrapper for ID field (non-nullable).
     * The underlying Java IdField has Function<R, T> so it returns T directly.
     */
    data class IdField<T, Row>(
        override val underlying: typo.dsl.SqlExpr.IdField<T, Row>
    ) : FieldLike<T, Row> {
        // Secondary constructor that builds the Java IdField
        constructor(
            path: List<typo.dsl.Path>,
            column: String,
            get: (Row) -> T,
            sqlReadCast: String?,
            sqlWriteCast: String?,
            setter: (Row, T) -> Row,
            pgType: DbType<T>
        ) : this(
            typo.dsl.SqlExpr.IdField(
                path,
                column,
                java.util.function.Function { row -> get(row) },
                Optional.ofNullable(sqlReadCast),
                Optional.ofNullable(sqlWriteCast),
                java.util.function.BiFunction { row, value -> setter(row, value) },
                pgType
            )
        )
    }

    // CompositeIn with invoke operator for construction
    object CompositeIn {
        operator fun <Tuple, Row> invoke(
            parts: List<typo.dsl.SqlExpr.CompositeIn.Part<*, Tuple, Row>>,
            tuples: List<Tuple>
        ): typo.dsl.SqlExpr.CompositeIn<Tuple, Row> =
            typo.dsl.SqlExpr.CompositeIn(parts, tuples)

        /**
         * Factory function for creating Part instances that accepts Kotlin FieldLike types.
         */
        fun <Id, Tuple, Row> Part(
            field: FieldLike<Id, Row>,
            extract: (Tuple) -> Id,
            pgType: typo.runtime.DbType<Id>
        ): typo.dsl.SqlExpr.CompositeIn.Part<Id, Tuple, Row> =
            typo.dsl.SqlExpr.CompositeIn.Part(
                field.underlying,
                java.util.function.Function { tuple -> extract(tuple) },
                pgType
            )
    }
}

/**
 * Kotlin wrapper for ForeignKey that accepts Kotlin FieldLike wrappers.
 */
class ForeignKey<Fields, Row>(val underlying: typo.dsl.ForeignKey<Fields, Row>) {
    companion object {
        fun <Fields, Row> of(constraintName: String): ForeignKey<Fields, Row> =
            ForeignKey(typo.dsl.ForeignKey.of(constraintName))
    }

    fun <T> withColumnPair(
        thisField: SqlExpr.FieldLike<T, *>,
        otherGetter: (Fields) -> SqlExpr.FieldLike<T, Row>
    ): ForeignKey<Fields, Row> =
        ForeignKey(underlying.withColumnPair(thisField.underlying) { fields ->
            otherGetter(fields).underlying
        })
}
