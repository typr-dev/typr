package dev.typr.dslkt

import dev.typr.foundations.DbType
import dev.typr.foundations.PgTypes
import dev.typr.foundations.data.Json
import dev.typr.foundationskt.Bijection
import java.math.BigDecimal
import java.util.Optional

@Suppress("UNCHECKED_CAST")
private fun <T> T?.toOptional(): Optional<T> = Optional.ofNullable(this) as Optional<T>

/**
 * Kotlin ADT for SqlExpr that wraps the Java SqlExpr nodes.
 * Each node corresponds to a node in Java and has an `underlying` field.
 */
sealed interface SqlExpr<T> {
    val underlying: dev.typr.dsl.SqlExpr<T>

    // ==================== Comparison operators ====================
    fun isEqual(other: SqlExpr<T>): SqlExpr<Boolean> =
        wrapBool(underlying.isEqual(other.underlying))

    fun isNotEqual(other: SqlExpr<T>): SqlExpr<Boolean> =
        wrapBool(underlying.isNotEqual(other.underlying))

    fun greaterThan(other: SqlExpr<T>): SqlExpr<Boolean> =
        wrapBool(underlying.greaterThan(other.underlying))

    fun greaterThanOrEqual(other: SqlExpr<T>): SqlExpr<Boolean> =
        wrapBool(underlying.greaterThanOrEqual(other.underlying))

    fun lessThan(other: SqlExpr<T>): SqlExpr<Boolean> =
        wrapBool(underlying.lessThan(other.underlying))

    fun lessThanOrEqual(other: SqlExpr<T>): SqlExpr<Boolean> =
        wrapBool(underlying.lessThanOrEqual(other.underlying))

    // Logical operators
    fun or(other: SqlExpr<T>, bijection: dev.typr.foundations.Bijection<T, Boolean>): SqlExpr<T> =
        wrap(underlying.or(other.underlying, bijection))

    fun and(other: SqlExpr<T>, bijection: dev.typr.foundations.Bijection<T, Boolean>): SqlExpr<T> =
        wrap(underlying.and(other.underlying, bijection))

    fun not(bijection: dev.typr.foundations.Bijection<T, Boolean>): SqlExpr<T> =
        wrap(underlying.not(bijection))

    // Arithmetic operators
    fun plus(other: SqlExpr<T>): SqlExpr<T> =
        wrap(underlying.plus(other.underlying))

    fun minus(other: SqlExpr<T>): SqlExpr<T> =
        wrap(underlying.minus(other.underlying))

    fun multiply(other: SqlExpr<T>): SqlExpr<T> =
        wrap(underlying.multiply(other.underlying))

    // String operations
    fun like(pattern: String, bijection: Bijection<T, String>): SqlExpr<Boolean> =
        wrapBool(underlying.like(pattern, bijection.underlying))

    fun stringAppend(other: SqlExpr<T>, bijection: Bijection<T, String>): SqlExpr<T> =
        wrap(underlying.stringAppend(other.underlying, bijection.underlying))

    fun lower(bijection: Bijection<T, String>): SqlExpr<T> =
        wrap(underlying.lower(bijection.underlying))

    fun upper(bijection: Bijection<T, String>): SqlExpr<T> =
        wrap(underlying.upper(bijection.underlying))

    fun reverse(bijection: Bijection<T, String>): SqlExpr<T> =
        wrap(underlying.reverse(bijection.underlying))

    fun strpos(substring: SqlExpr<String>, bijection: Bijection<T, String>): SqlExpr<Int> =
        wrapInt(underlying.strpos(substring.underlying, bijection.underlying))

    fun strLength(bijection: Bijection<T, String>): SqlExpr<Int> =
        wrapInt(underlying.strLength(bijection.underlying))

    fun substring(
        from: SqlExpr<Int>,
        count: SqlExpr<Int>,
        bijection: Bijection<T, String>
    ): SqlExpr<T> =
        wrap(underlying.substring(unwrapInt(from), unwrapInt(count), bijection.underlying))

    // Null handling
    fun isNull(): SqlExpr<Boolean> =
        wrapBool(underlying.isNull())

    fun isNotNull(): SqlExpr<Boolean> =
        wrapBool(underlying.isNotNull())

    fun coalesce(defaultValue: SqlExpr<T>): SqlExpr<T> =
        wrap(underlying.coalesce(defaultValue.underlying))

    // Type conversion
    fun <TT> underlyingValue(bijection: dev.typr.foundations.Bijection<T, TT>): SqlExpr<TT> =
        wrap(underlying.underlying(bijection))

    // Range operations
    fun between(low: SqlExpr<T>, high: SqlExpr<T>): SqlExpr<Boolean> =
        wrapBool(underlying.between(low.underlying, high.underlying))

    fun notBetween(low: SqlExpr<T>, high: SqlExpr<T>): SqlExpr<Boolean> =
        wrapBool(underlying.notBetween(low.underlying, high.underlying))

    // Custom operators
    fun <T2> customBinaryOp(op: String, right: SqlExpr<T2>, eval: (T, T2) -> Boolean): SqlExpr<Boolean> =
        wrapBool(underlying.customBinaryOp(op, right.underlying, java.util.function.BiFunction { a, b -> eval(a, b) }))

    // Sorting
    fun asc(): dev.typr.dsl.SortOrder<T> = dev.typr.dsl.SortOrder.asc(underlying)
    fun desc(): dev.typr.dsl.SortOrder<T> = dev.typr.dsl.SortOrder.desc(underlying)

    // ==================== IN expressions ====================

    /** Check if this expression is IN a list of values */
    fun `in`(values: List<T>): SqlExpr<Boolean> {
        val rows = dev.typr.dsl.SqlExpr.Rows.of(underlying, values)
        return wrapBool(dev.typr.dsl.SqlExpr.In(underlying, rows))
    }

    /** Check if this expression is IN a vararg of values */
    fun `in`(vararg values: T): SqlExpr<Boolean> =
        wrapBool(underlying.`in`(*values))

    /** Check if this expression is IN a subquery result */
    fun <F> `in`(subquery: Subquery<F, T>): SqlExpr<Boolean> =
        wrapBool(dev.typr.dsl.SqlExpr.In(underlying, subquery.javaSubquery))

    fun notIn(vararg values: T): SqlExpr<Boolean> =
        SqlExpr.wrapBool(underlying.notIn(*values))

    /** Kotlin-friendly alias for `in` since 'in' is a reserved keyword */
    fun among(values: List<T>): SqlExpr<Boolean> = `in`(values)

    /** Kotlin-friendly alias for `in` since 'in' is a reserved keyword */
    fun among(vararg values: T): SqlExpr<Boolean> = `in`(*values)

    /** Kotlin-friendly alias for `in` with subquery */
    fun <F> among(subquery: Subquery<F, T>): SqlExpr<Boolean> = `in`(subquery)

    // ==================== Tuple creation with tupleWith() ====================

    /** Create a 1-element tuple expression (wraps this expression) */
    fun tupleWith(): TupleExpr1<T> = TupleExpr.of(this)

    /** Create a 2-element tuple expression */
    fun <T1> tupleWith(e1: SqlExpr<T1>): TupleExpr2<T, T1> = TupleExpr.of(this, e1)

    /** Create a 3-element tuple expression */
    fun <T1, T2> tupleWith(e1: SqlExpr<T1>, e2: SqlExpr<T2>): TupleExpr3<T, T1, T2> = TupleExpr.of(this, e1, e2)

    /** Create a 4-element tuple expression */
    fun <T1, T2, T3> tupleWith(e1: SqlExpr<T1>, e2: SqlExpr<T2>, e3: SqlExpr<T3>): TupleExpr4<T, T1, T2, T3> =
        TupleExpr.of(this, e1, e2, e3)

    /** Create a 5-element tuple expression */
    fun <T1, T2, T3, T4> tupleWith(e1: SqlExpr<T1>, e2: SqlExpr<T2>, e3: SqlExpr<T3>, e4: SqlExpr<T4>): TupleExpr5<T, T1, T2, T3, T4> =
        TupleExpr.of(this, e1, e2, e3, e4)

    /** Create a 6-element tuple expression */
    fun <T1, T2, T3, T4, T5> tupleWith(e1: SqlExpr<T1>, e2: SqlExpr<T2>, e3: SqlExpr<T3>, e4: SqlExpr<T4>, e5: SqlExpr<T5>): TupleExpr6<T, T1, T2, T3, T4, T5> =
        TupleExpr.of(this, e1, e2, e3, e4, e5)


    companion object {
        // ========== Bijections for primitive type conversion ==========

        @JvmField
        val JavaToKotlinBool: dev.typr.foundations.Bijection<Boolean, Boolean> =
            dev.typr.foundations.Bijection.identity()

        @JvmField
        val JavaToKotlinInt: dev.typr.foundations.Bijection<Int, Int> =
            dev.typr.foundations.Bijection.identity()

        @JvmField
        val JavaToKotlinLong: dev.typr.foundations.Bijection<Long, Long> =
            dev.typr.foundations.Bijection.identity()

        @JvmField
        val JavaToKotlinDouble: dev.typr.foundations.Bijection<Double, Double> =
            dev.typr.foundations.Bijection.identity()

        // ========== Wrapping/Unwrapping helpers ==========

        /** Wrap any Java SqlExpr */
        @JvmStatic
        fun <T> wrap(expr: dev.typr.dsl.SqlExpr<T>): SqlExpr<T> =
            Wrapped(expr)

        /** Wrap a Java SqlExpr<Boolean> to Kotlin SqlExpr<Boolean> */
        @JvmStatic
        fun wrapBool(expr: dev.typr.dsl.SqlExpr<Boolean>): SqlExpr<Boolean> =
            Wrapped(expr)

        /** Unwrap a Kotlin SqlExpr<Boolean> to Java SqlExpr<Boolean> */
        @JvmStatic
        fun unwrapBool(expr: SqlExpr<Boolean>): dev.typr.dsl.SqlExpr<Boolean> =
            expr.underlying

        /** Wrap a Java SqlExpr<Int> to Kotlin SqlExpr<Int> */
        @JvmStatic
        fun wrapInt(expr: dev.typr.dsl.SqlExpr<Int>): SqlExpr<Int> =
            Wrapped(expr)

        /** Unwrap a Kotlin SqlExpr<Int> to Java SqlExpr<Int> */
        @JvmStatic
        fun unwrapInt(expr: SqlExpr<Int>): dev.typr.dsl.SqlExpr<Int> =
            expr.underlying

        /** Wrap a Java SqlExpr<Long> to Kotlin SqlExpr<Long> */
        @JvmStatic
        fun wrapLong(expr: dev.typr.dsl.SqlExpr<Long>): SqlExpr<Long> =
            Wrapped(expr)

        /** Unwrap a Kotlin SqlExpr<Long> to Java SqlExpr<Long> */
        @JvmStatic
        fun unwrapLong(expr: SqlExpr<Long>): dev.typr.dsl.SqlExpr<Long> =
            expr.underlying

        /** Wrap a Java SqlExpr<Double> to Kotlin SqlExpr<Double> */
        @JvmStatic
        fun wrapDouble(expr: dev.typr.dsl.SqlExpr<Double>): SqlExpr<Double> =
            Wrapped(expr)

        /** Unwrap a Kotlin SqlExpr<Double> to Java SqlExpr<Double> */
        @JvmStatic
        fun unwrapDouble(expr: SqlExpr<Double>): dev.typr.dsl.SqlExpr<Double> =
            expr.underlying

        // ========== Static factory methods ==========

        /** Combine multiple boolean expressions with AND */
        @JvmStatic
        fun all(vararg exprs: SqlExpr<Boolean>): SqlExpr<Boolean> {
            val javaExprs = exprs.map { it.underlying }.toTypedArray()
            return wrapBool(dev.typr.dsl.SqlExpr.all(*javaExprs))
        }

        /** Combine multiple boolean expressions with OR */
        @JvmStatic
        fun any(vararg exprs: SqlExpr<Boolean>): SqlExpr<Boolean> {
            val javaExprs = exprs.map { it.underlying }.toTypedArray()
            return wrapBool(dev.typr.dsl.SqlExpr.any(*javaExprs))
        }

        /** Check if a subquery returns any rows */
        @JvmStatic
        fun <F, R> exists(subquery: dev.typr.dsl.SelectBuilder<F, R>): SqlExpr<Boolean> =
            wrapBool(dev.typr.dsl.SqlExpr.exists(subquery))

        /** Check if a subquery returns no rows */
        @JvmStatic
        fun <F, R> notExists(subquery: dev.typr.dsl.SelectBuilder<F, R>): SqlExpr<Boolean> =
            wrapBool(dev.typr.dsl.SqlExpr.notExists(subquery))

        // ========== Aggregate factory methods ==========

        /** COUNT(*) - count all rows */
        @JvmStatic
        fun count(): SqlExpr<Long> =
            wrapLong(dev.typr.dsl.SqlExpr.CountStar())

        /** COUNT(expr) - count non-null values */
        @JvmStatic
        fun <T> count(expr: SqlExpr<T>): SqlExpr<Long> =
            wrapLong(dev.typr.dsl.SqlExpr.Count(expr.underlying))

        /** COUNT(DISTINCT expr) - count distinct non-null values */
        @JvmStatic
        fun <T> countDistinct(expr: SqlExpr<T>): SqlExpr<Long> =
            wrapLong(dev.typr.dsl.SqlExpr.CountDistinct(expr.underlying))

        /** SUM(expr) for Int - returns Long */
        @JvmStatic
        fun sum(expr: SqlExpr<Int>): SqlExpr<Long> =
            wrapLong(dev.typr.dsl.SqlExpr.Sum(expr.underlying, PgTypes.int8))

        /** SUM(expr) for Long - returns Long */
        @JvmStatic
        fun sumLong(expr: SqlExpr<Long>): SqlExpr<Long> =
            wrapLong(dev.typr.dsl.SqlExpr.Sum(expr.underlying, PgTypes.int8))

        /** SUM(expr) for BigDecimal - returns BigDecimal */
        @JvmStatic
        fun sumBigDecimal(expr: SqlExpr<BigDecimal>): SqlExpr<BigDecimal> =
            wrap(dev.typr.dsl.SqlExpr.Sum(expr.underlying, PgTypes.numeric))

        /** SUM(expr) for Double - returns Double */
        @JvmStatic
        fun sumDouble(expr: SqlExpr<Double>): SqlExpr<Double> =
            wrapDouble(dev.typr.dsl.SqlExpr.Sum(expr.underlying, PgTypes.float8))

        /** AVG(expr) - average of numeric values, returns Double */
        @JvmStatic
        fun <T : Number> avg(expr: SqlExpr<T>): SqlExpr<Double> =
            wrapDouble(dev.typr.dsl.SqlExpr.Avg(expr.underlying))

        /** MIN(expr) - minimum value */
        @JvmStatic
        fun <T> min(expr: SqlExpr<T>): SqlExpr<T> =
            wrap(dev.typr.dsl.SqlExpr.min(expr.underlying))

        /** MAX(expr) - maximum value */
        @JvmStatic
        fun <T> max(expr: SqlExpr<T>): SqlExpr<T> =
            wrap(dev.typr.dsl.SqlExpr.max(expr.underlying))

        /** STRING_AGG(expr, delimiter) - concatenate strings */
        @JvmStatic
        fun stringAgg(expr: SqlExpr<String>, delimiter: String): SqlExpr<String> =
            wrap(dev.typr.dsl.SqlExpr.StringAgg(expr.underlying, delimiter))

        /** ARRAY_AGG(expr) - collect values into array */
        @JvmStatic
        fun <T> arrayAgg(expr: SqlExpr<T>, arrayType: DbType<List<T>>): SqlExpr<List<T>> =
            wrap(dev.typr.dsl.SqlExpr.ArrayAgg(expr.underlying, arrayType))

        /** JSON_AGG(expr) - collect values into JSON array */
        @JvmStatic
        fun <T> jsonAgg(expr: SqlExpr<T>): SqlExpr<Json> =
            wrap(dev.typr.dsl.SqlExpr.JsonAgg(expr.underlying, PgTypes.json))

        /** BOOL_AND(expr) - true if all values are true */
        @JvmStatic
        fun boolAnd(expr: SqlExpr<Boolean>): SqlExpr<Boolean> =
            wrapBool(dev.typr.dsl.SqlExpr.BoolAnd(expr.underlying))

        /** BOOL_OR(expr) - true if any value is true */
        @JvmStatic
        fun boolOr(expr: SqlExpr<Boolean>): SqlExpr<Boolean> =
            wrapBool(dev.typr.dsl.SqlExpr.BoolOr(expr.underlying))

        /** Create a constant expression */
        @JvmStatic
        fun <T> const(value: T, dbType: DbType<T>): SqlExpr<T> =
            ConstReq(dev.typr.dsl.SqlExpr.ConstReq(value, dbType))

        /** Create an optional constant expression */
        @JvmStatic
        fun <T> constOpt(value: T?, dbType: DbType<T>): SqlExpr<T> =
            ConstOpt(dev.typr.dsl.SqlExpr.ConstOpt(Optional.ofNullable(value), dbType))
    }

    // ========== Generic wrapper for any Java SqlExpr ==========

    data class Wrapped<T>(override val underlying: dev.typr.dsl.SqlExpr<T>) : SqlExpr<T>

    // ========== FieldLike and subclasses ==========

    sealed interface FieldLike<T, Row> : SqlExpr<T> {
        override val underlying: dev.typr.dsl.SqlExpr.FieldLike<T, Row>

        fun path(): List<dev.typr.dsl.Path> = underlying._path()
        fun column(): String = underlying.column()
        fun name(): String = underlying.name()

        fun get(row: Row): T? = underlying.get(row).orElse(null)

        fun set(row: Row, value: T?): dev.typr.foundations.Either<String, Row> =
            underlying.set(row, value.toOptional())

        fun sqlReadCast(): String? = underlying.sqlReadCast().orElse(null)
        fun sqlWriteCast(): String? = underlying.sqlWriteCast().orElse(null)
        fun dbType(): DbType<T> = underlying.dbType()

        // Value-accepting comparison operators
        fun isEqual(value: T): SqlExpr<Boolean> = SqlExpr.wrapBool(underlying.isEqual(value))
        fun isNotEqual(value: T): SqlExpr<Boolean> = SqlExpr.wrapBool(underlying.isNotEqual(value))
        fun greaterThan(value: T): SqlExpr<Boolean> = SqlExpr.wrapBool(underlying.greaterThan(value))
        fun greaterThanOrEqual(value: T): SqlExpr<Boolean> = SqlExpr.wrapBool(underlying.greaterThanOrEqual(value))
        fun lessThan(value: T): SqlExpr<Boolean> = SqlExpr.wrapBool(underlying.lessThan(value))
        fun lessThanOrEqual(value: T): SqlExpr<Boolean> = SqlExpr.wrapBool(underlying.lessThanOrEqual(value))

        // Value-accepting range operators
        fun between(low: T, high: T): SqlExpr<Boolean> = SqlExpr.wrapBool(underlying.between(low, high))
        fun notBetween(low: T, high: T): SqlExpr<Boolean> = SqlExpr.wrapBool(underlying.notBetween(low, high))

        // Value-accepting coalesce
        fun coalesce(defaultValue: T): SqlExpr<T> = SqlExpr.wrap(underlying.coalesce(defaultValue))

        // Value-accepting arithmetic
        fun plus(value: T): SqlExpr<T> = SqlExpr.wrap(underlying.plus(value))
        fun minus(value: T): SqlExpr<T> = SqlExpr.wrap(underlying.minus(value))
        fun multiply(value: T): SqlExpr<T> = SqlExpr.wrap(underlying.multiply(value))

        // String operations with value
        fun stringAppend(value: T, bijection: Bijection<T, String>): SqlExpr<T> =
            SqlExpr.wrap(underlying.stringAppend(value, bijection.underlying))

        companion object {
            /** Wrap a Java FieldLike into the appropriate Kotlin FieldLike subtype */
            @JvmStatic
            fun <T, Row> wrap(javaFieldLike: dev.typr.dsl.SqlExpr.FieldLike<T, Row>): FieldLike<T, Row> =
                when (javaFieldLike) {
                    is dev.typr.dsl.SqlExpr.Field<T, Row> -> Field(javaFieldLike)
                    is dev.typr.dsl.SqlExpr.OptField<T, Row> -> OptField(javaFieldLike)
                    is dev.typr.dsl.SqlExpr.IdField<T, Row> -> IdField(javaFieldLike)
                    else -> throw IllegalArgumentException("Unknown FieldLike type: ${javaFieldLike::class}")
                }
        }
    }

    data class Field<T, Row>(
        override val underlying: dev.typr.dsl.SqlExpr.Field<T, Row>
    ) : FieldLike<T, Row> {
        constructor(
            path: List<dev.typr.dsl.Path>,
            column: String,
            get: (Row) -> T,
            sqlReadCast: String?,
            sqlWriteCast: String?,
            setter: (Row, T) -> Row,
            dbType: DbType<T>
        ) : this(
            dev.typr.dsl.SqlExpr.Field(
                path,
                column,
                java.util.function.Function { row -> get(row) },
                Optional.ofNullable(sqlReadCast),
                Optional.ofNullable(sqlWriteCast),
                java.util.function.BiFunction { row, value -> setter(row, value) },
                dbType
            )
        )
    }

    data class OptField<T, Row>(
        override val underlying: dev.typr.dsl.SqlExpr.OptField<T, Row>
    ) : FieldLike<T, Row> {
        constructor(
            path: List<dev.typr.dsl.Path>,
            column: String,
            get: (Row) -> T?,
            sqlReadCast: String?,
            sqlWriteCast: String?,
            setter: (Row, T?) -> Row,
            dbType: DbType<T>
        ) : this(
            dev.typr.dsl.SqlExpr.OptField<T, Row>(
                path,
                column,
                java.util.function.Function<Row, Optional<T>> { row -> get(row).toOptional() },
                Optional.ofNullable(sqlReadCast),
                Optional.ofNullable(sqlWriteCast),
                java.util.function.BiFunction<Row, Optional<T>, Row> { row, value ->
                    setter(
                        row,
                        value.orElse(null)
                    )
                },
                dbType
            )
        )

        fun getOrNull(row: Row): T? = underlying.get(row).orElse(null)
    }

    data class IdField<T, Row>(
        override val underlying: dev.typr.dsl.SqlExpr.IdField<T, Row>
    ) : FieldLike<T, Row> {
        constructor(
            path: List<dev.typr.dsl.Path>,
            column: String,
            get: (Row) -> T,
            sqlReadCast: String?,
            sqlWriteCast: String?,
            setter: (Row, T) -> Row,
            dbType: DbType<T>
        ) : this(
            dev.typr.dsl.SqlExpr.IdField(
                path,
                column,
                java.util.function.Function { row -> get(row) },
                Optional.ofNullable(sqlReadCast),
                Optional.ofNullable(sqlWriteCast),
                java.util.function.BiFunction { row, value -> setter(row, value) },
                dbType
            )
        )
    }

    // ========== Const types ==========

    sealed interface Const<T> : SqlExpr<T> {
        override val underlying: dev.typr.dsl.SqlExpr.Const<T>
        fun dbType(): DbType<T> = underlying.dbType()
    }

    data class ConstReq<T>(override val underlying: dev.typr.dsl.SqlExpr.ConstReq<T>) : Const<T> {
        fun value(): T = underlying.value()

        companion object {
            @JvmStatic
            operator fun <T> invoke(value: T, dbType: DbType<T>): ConstReq<T> =
                ConstReq(dev.typr.dsl.SqlExpr.ConstReq(value, dbType))
        }
    }

    data class ConstOpt<T>(override val underlying: dev.typr.dsl.SqlExpr.ConstOpt<T>) : Const<T> {
        fun value(): T? = underlying.value().orElse(null)

        companion object {
            @JvmStatic
            operator fun <T> invoke(value: T?, dbType: DbType<T>): ConstOpt<T> =
                ConstOpt(dev.typr.dsl.SqlExpr.ConstOpt(Optional.ofNullable(value), dbType))
        }
    }

    /**
     * Subquery expression for use in IN clauses.
     * Wraps a Java Subquery and provides the underlying SqlExpr for the IN operator.
     */
    class Subquery<F, R>(
        val javaSubquery: dev.typr.dsl.SqlExpr.Subquery<F, R>
    ) : SqlExpr<List<R>> {
        override val underlying: dev.typr.dsl.SqlExpr<List<R>> = javaSubquery
    }
}

// ========== ForeignKey ==========

class ForeignKey<Fields, Row>(val underlying: dev.typr.dsl.ForeignKey<Fields, Row>) {
    companion object {
        @JvmStatic
        fun <Fields, Row> of(constraintName: String): ForeignKey<Fields, Row> =
            ForeignKey(dev.typr.dsl.ForeignKey.of(constraintName))
    }

    fun <T> withColumnPair(
        thisField: SqlExpr.FieldLike<T, *>,
        otherGetter: (Fields) -> SqlExpr.FieldLike<T, Row>
    ): ForeignKey<Fields, Row> =
        ForeignKey(underlying.withColumnPair(thisField.underlying) { fields ->
            otherGetter(fields).underlying
        })
}

