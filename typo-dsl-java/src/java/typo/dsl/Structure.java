package typo.dsl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Expresses that (tuples of) Fields structures can be joined.
 * This also serves as the type-level connection between Fields and Row.
 * 
 * Note: Java doesn't have Scala's tuple types, so we use a Tuple interface
 * to represent joined structures.
 */
public interface Structure<Fields, Row> {
    Fields fields();
    List<SqlExpr.FieldLike<?, Row>> columns();
    List<Path> path();
    Structure<Fields, Row> withPath(Path path);
    
    // It's up to you to ensure that the Row in field is the same type as row
    <T> Optional<T> untypedGet(SqlExpr.FieldLike<T, ?> field, Row row);
    
    // It's up to you to ensure that the Row in field is the same type as row
    default <T> Optional<T> untypedEval(SqlExpr<T> expr, Row row) {
        
        SqlExprVisitor<T, Row, Optional<T>> visitor = new SqlExprVisitor<T, Row, Optional<T>>() {
            @Override
            public Optional<T> visitFieldLike(SqlExpr.FieldLike<T, Row> field) {
                return untypedGet(field, row);
            }
            
            @Override
            public Optional<T> visitConstReq(SqlExpr.ConstReq<T> constReq) {
                return Optional.of(constReq.value());
            }
            
            @Override
            public Optional<T> visitConstOpt(SqlExpr.ConstOpt<T> constOpt) {
                return constOpt.value();
            }
            
            @Override
            public Optional<T> visitArrayIndex(SqlExpr.ArrayIndex<T> arrayIndex) {
                return untypedEval(arrayIndex.arr(), row).flatMap(arr ->
                    untypedEval(arrayIndex.idx(), row).flatMap(i -> {
                        T[] array = (T[]) arr;
                        if (i < 0 || i >= array.length) {
                            return Optional.empty();
                        }
                        return Optional.of(array[i]);
                    })
                );
            }
            
            @Override
            public <A> Optional<T> visitApply1(SqlExpr.Apply1<A, T> apply1) {
                Optional<A> arg = untypedEval(apply1.arg1(), row);
                return arg.map(v -> apply1.f().eval().apply(v));
            }
            
            @Override
            public <A, B> Optional<T> visitApply2(SqlExpr.Apply2<A, B, T> apply2) {
                Optional<A> arg1 = untypedEval(apply2.arg1(), row);
                Optional<B> arg2 = untypedEval(apply2.arg2(), row);
                return arg1.flatMap(v1 ->
                    arg2.map(v2 -> apply2.f().eval().apply(v1, v2))
                );
            }
            
            @Override
            public <A, B, C> Optional<T> visitApply3(SqlExpr.Apply3<A, B, C, T> apply3) {
                Optional<A> arg1 = untypedEval(apply3.arg1(), row);
                Optional<B> arg2 = untypedEval(apply3.arg2(), row);
                Optional<C> arg3 = untypedEval(apply3.arg3(), row);
                return arg1.flatMap(v1 ->
                    arg2.flatMap(v2 ->
                        arg3.map(v3 -> apply3.f().eval().apply(v1, v2, v3))
                    )
                );
            }
            
            @Override
            public <A, B> Optional<T> visitBinary(SqlExpr.Binary<A, B, T> binary) {
                Optional<A> left = untypedEval(binary.left(), row);
                Optional<B> right = untypedEval(binary.right(), row);
                return left.flatMap(v1 ->
                    right.map(v2 -> binary.op().eval().apply(v1, v2))
                );
            }
            
            @Override
            public <U> Optional<T> visitUnderlying(SqlExpr.Underlying<U, T> underlying) {
                Optional<U> value = untypedEval(underlying.expr(), row);
                return value.map(v -> underlying.bijection().underlying(v));
            }
            
            @Override
            public Optional<T> visitCoalesce(SqlExpr.Coalesce<T> coalesce) {
                Optional<T> result = untypedEval(coalesce.expr(), row);
                if (result.isPresent()) {
                    return result;
                }
                return untypedEval(coalesce.getOrElse(), row);
            }
            
            @Override
            public Optional<T> visitInExpr(SqlExpr.In<?> in) {
                // For In expressions, T is always Boolean, so we can safely cast the result
                Optional<Boolean> result = evaluateInExpressionHelper(Structure.this, in, row);
                return result.map(b -> (T) b);
            }
            
            @Override
            public Optional<T> visitIsNullExpr(SqlExpr.IsNull<?> isNull) {
                // For IsNull expressions, T is always Boolean, so we can safely cast the result
                Boolean result = evaluateIsNullExpression(Structure.this, isNull, row);
                return Optional.of((T) result);
            }
            
            @Override
            public Optional<T> visitNot(SqlExpr.Not<T> not) {
                return untypedEval(not.expr(), row).map(v -> {
                    Bijection<T, Boolean> bijection = not.B();
                    Boolean b = bijection.underlying(v);
                    return bijection.from(!b);
                });
            }
            
            @Override
            public Optional<T> visitRowExpr(SqlExpr.RowExpr rowExpr) {
                List<Optional<?>> values = new ArrayList<>();
                for (SqlExpr<?> e : rowExpr.exprs()) {
                    values.add(untypedEval(e, row));
                }
                return Optional.of((T) values);
            }
            
            @Override
            public <Tuple> Optional<T> visitCompositeInExpr(SqlExpr.CompositeIn<Tuple, ?> compositeIn) {
                // For CompositeIn expressions, T is always Boolean, so we can safely cast the result
                Boolean result = evaluateCompositeInExpression(Structure.this, compositeIn, row);
                return Optional.of((T) result);
            }
            
            @Override
            public Optional<T> visitDefault(SqlExpr<T> expr) {
                throw new UnsupportedOperationException("Unknown expression type: " + expr.getClass());
            }
        };
        
        return visitor.accept(expr);
    }
    
    default <Fields2, Row2> Structure<Tuple2<Fields, Fields2>, Tuple2<Row, Row2>> join(Structure<Fields2, Row2> other) {
        return new Tupled<>(sharedPrefix(this.path(), other.path()), this, other);
    }
    
    default <Fields2, Row2> Structure<Tuple2<Fields, Fields2>, Tuple2<Row, Optional<Row2>>> leftJoin(Structure<Fields2, Row2> other) {
        return new LeftTupled<>(sharedPrefix(this.path(), other.path()), this, other);
    }
    
    static <T> List<T> sharedPrefix(List<T> left, List<T> right) {
        List<T> prefix = new ArrayList<>();
        int minSize = Math.min(left.size(), right.size());
        for (int i = 0; i < minSize; i++) {
            if (left.get(i).equals(right.get(i))) {
                prefix.add(left.get(i));
            } else {
                break;
            }
        }
        return prefix;
    }
    
    // Tuple types for joining - record implementation for proper equals/hashCode
    record Tuple2<T1, T2>(T1 _1, T2 _2) {
        public static <T1, T2> Tuple2<T1, T2> of(T1 first, T2 second) {
            return new Tuple2<>(first, second);
        }
    }
    
    // Base class for relations
    abstract class Relation<Fields, Row> implements Structure<Fields, Row> {
        protected final List<Path> _path;
        
        protected Relation(List<Path> path) {
            this._path = new ArrayList<>(path);
        }
        
        @Override
        public List<Path> path() {
            return new ArrayList<>(_path);
        }
        
        @Override
        public Structure<Fields, Row> withPath(Path newPath) {
            List<Path> newPaths = new ArrayList<>();
            newPaths.add(newPath);
            newPaths.addAll(_path);
            return copy(newPaths);
        }
        
        protected abstract Relation<Fields, Row> copy(List<Path> path);
        
        @Override
        @SuppressWarnings("unchecked")
        public <T> Optional<T> untypedGet(SqlExpr.FieldLike<T, ?> field, Row row) {
            // This cast is necessary because the field's Row type parameter might not
            // match our Row type due to joins and other operations. This mirrors Scala's
            // castRow extension method. The cast is safe within our controlled environment
            // where we ensure fields and rows correspond correctly.
            SqlExpr.FieldLike<T, Row> typedField = (SqlExpr.FieldLike<T, Row>) field;
            return typedField.get(row);
        }
    }
    
    // Implementation for joined structures
    class Tupled<Fields1, Fields2, Row1, Row2> implements Structure<Tuple2<Fields1, Fields2>, Tuple2<Row1, Row2>> {
        private final List<Path> _path;
        private final Structure<Fields1, Row1> left;
        private final Structure<Fields2, Row2> right;
        
        Tupled(List<Path> path, Structure<Fields1, Row1> left, Structure<Fields2, Row2> right) {
            this._path = new ArrayList<>(path);
            this.left = left;
            this.right = right;
        }
        
        @Override
        public Tuple2<Fields1, Fields2> fields() {
            return Tuple2.of(left.fields(), right.fields());
        }
        
        @Override
        @SuppressWarnings("unchecked")
        public List<SqlExpr.FieldLike<?, Tuple2<Row1, Row2>>> columns() {
            List<SqlExpr.FieldLike<?, Tuple2<Row1, Row2>>> cols = new ArrayList<>();
            for (var col : left.columns()) {
                cols.add((SqlExpr.FieldLike<?, Tuple2<Row1, Row2>>) (SqlExpr.FieldLike<?, ?>) col);
            }
            for (var col : right.columns()) {
                cols.add((SqlExpr.FieldLike<?, Tuple2<Row1, Row2>>) (SqlExpr.FieldLike<?, ?>) col);
            }
            return cols;
        }

        @Override
        public List<Path> path() {
            return new ArrayList<>(_path);
        }

        @Override
        public Structure<Tuple2<Fields1, Fields2>, Tuple2<Row1, Row2>> withPath(Path newPath) {
            List<Path> newPaths = new ArrayList<>();
            newPaths.add(newPath);
            newPaths.addAll(_path);
            return new Tupled<>(newPaths, left.withPath(newPath), right.withPath(newPath));
        }

        @Override
        public <T> Optional<T> untypedGet(SqlExpr.FieldLike<T, ?> field, Tuple2<Row1, Row2> row) {
            if (containsField(left.columns(), field)) {
                return left.untypedGet(field, row._1());
            } else {
                return right.untypedGet(field, row._2());
            }
        }
    }

    // Implementation for left joined structures
    class LeftTupled<Fields1, Fields2, Row1, Row2> implements Structure<Tuple2<Fields1, Fields2>, Tuple2<Row1, Optional<Row2>>> {
        private final List<Path> _path;
        private final Structure<Fields1, Row1> left;
        private final Structure<Fields2, Row2> right;

        LeftTupled(List<Path> path, Structure<Fields1, Row1> left, Structure<Fields2, Row2> right) {
            this._path = new ArrayList<>(path);
            this.left = left;
            this.right = right;
        }

        @Override
        public Tuple2<Fields1, Fields2> fields() {
            return Tuple2.of(left.fields(), right.fields());
        }

        @Override
        @SuppressWarnings("unchecked")
        public List<SqlExpr.FieldLike<?, Tuple2<Row1, Optional<Row2>>>> columns() {
            List<SqlExpr.FieldLike<?, Tuple2<Row1, Optional<Row2>>>> cols = new ArrayList<>();
            for (var col : left.columns()) {
                cols.add((SqlExpr.FieldLike<?, Tuple2<Row1, Optional<Row2>>>) (SqlExpr.FieldLike<?, ?>) col);
            }
            for (var col : right.columns()) {
                cols.add((SqlExpr.FieldLike<?, Tuple2<Row1, Optional<Row2>>>) (SqlExpr.FieldLike<?, ?>) col);
            }
            return cols;
        }
        
        @Override
        public List<Path> path() {
            return new ArrayList<>(_path);
        }
        
        @Override
        public Structure<Tuple2<Fields1, Fields2>, Tuple2<Row1, Optional<Row2>>> withPath(Path newPath) {
            List<Path> newPaths = new ArrayList<>();
            newPaths.add(newPath);
            newPaths.addAll(_path);
            return new LeftTupled<>(newPaths, left.withPath(newPath), right.withPath(newPath));
        }
        
        @Override
        public <T> Optional<T> untypedGet(SqlExpr.FieldLike<T, ?> field, Tuple2<Row1, Optional<Row2>> row) {
            if (containsField(left.columns(), field)) {
                return left.untypedGet(field, row._1());
            } else {
                return row._2().flatMap(r2 -> right.untypedGet(field, r2));
            }
        }
    }

    // Helper method to check if a field exists in a columns list by path and column name
    // This is needed because Field records contain Function/BiFunction which don't have structural equality
    private static boolean containsField(List<? extends SqlExpr.FieldLike<?, ?>> columns, SqlExpr.FieldLike<?, ?> field) {
        for (var col : columns) {
            if (col.path().equals(field.path()) && col.column().equals(field.column())) {
                return true;
            }
        }
        return false;
    }

    // Helper methods for type-safe boolean expression evaluation

    private static <T, Row> Optional<Boolean> evaluateInExpressionHelper(Structure<?, Row> structure, SqlExpr.In<T> in, Row row) {
        return structure.untypedEval(in.expr(), row).map(v -> {
            for (T value : in.values()) {
                if (v.equals(value)) {
                    return Boolean.TRUE;
                }
            }
            return Boolean.FALSE;
        });
    }
    
    private static <Row> Boolean evaluateIsNullExpression(Structure<?, Row> structure, SqlExpr.IsNull<?> isNull, Row row) {
        return structure.untypedEval(isNull.expr(), row).isEmpty();
    }
    
    private static <Row, Tuple> Boolean evaluateCompositeInExpression(Structure<?, Row> structure, SqlExpr.CompositeIn<Tuple, ?> compositeIn, Row row) {
        // Build the row from current data
        List<Object> thisRow = new ArrayList<>();
        for (var part : compositeIn.parts()) {
            Optional<?> value = structure.untypedEval(part.field(), row);
            if (value.isEmpty()) {
                return Boolean.FALSE;
            }
            thisRow.add(value.get());
        }
        
        // Check if any tuple matches
        for (Tuple tuple : compositeIn.tuples()) {
            List<Object> thatRow = new ArrayList<>();
            for (var part : compositeIn.parts()) {
                // Extract value from the part with proper typing
                Object value = extractPartValue(part, tuple);
                thatRow.add(value);
            }
            if (thisRow.equals(thatRow)) {
                return Boolean.TRUE;
            }
        }
        return Boolean.FALSE;
    }
    
    private static <T, Tuple, Row> T extractPartValue(SqlExpr.CompositeIn.Part<T, Tuple, Row> part, Tuple tuple) {
        return part.extract().apply(tuple);
    }
}