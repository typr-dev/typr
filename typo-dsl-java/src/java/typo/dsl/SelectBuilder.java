package typo.dsl;

import typo.runtime.Fragment;
import typo.runtime.RowParser;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Builder for SQL SELECT queries with type-safe operations.
 */
public interface SelectBuilder<Fields, Row> {

    /**
     * Create a SelectBuilder for a table.
     */
    static <Fields, Row> SelectBuilder<Fields, Row> of(
            String name,
            Structure.Relation<Fields, Row> structure,
            RowParser<Row> rowParser,
            Dialect dialect) {
        return new SelectBuilderSql.Relation<>(name, structure, rowParser, SelectParams.empty(), dialect);
    }

    RenderCtx renderCtx();
    Structure<Fields, Row> structure();
    
    /**
     * Add a where clause to the query.
     * Consecutive calls to where will be combined with AND.
     *
     * Example:
     * productRepo.select()
     *   .where(p -> p.productClass().isEqual(new ConstReq<>("H")))
     *   .where(p -> p.daysToManufacture().greaterThan(new ConstReq<>(25))
     *                .or(p.daysToManufacture().lessThanOrEqual(new ConstReq<>(0)), Bijection.asBool()))
     */
    default SelectBuilder<Fields, Row> where(Function<Fields, SqlExpr<Boolean>> predicate) {
        return withParams(params().where(predicate));
    }
    
    /**
     * Conditionally add a where clause based on an optional value.
     */
    default <T> SelectBuilder<Fields, Row> maybeWhere(Optional<T> value, BiFunction<Fields, T, SqlExpr<Boolean>> predicate) {
        return value.map(t -> where(fields -> predicate.apply(fields, t)))
                   .orElse(this);
    }
    
    /**
     * Add an order by clause to the query.
     * Consecutive calls to orderBy will be combined and order kept.
     */
    default <T> SelectBuilder<Fields, Row> orderBy(Function<Fields, SortOrder<T>> orderFunc) {
        return withParams(params().orderBy(orderFunc));
    }
    
    /**
     * Add a seek predicate for cursor-based pagination.
     */
    default <T> SelectBuilder<Fields, Row> seek(Function<Fields, SortOrder<T>> orderFunc, SqlExpr.Const<T> value) {
        return withParams(params().seek(orderFunc, value));
    }
    
    /**
     * Conditionally add a seek predicate or just order by.
     */
    default <T> SelectBuilder<Fields, Row> maybeSeek(
            Function<Fields, SortOrder<T>> orderFunc, 
            Optional<T> maybeValue,
            Function<T, SqlExpr.Const<T>> asConst) {
        return maybeValue.map(value -> seek(orderFunc, asConst.apply(value)))
                        .orElse(orderBy(orderFunc));
    }
    
    /**
     * Set the offset for the query.
     */
    default SelectBuilder<Fields, Row> offset(int offset) {
        return withParams(params().offset(offset));
    }
    
    /**
     * Set the limit for the query.
     */
    default SelectBuilder<Fields, Row> limit(int limit) {
        return withParams(params().limit(limit));
    }
    
    /**
     * Execute the query and return the results as a list.
     */
    List<Row> toList(Connection connection);
    
    /**
     * Execute a count query.
     */
    int count(Connection connection);
    
    /**
     * Return SQL for debugging. Empty if backed by a mock repository.
     */
    Optional<Fragment> sql();
    
    /**
     * Join using a foreign key relationship.
     */
    default <Fields2, Row2> SelectBuilder<Structure.Tuple2<Fields, Fields2>, Structure.Tuple2<Row, Row2>> 
            joinFk(Function<Fields, ForeignKey<Fields2, Row2>> fkFunc, SelectBuilder<Fields2, Row2> other) {
        return joinOn(other, (fields1_2) -> {
            Fields fields1 = fields1_2._1();
            Fields2 fields2 = fields1_2._2();
            ForeignKey<Fields2, Row2> fk = fkFunc.apply(fields1);
            
            SqlExpr<Boolean> condition = null;
            for (ForeignKey.ColumnPair<?, Fields2> pair : fk.columnPairs()) {
                condition = condition == null 
                    ? buildEqualityCondition(pair, fields2)
                    : condition.and(buildEqualityCondition(pair, fields2), Bijection.asBool());
            }
            return condition;
        });
    }
    
    /**
     * Helper method to build equality condition for a column pair with proper typing.
     * This method captures the unknown type parameter T from the ColumnPair.
     */
    private static <T, Fields2> SqlExpr<Boolean> buildEqualityCondition(
            ForeignKey.ColumnPair<T, Fields2> pair, 
            Fields2 fields2) {
        SqlExpr<T> thisField = pair.thisField();
        SqlExpr<T> thatField = pair.thatField().apply(fields2);
        return thisField.isEqual(thatField);
    }
    
    /**
     * Start constructing a join.
     */
    default <Fields2, Row2> PartialJoin<Fields, Row, Fields2, Row2> join(SelectBuilder<Fields2, Row2> other) {
        return new PartialJoin<>(this, other);
    }
    
    /**
     * Inner join with the given predicate.
     */
    <Fields2, Row2> SelectBuilder<Structure.Tuple2<Fields, Fields2>, Structure.Tuple2<Row, Row2>> 
        joinOn(SelectBuilder<Fields2, Row2> other, Function<Structure.Tuple2<Fields, Fields2>, SqlExpr<Boolean>> pred);
    
    /**
     * Left join with the given predicate.
     */
    <Fields2, Row2> SelectBuilder<Structure.Tuple2<Fields, Fields2>, Structure.Tuple2<Row, Optional<Row2>>> 
        leftJoinOn(SelectBuilder<Fields2, Row2> other, Function<Structure.Tuple2<Fields, Fields2>, SqlExpr<Boolean>> pred);
    
    // Protected methods that implementations must provide
    SelectParams<Fields, Row> params();
    SelectBuilder<Fields, Row> withParams(SelectParams<Fields, Row> params);
    
    /**
     * Helper class for building joins with fluent syntax.
     */
    class PartialJoin<Fields, Row, Fields2, Row2> {
        private final SelectBuilder<Fields, Row> parent;
        private final SelectBuilder<Fields2, Row2> other;
        
        public PartialJoin(SelectBuilder<Fields, Row> parent, SelectBuilder<Fields2, Row2> other) {
            this.parent = parent;
            this.other = other;
        }
        
        /**
         * Complete the join using a foreign key.
         */
        public SelectBuilder<Structure.Tuple2<Fields, Fields2>, Structure.Tuple2<Row, Row2>> 
                onFk(Function<Fields, ForeignKey<Fields2, Row2>> fkFunc) {
            return parent.joinFk(fkFunc, other);
        }
        
        /**
         * Inner join with the given predicate.
         */
        public SelectBuilder<Structure.Tuple2<Fields, Fields2>, Structure.Tuple2<Row, Row2>> 
                on(Function<Structure.Tuple2<Fields, Fields2>, SqlExpr<Boolean>> pred) {
            return parent.joinOn(other, pred);
        }
        
        /**
         * Left join with the given predicate.
         */
        public SelectBuilder<Structure.Tuple2<Fields, Fields2>, Structure.Tuple2<Row, Optional<Row2>>> 
                leftOn(Function<Structure.Tuple2<Fields, Fields2>, SqlExpr<Boolean>> pred) {
            return parent.leftJoinOn(other, pred);
        }
    }
}