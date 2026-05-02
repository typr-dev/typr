package dev.typr.dslkt

import dev.typr.dsl.SelectBuilder as JavaSelectBuilder
import dev.typr.dsl.Structure
import dev.typr.dsl.SqlExpr as JavaSqlExpr
import dev.typr.dsl.SortOrder
import dev.typr.dsl.RenderCtx
import dev.typr.foundations.Tuple.Tuple2
import dev.typr.dsl.Dialect
import dev.typr.foundationskt.Fragment
import dev.typr.foundationskt.Connection
import java.util.Optional

/**
 * Kotlin facade for SQL SELECT queries with type-safe operations.
 * Delegates to the Java implementation while providing Kotlin-friendly APIs.
 */
class SelectBuilder<Fields, Row> internal constructor(
    private val javaBuilder: JavaSelectBuilder<Fields, Row>
) {

    fun renderCtx(): RenderCtx = javaBuilder.renderCtx()

    fun structure(): Structure<Fields, Row> = javaBuilder.structure()

    /**
     * Add a where clause to the query.
     * Consecutive calls to where will be combined with AND.
     * The lambda has SqlExprExtensions as receiver, making extensions like like(), and(), or() available.
     */
    fun where(predicate: SqlExprExtensions.(Fields) -> SqlExpr<Boolean>): SelectBuilder<Fields, Row> {
        return SelectBuilder(javaBuilder.where { fields -> SqlExprExtensionsInstance.predicate(fields).underlying })
    }

    /**
     * Conditionally add a where clause based on a nullable value.
     */
    fun <T> maybeWhere(value: T?, predicate: SqlExprExtensions.(Fields, T) -> SqlExpr<Boolean>): SelectBuilder<Fields, Row> {
        return SelectBuilder(javaBuilder.maybeWhere(Optional.ofNullable(value)) { fields, v -> SqlExprExtensionsInstance.predicate(fields, v).underlying })
    }

    /**
     * Add an order by clause to the query.
     * Consecutive calls to orderBy will be combined and order kept.
     */
    fun <T> orderBy(orderFunc: (Fields) -> SortOrder<T>): SelectBuilder<Fields, Row> {
        return SelectBuilder(javaBuilder.orderBy(orderFunc))
    }

    /**
     * Add a seek predicate for cursor-based pagination.
     */
    fun <T> seek(orderFunc: (Fields) -> SortOrder<T>, value: SqlExpr.Const<T>): SelectBuilder<Fields, Row> {
        return SelectBuilder(javaBuilder.seek(orderFunc, value.underlying))
    }

    /**
     * Conditionally add a seek predicate or just order by.
     * Convenience overload that automatically creates the Const from the field's DbType.
     */
    fun <T> maybeSeek(
        orderFunc: (Fields) -> SortOrder<T>,
        maybeValue: T?
    ): SelectBuilder<Fields, Row> {
        return SelectBuilder(javaBuilder.maybeSeek(orderFunc, Optional.ofNullable(maybeValue)) { value ->
            val sortOrder = orderFunc(javaBuilder.structure().fields())
            val fieldLike = sortOrder.expr() as? JavaSqlExpr.FieldLike<T, *>
            val dbType = fieldLike?.dbType() ?: throw IllegalArgumentException("Cannot extract DbType from SortOrder expression")
            JavaSqlExpr.ConstReq(value, dbType)
        })
    }

    /**
     * Conditionally add a seek predicate or just order by.
     * Overload that allows providing a custom asConst function.
     */
    fun <T> maybeSeek(
        orderFunc: (Fields) -> SortOrder<T>,
        maybeValue: T?,
        asConst: (T) -> SqlExpr.Const<T>
    ): SelectBuilder<Fields, Row> {
        return SelectBuilder(javaBuilder.maybeSeek(orderFunc, Optional.ofNullable(maybeValue)) { value ->
            asConst(value).underlying
        })
    }

    /**
     * Set the offset for the query.
     */
    fun offset(offset: Int): SelectBuilder<Fields, Row> {
        return SelectBuilder(javaBuilder.offset(offset))
    }

    /**
     * Set the limit for the query.
     */
    fun limit(limit: Int): SelectBuilder<Fields, Row> {
        return SelectBuilder(javaBuilder.limit(limit))
    }

    /**
     * Execute the query and return the results as a list.
     */
    fun toList(connection: Connection): List<Row> {
        return javaBuilder.toList(connection.javaConnection)
    }

    /**
     * Execute a count query.
     */
    fun count(connection: Connection): Int {
        return javaBuilder.count(connection.javaConnection)
    }

    /**
     * Return SQL for debugging. Returns null if backed by a mock repository.
     */
    fun sql(): Fragment? {
        return javaBuilder.sql().map { Fragment(it) }.orElse(null)
    }

    /**
     * Convert this SelectBuilder to a Subquery expression for use in IN clauses.
     */
    fun subquery(): SqlExpr.Subquery<Fields, Row> =
        SqlExpr.Subquery(javaBuilder.subquery())

    /**
     * Join using a foreign key relationship.
     */
    fun <Fields2, Row2> joinFk(
        fkFunc: (Fields) -> ForeignKey<Fields2, Row2>,
        other: SelectBuilder<Fields2, Row2>
    ): SelectBuilder<Tuple2<Fields, Fields2>, Tuple2<Row, Row2>> {
        return SelectBuilder(javaBuilder.joinFk({ fields -> fkFunc(fields).underlying }, other.javaBuilder))
    }

    /**
     * Start constructing a join.
     */
    fun <Fields2, Row2> join(other: SelectBuilder<Fields2, Row2>): PartialJoin<Fields, Row, Fields2, Row2> {
        return PartialJoin(this, other)
    }

    /**
     * Inner join with the given predicate.
     */
    fun <Fields2, Row2> joinOn(
        other: SelectBuilder<Fields2, Row2>,
        pred: (Tuple2<Fields, Fields2>) -> SqlExpr<Boolean>
    ): SelectBuilder<Tuple2<Fields, Fields2>, Tuple2<Row, Row2>> {
        return SelectBuilder(javaBuilder.joinOn(other.javaBuilder) { t -> pred(t).underlying })
    }

    /**
     * Left join with the given predicate.
     */
    fun <Fields2, Row2> leftJoinOn(
        other: SelectBuilder<Fields2, Row2>,
        pred: (Tuple2<Fields, Fields2>) -> SqlExpr<Boolean>
    ): LeftJoinSelectBuilder<Fields, Fields2, Row, Row2> {
        val javaResult: JavaSelectBuilder<Tuple2<Fields, Fields2>, Tuple2<Row, Optional<Row2>>> =
            javaBuilder.leftJoinOn(other.javaBuilder) { t -> pred(t).underlying }
        return LeftJoinSelectBuilder(javaResult)
    }

    /**
     * Multiset join with the given predicate.
     */
    fun <Fields2, Row2> multisetOn(
        other: SelectBuilder<Fields2, Row2>,
        pred: (Tuple2<Fields, Fields2>) -> SqlExpr<Boolean>
    ): MultisetSelectBuilder<Fields, Fields2, Row, Row2> {
        return MultisetSelectBuilder(javaBuilder.multisetOn(other.javaBuilder) { t -> pred(t).underlying })
    }

    /**
     * Group by a single key.
     */
    fun <G> groupBy(groupKey: (Fields) -> SqlExpr<G>): dev.typr.dsl.GroupedBuilder<Fields, Row> {
        return javaBuilder.groupBy { fields -> groupKey(fields).underlying }
    }

    /**
     * Group by two keys.
     */
    fun <G1, G2> groupBy(
        key1: (Fields) -> SqlExpr<G1>,
        key2: (Fields) -> SqlExpr<G2>
    ): dev.typr.dsl.GroupedBuilder<Fields, Row> {
        return javaBuilder.groupBy({ fields -> key1(fields).underlying }, { fields -> key2(fields).underlying })
    }

    /**
     * Group by three keys.
     */
    fun <G1, G2, G3> groupBy(
        key1: (Fields) -> SqlExpr<G1>,
        key2: (Fields) -> SqlExpr<G2>,
        key3: (Fields) -> SqlExpr<G3>
    ): dev.typr.dsl.GroupedBuilder<Fields, Row> {
        return javaBuilder.groupBy({ fields -> key1(fields).underlying }, { fields -> key2(fields).underlying }, { fields -> key3(fields).underlying })
    }

    /**
     * Group by a list of expressions.
     */
    fun groupByExpr(groupKeys: (Fields) -> List<SqlExpr<*>>): dev.typr.dsl.GroupedBuilder<Fields, Row> {
        return javaBuilder.groupByExpr { fields -> groupKeys(fields).map { it.underlying } }
    }

    /**
     * Project to a tuple expression.
     * Use with tupleWith() methods on SqlExpr:
     * ```kotlin
     * // Project to 1 column
     * builder.map { p -> p.name().tupleWith() }
     *
     * // Project to 2 columns
     * builder.map { p -> p.name().tupleWith(p.age()) }
     *
     * // Project to multiple columns
     * builder.map { p -> p.name().tupleWith(p.age(), p.email()) }
     * ```
     */
    fun <NewRow : dev.typr.foundations.Tuple> map(
        f: (Fields) -> TupleExpr<NewRow>
    ): SelectBuilder<dev.typr.dsl.TupleExpr<NewRow>, NewRow> {
        return SelectBuilder(javaBuilder.map { fields -> f(fields).underlying })
    }

    /**
     * Helper class for building joins with fluent syntax.
     */
    class PartialJoin<Fields, Row, Fields2, Row2> internal constructor(
        private val parent: SelectBuilder<Fields, Row>,
        private val other: SelectBuilder<Fields2, Row2>
    ) {
        /**
         * Complete the join using a foreign key.
         */
        fun onFk(fkFunc: (Fields) -> ForeignKey<Fields2, Row2>): SelectBuilder<Tuple2<Fields, Fields2>, Tuple2<Row, Row2>> {
            return parent.joinFk(fkFunc, other)
        }

        /**
         * Inner join with the given predicate.
         */
        fun on(pred: (Tuple2<Fields, Fields2>) -> dev.typr.dslkt.SqlExpr<Boolean>): SelectBuilder<Tuple2<Fields, Fields2>, Tuple2<Row, Row2>> {
            return parent.joinOn(other, pred)
        }

        /**
         * Left join with the given predicate.
         */
        fun leftOn(pred: (Tuple2<Fields, Fields2>) -> dev.typr.dslkt.SqlExpr<Boolean>): LeftJoinSelectBuilder<Fields, Fields2, Row, Row2> {
            return parent.leftJoinOn(other, pred)
        }
    }

    companion object {
        /**
         * Create a SelectBuilder for a table.
         */
        fun <Fields, Row : Any> of(
            name: String,
            structure: RelationStructure<Fields, Row>,
            rowCodec: dev.typr.foundationskt.RowCodec<Row>,
            dialect: Dialect
        ): SelectBuilder<Fields, Row> {
            return SelectBuilder(JavaSelectBuilder.of(name, structure, rowCodec.underlying, dialect))
        }
    }
}

/**
 * A specialized SelectBuilder for multiset joins that converts java.util.List to kotlin.List.
 */
class MultisetSelectBuilder<Fields1, Fields2, Row1, Row2> internal constructor(
    private val javaBuilder: JavaSelectBuilder<Tuple2<Fields1, Fields2>, Tuple2<Row1, List<Row2>>>
) {

    fun renderCtx(): RenderCtx = javaBuilder.renderCtx()

    fun where(predicate: SqlExprExtensions.(Tuple2<Fields1, Fields2>) -> SqlExpr<Boolean>): MultisetSelectBuilder<Fields1, Fields2, Row1, Row2> {
        return MultisetSelectBuilder(javaBuilder.where { t -> SqlExprExtensionsInstance.predicate(t).underlying })
    }

    fun <T> maybeWhere(value: T?, predicate: SqlExprExtensions.(Tuple2<Fields1, Fields2>, T) -> SqlExpr<Boolean>): MultisetSelectBuilder<Fields1, Fields2, Row1, Row2> {
        return MultisetSelectBuilder(javaBuilder.maybeWhere(Optional.ofNullable(value)) { t, v -> SqlExprExtensionsInstance.predicate(t, v).underlying })
    }

    fun <T> orderBy(orderFunc: (Tuple2<Fields1, Fields2>) -> SortOrder<T>): MultisetSelectBuilder<Fields1, Fields2, Row1, Row2> {
        return MultisetSelectBuilder(javaBuilder.orderBy(orderFunc))
    }

    fun offset(offset: Int): MultisetSelectBuilder<Fields1, Fields2, Row1, Row2> {
        return MultisetSelectBuilder(javaBuilder.offset(offset))
    }

    fun limit(limit: Int): MultisetSelectBuilder<Fields1, Fields2, Row1, Row2> {
        return MultisetSelectBuilder(javaBuilder.limit(limit))
    }

    fun toList(connection: Connection): List<Pair<Row1, List<Row2>>> {
        return javaBuilder.toList(connection.javaConnection).map { javaTuple ->
            Pair(javaTuple._1(), javaTuple._2().toList())
        }
    }

    fun count(connection: Connection): Int {
        return javaBuilder.count(connection.javaConnection)
    }

    fun sql(): Fragment? {
        return javaBuilder.sql().map { Fragment(it) }.orElse(null)
    }
}

/**
 * A specialized SelectBuilder for left joins that converts java.util.Optional to nullable types.
 */
class LeftJoinSelectBuilder<Fields1, Fields2, Row1, Row2>(
    private val javaBuilder: JavaSelectBuilder<Tuple2<Fields1, Fields2>, Tuple2<Row1, Optional<Row2>>>
) {

    fun renderCtx(): RenderCtx = javaBuilder.renderCtx()

    fun where(predicate: SqlExprExtensions.(Tuple2<Fields1, Fields2>) -> SqlExpr<Boolean>): LeftJoinSelectBuilder<Fields1, Fields2, Row1, Row2> {
        return LeftJoinSelectBuilder(javaBuilder.where { t -> SqlExprExtensionsInstance.predicate(t).underlying })
    }

    fun <T> maybeWhere(value: T?, predicate: SqlExprExtensions.(Tuple2<Fields1, Fields2>, T) -> SqlExpr<Boolean>): LeftJoinSelectBuilder<Fields1, Fields2, Row1, Row2> {
        return LeftJoinSelectBuilder(javaBuilder.maybeWhere(Optional.ofNullable(value)) { t, v -> SqlExprExtensionsInstance.predicate(t, v).underlying })
    }

    fun <T> orderBy(orderFunc: (Tuple2<Fields1, Fields2>) -> SortOrder<T>): LeftJoinSelectBuilder<Fields1, Fields2, Row1, Row2> {
        return LeftJoinSelectBuilder(javaBuilder.orderBy(orderFunc))
    }

    fun offset(offset: Int): LeftJoinSelectBuilder<Fields1, Fields2, Row1, Row2> {
        return LeftJoinSelectBuilder(javaBuilder.offset(offset))
    }

    fun limit(limit: Int): LeftJoinSelectBuilder<Fields1, Fields2, Row1, Row2> {
        return LeftJoinSelectBuilder(javaBuilder.limit(limit))
    }

    fun toList(connection: Connection): List<Pair<Row1, Row2?>> {
        return javaBuilder.toList(connection.javaConnection).map { javaTuple ->
            Pair(javaTuple._1(), javaTuple._2().orElse(null))
        }
    }

    fun count(connection: Connection): Int {
        return javaBuilder.count(connection.javaConnection)
    }

    fun sql(): Fragment? {
        return javaBuilder.sql().map { Fragment(it) }.orElse(null)
    }
}
