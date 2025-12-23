package typr.kotlindsl

import typr.dsl.SelectBuilder as JavaSelectBuilder
import typr.dsl.Structure
import typr.dsl.SqlExpr
import typr.dsl.SortOrder
import typr.dsl.RenderCtx
import typr.dsl.Tuple2
import typr.dsl.Dialect
import typr.runtime.Fragment
import java.sql.Connection
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
     */
    fun where(predicate: (Fields) -> SqlExpr<Boolean>): SelectBuilder<Fields, Row> {
        return SelectBuilder(javaBuilder.where(predicate))
    }

    /**
     * Conditionally add a where clause based on a nullable value.
     */
    fun <T> maybeWhere(value: T?, predicate: (Fields, T) -> SqlExpr<Boolean>): SelectBuilder<Fields, Row> {
        return SelectBuilder(javaBuilder.maybeWhere(Optional.ofNullable(value), predicate))
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
        return SelectBuilder(javaBuilder.seek(orderFunc, value))
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
            val fieldLike = sortOrder.expr() as? typr.dsl.SqlExpr.FieldLike<T, *>
            val pgType = fieldLike?.pgType() ?: throw IllegalArgumentException("Cannot extract DbType from SortOrder expression")
            typr.dsl.SqlExpr.ConstReq(value, pgType)
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
        return SelectBuilder(javaBuilder.maybeSeek(orderFunc, Optional.ofNullable(maybeValue), asConst))
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
        return javaBuilder.toList(connection)
    }

    /**
     * Execute a count query.
     */
    fun count(connection: Connection): Int {
        return javaBuilder.count(connection)
    }

    /**
     * Return SQL for debugging. Returns null if backed by a mock repository.
     */
    fun sql(): Fragment? {
        return javaBuilder.sql().orElse(null)
    }

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
        return SelectBuilder(javaBuilder.joinOn(other.javaBuilder, pred))
    }

    /**
     * Left join with the given predicate.
     */
    fun <Fields2, Row2> leftJoinOn(
        other: SelectBuilder<Fields2, Row2>,
        pred: (Tuple2<Fields, Fields2>) -> SqlExpr<Boolean>
    ): LeftJoinSelectBuilder<Fields, Fields2, Row, Row2> {
        val javaResult: JavaSelectBuilder<Tuple2<Fields, Fields2>, Tuple2<Row, Optional<Row2>>> =
            javaBuilder.leftJoinOn(other.javaBuilder, pred)
        return LeftJoinSelectBuilder(javaResult)
    }

    /**
     * Multiset join with the given predicate.
     */
    fun <Fields2, Row2> multisetOn(
        other: SelectBuilder<Fields2, Row2>,
        pred: (Tuple2<Fields, Fields2>) -> SqlExpr<Boolean>
    ): MultisetSelectBuilder<Fields, Fields2, Row, Row2> {
        return MultisetSelectBuilder(javaBuilder.multisetOn(other.javaBuilder, pred))
    }

    /**
     * Group by a single key.
     */
    fun <G> groupBy(groupKey: (Fields) -> SqlExpr<G>): typr.dsl.GroupedBuilder<Fields, Row> {
        return javaBuilder.groupBy(groupKey)
    }

    /**
     * Group by two keys.
     */
    fun <G1, G2> groupBy(
        key1: (Fields) -> SqlExpr<G1>,
        key2: (Fields) -> SqlExpr<G2>
    ): typr.dsl.GroupedBuilder<Fields, Row> {
        return javaBuilder.groupBy(key1, key2)
    }

    /**
     * Group by three keys.
     */
    fun <G1, G2, G3> groupBy(
        key1: (Fields) -> SqlExpr<G1>,
        key2: (Fields) -> SqlExpr<G2>,
        key3: (Fields) -> SqlExpr<G3>
    ): typr.dsl.GroupedBuilder<Fields, Row> {
        return javaBuilder.groupBy(key1, key2, key3)
    }

    /**
     * Group by a list of expressions.
     */
    fun groupByExpr(groupKeys: (Fields) -> List<SqlExpr<*>>): typr.dsl.GroupedBuilder<Fields, Row> {
        return javaBuilder.groupByExpr { fields -> groupKeys(fields) }
    }

    /**
     * Project to 1 column.
     */
    fun <T0> map(f0: (Fields) -> typr.kotlindsl.SqlExpr.FieldLike<T0, *>): SelectBuilder<typr.dsl.Tuples.TupleExpr1<T0>, typr.dsl.Tuples.Tuple1<T0>> {
        return SelectBuilder(javaBuilder.map { fields -> f0(fields).underlying })
    }

    /**
     * Project to 2 columns.
     */
    fun <T0, T1> map(
        f0: (Fields) -> typr.kotlindsl.SqlExpr.FieldLike<T0, *>,
        f1: (Fields) -> typr.kotlindsl.SqlExpr.FieldLike<T1, *>
    ): SelectBuilder<typr.dsl.Tuples.TupleExpr2<T0, T1>, typr.dsl.Tuples.Tuple2<T0, T1>> {
        return SelectBuilder(javaBuilder.map(
            { fields -> f0(fields).underlying },
            { fields -> f1(fields).underlying }
        ))
    }

    /**
     * Project to 3 columns.
     */
    fun <T0, T1, T2> map(
        f0: (Fields) -> typr.kotlindsl.SqlExpr.FieldLike<T0, *>,
        f1: (Fields) -> typr.kotlindsl.SqlExpr.FieldLike<T1, *>,
        f2: (Fields) -> typr.kotlindsl.SqlExpr.FieldLike<T2, *>
    ): SelectBuilder<typr.dsl.Tuples.TupleExpr3<T0, T1, T2>, typr.dsl.Tuples.Tuple3<T0, T1, T2>> {
        return SelectBuilder(javaBuilder.map(
            { fields -> f0(fields).underlying },
            { fields -> f1(fields).underlying },
            { fields -> f2(fields).underlying }
        ))
    }

    /**
     * Project to 4 columns.
     */
    fun <T0, T1, T2, T3> map(
        f0: (Fields) -> typr.kotlindsl.SqlExpr.FieldLike<T0, *>,
        f1: (Fields) -> typr.kotlindsl.SqlExpr.FieldLike<T1, *>,
        f2: (Fields) -> typr.kotlindsl.SqlExpr.FieldLike<T2, *>,
        f3: (Fields) -> typr.kotlindsl.SqlExpr.FieldLike<T3, *>
    ): SelectBuilder<typr.dsl.Tuples.TupleExpr4<T0, T1, T2, T3>, typr.dsl.Tuples.Tuple4<T0, T1, T2, T3>> {
        return SelectBuilder(javaBuilder.map(
            { fields -> f0(fields).underlying },
            { fields -> f1(fields).underlying },
            { fields -> f2(fields).underlying },
            { fields -> f3(fields).underlying }
        ))
    }

    /**
     * Project to 5 columns.
     */
    fun <T0, T1, T2, T3, T4> map(
        f0: (Fields) -> typr.kotlindsl.SqlExpr.FieldLike<T0, *>,
        f1: (Fields) -> typr.kotlindsl.SqlExpr.FieldLike<T1, *>,
        f2: (Fields) -> typr.kotlindsl.SqlExpr.FieldLike<T2, *>,
        f3: (Fields) -> typr.kotlindsl.SqlExpr.FieldLike<T3, *>,
        f4: (Fields) -> typr.kotlindsl.SqlExpr.FieldLike<T4, *>
    ): SelectBuilder<typr.dsl.Tuples.TupleExpr5<T0, T1, T2, T3, T4>, typr.dsl.Tuples.Tuple5<T0, T1, T2, T3, T4>> {
        return SelectBuilder(javaBuilder.map(
            { fields -> f0(fields).underlying },
            { fields -> f1(fields).underlying },
            { fields -> f2(fields).underlying },
            { fields -> f3(fields).underlying },
            { fields -> f4(fields).underlying }
        ))
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
        fun on(pred: (Tuple2<Fields, Fields2>) -> SqlExpr<Boolean>): SelectBuilder<Tuple2<Fields, Fields2>, Tuple2<Row, Row2>> {
            return parent.joinOn(other, pred)
        }

        /**
         * Left join with the given predicate.
         */
        fun leftOn(pred: (Tuple2<Fields, Fields2>) -> SqlExpr<Boolean>): LeftJoinSelectBuilder<Fields, Fields2, Row, Row2> {
            return parent.leftJoinOn(other, pred)
        }
    }

    companion object {
        /**
         * Create a SelectBuilder for a table.
         */
        fun <Fields, Row> of(
            name: String,
            structure: RelationStructure<Fields, Row>,
            rowParser: typr.kotlindsl.RowParser<Row>,
            dialect: Dialect
        ): SelectBuilder<Fields, Row> {
            return SelectBuilder(JavaSelectBuilder.of(name, structure, rowParser.underlying, dialect))
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

    fun where(predicate: (Tuple2<Fields1, Fields2>) -> SqlExpr<Boolean>): MultisetSelectBuilder<Fields1, Fields2, Row1, Row2> {
        return MultisetSelectBuilder(javaBuilder.where(predicate))
    }

    fun <T> maybeWhere(value: T?, predicate: (Tuple2<Fields1, Fields2>, T) -> SqlExpr<Boolean>): MultisetSelectBuilder<Fields1, Fields2, Row1, Row2> {
        return MultisetSelectBuilder(javaBuilder.maybeWhere(Optional.ofNullable(value), predicate))
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
        return javaBuilder.toList(connection).map { javaTuple ->
            Pair(javaTuple._1(), javaTuple._2().toList())
        }
    }

    fun count(connection: Connection): Int {
        return javaBuilder.count(connection)
    }

    fun sql(): Fragment? {
        return javaBuilder.sql().orElse(null)
    }
}

/**
 * A specialized SelectBuilder for left joins that converts java.util.Optional to nullable types.
 */
class LeftJoinSelectBuilder<Fields1, Fields2, Row1, Row2>(
    private val javaBuilder: JavaSelectBuilder<Tuple2<Fields1, Fields2>, Tuple2<Row1, Optional<Row2>>>
) {

    fun renderCtx(): RenderCtx = javaBuilder.renderCtx()

    fun where(predicate: (Tuple2<Fields1, Fields2>) -> SqlExpr<Boolean>): LeftJoinSelectBuilder<Fields1, Fields2, Row1, Row2> {
        return LeftJoinSelectBuilder(javaBuilder.where(predicate))
    }

    fun <T> maybeWhere(value: T?, predicate: (Tuple2<Fields1, Fields2>, T) -> SqlExpr<Boolean>): LeftJoinSelectBuilder<Fields1, Fields2, Row1, Row2> {
        return LeftJoinSelectBuilder(javaBuilder.maybeWhere(Optional.ofNullable(value), predicate))
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
        return javaBuilder.toList(connection).map { javaTuple ->
            Pair(javaTuple._1(), javaTuple._2().orElse(null))
        }
    }

    fun count(connection: Connection): Int {
        return javaBuilder.count(connection)
    }

    fun sql(): Fragment? {
        return javaBuilder.sql().orElse(null)
    }
}
