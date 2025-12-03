package typo.dsl;

import typo.runtime.DbType;
import typo.runtime.Fragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;

public sealed interface SqlExpr<T> {

    /** Combine multiple boolean expressions with AND. Returns TRUE if all expressions are true. */
    @SafeVarargs
    static SqlExpr<Boolean> all(SqlExpr<Boolean>... exprs) {
        if (exprs.length == 0) {
            return new ConstReq<>(true, typo.runtime.PgTypes.bool);
        }
        SqlExpr<Boolean> result = exprs[0];
        for (int i = 1; i < exprs.length; i++) {
            result = new Binary<>(result, SqlOperator.and(Bijection.asBool()), exprs[i]);
        }
        return result;
    }

    /** Combine multiple boolean expressions with OR. Returns TRUE if any expression is true. */
    @SafeVarargs
    static SqlExpr<Boolean> any(SqlExpr<Boolean>... exprs) {
        if (exprs.length == 0) {
            return new ConstReq<>(false, typo.runtime.PgTypes.bool);
        }
        SqlExpr<Boolean> result = exprs[0];
        for (int i = 1; i < exprs.length; i++) {
            result = new Binary<>(result, SqlOperator.or(Bijection.asBool()), exprs[i]);
        }
        return result;
    }

    // Comparison operators
    default SqlExpr<Boolean> isEqual(SqlExpr<T> other) {
        return new Binary<>(this, SqlOperator.eq(), other);
    }
    
    default SqlExpr<Boolean> isNotEqual(SqlExpr<T> other) {
        return new Binary<>(this, SqlOperator.neq(), other);
    }
    
    default SqlExpr<Boolean> greaterThan(SqlExpr<T> other) {
        return new Binary<>(this, SqlOperator.gt(), other);
    }
    
    default SqlExpr<Boolean> greaterThanOrEqual(SqlExpr<T> other) {
        return new Binary<>(this, SqlOperator.gte(), other);
    }
    
    default SqlExpr<Boolean> lessThan(SqlExpr<T> other) {
        return new Binary<>(this, SqlOperator.lt(), other);
    }
    
    default SqlExpr<Boolean> lessThanOrEqual(SqlExpr<T> other) {
        return new Binary<>(this, SqlOperator.lte(), other);
    }
    
    /**
     * Logical operators for boolean expressions.
     *
     * <p>These methods require a {@link Bijection} to prove that T is compatible with Boolean.
     * For {@code SqlExpr<Boolean>}, use {@link Bijection#asBool()} as the proof:
     *
     * <pre>{@code
     * SqlExpr<Boolean> a = field1.isEqual(value1);
     * SqlExpr<Boolean> b = field2.isEqual(value2);
     *
     * // Combine boolean expressions:
     * SqlExpr<Boolean> combined = a.and(b, Bijection.asBool());
     * SqlExpr<Boolean> either = a.or(b, Bijection.asBool());
     * SqlExpr<Boolean> negated = a.not(Bijection.asBool());
     *
     * // For custom types with boolean semantics, provide a bijection:
     * // SqlExpr<MyBoolWrapper> expr = ...;
     * // expr.not(myBoolWrapperBijection);
     * }</pre>
     */
    default SqlExpr<T> or(SqlExpr<T> other, Bijection<T, Boolean> bijection) {
        return new Binary<>(this, SqlOperator.or(bijection), other);
    }

    default SqlExpr<T> and(SqlExpr<T> other, Bijection<T, Boolean> bijection) {
        return new Binary<>(this, SqlOperator.and(bijection), other);
    }

    default SqlExpr<T> not(Bijection<T, Boolean> bijection) {
        return new Not<>(this, bijection);
    }

    // Arithmetic operators
    default SqlExpr<T> plus(SqlExpr<T> other) {
        return new Binary<>(this, SqlOperator.plus(), other);
    }
    
    default SqlExpr<T> minus(SqlExpr<T> other) {
        return new Binary<>(this, SqlOperator.minus(), other);
    }
    
    default SqlExpr<T> multiply(SqlExpr<T> other) {
        return new Binary<>(this, SqlOperator.mul(), other);
    }
    
    // String operations
    default SqlExpr<Boolean> like(String pattern, Bijection<T, String> bijection) {
        return new Binary<>(this, SqlOperator.like(bijection), new ConstReq<>(pattern, typo.runtime.PgTypes.text));
    }
    
    default SqlExpr<T> stringAppend(SqlExpr<T> other, Bijection<T, String> bijection) {
        return new Binary<>(this, SqlOperator.strAdd(bijection), other);
    }
    
    default SqlExpr<T> lower(Bijection<T, String> bijection) {
        return new Apply1<>(SqlFunction1.lower(bijection), this);
    }
    
    default SqlExpr<T> upper(Bijection<T, String> bijection) {
        return new Apply1<>(SqlFunction1.upper(bijection), this);
    }
    
    default SqlExpr<T> reverse(Bijection<T, String> bijection) {
        return new Apply1<>(SqlFunction1.reverse(bijection), this);
    }
    
    default SqlExpr<Integer> strpos(SqlExpr<String> substring, Bijection<T, String> bijection) {
        return new Apply2<>(SqlFunction2.strpos(bijection), this, substring);
    }
    
    default SqlExpr<Integer> strLength(Bijection<T, String> bijection) {
        return new Apply1<>(SqlFunction1.length(bijection), this);
    }
    
    default SqlExpr<T> substring(SqlExpr<Integer> from, SqlExpr<Integer> count, Bijection<T, String> bijection) {
        return new Apply3<>(SqlFunction3.substring(bijection), this, from, count);
    }
    
    // Null handling
    default SqlExpr<Boolean> isNull() {
        return new IsNull<>(this);
    }
    
    default SqlExpr<T> coalesce(SqlExpr<T> defaultValue) {
        return new Coalesce<>(this, defaultValue);
    }
    
    // Type conversion
    default <TT> SqlExpr<TT> underlying(Bijection<T, TT> bijection) {
        return new Underlying<>(this, bijection);
    }
    
    // Array operations
    default SqlExpr<Boolean> in(T[] values, DbType<T> pgType) {
        return new In<>(this, values, pgType);
    }
    
    // This method should only be available for array types
    // The caller needs to cast to the appropriate type
    
    // Custom operators
    default <T2> SqlExpr<Boolean> customBinaryOp(String op, SqlExpr<T2> right, BiFunction<T, T2, Boolean> eval) {
        return new Binary<>(this, new SqlOperator<>(op, eval), right);
    }
    
    // Rendering
    Fragment render(RenderCtx ctx, AtomicInteger counter);
    
    // Field types
    sealed interface FieldLike<T, R> extends SqlExpr<T> {
        List<Path> path();
        String column();
        default String name() { return column(); }
        Optional<T> get(R row);
        typo.runtime.Either<String, R> set(R row, Optional<T> value);
        Optional<String> sqlReadCast();
        Optional<String> sqlWriteCast();
        DbType<T> pgType();
        
        // Convenience methods for type-safe value comparisons
        default SqlExpr<Boolean> isEqual(T value) {
            return isEqual(new ConstReq<>(value, pgType()));
        }
        
        default SqlExpr<Boolean> isNotEqual(T value) {
            return isNotEqual(new ConstReq<>(value, pgType()));
        }
        
        default SqlExpr<Boolean> greaterThan(T value) {
            return greaterThan(new ConstReq<>(value, pgType()));
        }
        
        default SqlExpr<Boolean> greaterThanOrEqual(T value) {
            return greaterThanOrEqual(new ConstReq<>(value, pgType()));
        }
        
        default SqlExpr<Boolean> lessThan(T value) {
            return lessThan(new ConstReq<>(value, pgType()));
        }
        
        default SqlExpr<Boolean> lessThanOrEqual(T value) {
            return lessThanOrEqual(new ConstReq<>(value, pgType()));
        }
        
        @SuppressWarnings("unchecked")
        default SqlExpr<Boolean> in(T... values) {
            return in(values, pgType());
        }
        
        default SqlExpr<T> coalesce(T defaultValue) {
            return coalesce(new ConstReq<>(defaultValue, pgType()));
        }
        
        // Arithmetic operations
        default SqlExpr<T> plus(T value) {
            return plus(new ConstReq<>(value, pgType()));
        }
        
        default SqlExpr<T> minus(T value) {
            return minus(new ConstReq<>(value, pgType()));
        }
        
        default SqlExpr<T> multiply(T value) {
            return multiply(new ConstReq<>(value, pgType()));
        }
        
        // String operations with value
        default SqlExpr<T> stringAppend(T value, Bijection<T, String> bijection) {
            return stringAppend(new ConstReq<>(value, pgType()), bijection);
        }
        
        // Sort order helpers
        default SortOrder<T> asc() {
            return SortOrder.asc(this);
        }
        
        default SortOrder<T> desc() {
            return SortOrder.desc(this);
        }
    }
    
    sealed interface FieldLikeNotId<T, R> extends FieldLike<T, R> {}
    
    record Field<T, R>(
        List<Path> path,
        String column,
        Function<R, T> get,
        Optional<String> sqlReadCast,
        Optional<String> sqlWriteCast,
        BiFunction<R, T, R> setter,
        DbType<T> pgType
    ) implements FieldLikeNotId<T, R> {
        @Override
        public Optional<T> get(R row) {
            return Optional.of(get.apply(row));
        }

        @Override
        public typo.runtime.Either<String, R> set(R row, Optional<T> value) {
            if (value.isPresent()) {
                return typo.runtime.Either.right(setter.apply(row, value.get()));
            } else {
                return typo.runtime.Either.left("Expected non-null value for " + column());
            }
        }

        @Override
        public Fragment render(RenderCtx ctx, AtomicInteger counter) {
            String colRef = ctx.alias(path())
                .map(alias -> {
                    if (ctx.inJoinContext()) {
                        // In join context, reference CTE output columns: cteName.alias_column
                        // Resolve the actual CTE name that contains this alias's columns
                        String cteName = ctx.resolveCte(alias);
                        return cteName + "." + alias + "_" + column();
                    } else {
                        // In base context, reference actual table columns: (alias)."column"
                        return ctx.dialect().columnRef(alias, ctx.dialect().quoteIdent(column()));
                    }
                })
                .orElse(ctx.dialect().quoteIdent(column()));
            return Fragment.lit(colRef + " ");
        }
    }

    record OptField<T, R>(
        List<Path> path,
        String column,
        Function<R, Optional<T>> get,
        Optional<String> sqlReadCast,
        Optional<String> sqlWriteCast,
        BiFunction<R, Optional<T>, R> setter,
        DbType<T> pgType
    ) implements FieldLikeNotId<T, R> {
        @Override
        public Optional<T> get(R row) {
            return get.apply(row);
        }

        @Override
        public typo.runtime.Either<String, R> set(R row, Optional<T> value) {
            return typo.runtime.Either.right(setter.apply(row, value));
        }

        @Override
        public Fragment render(RenderCtx ctx, AtomicInteger counter) {
            String colRef = ctx.alias(path())
                .map(alias -> {
                    if (ctx.inJoinContext()) {
                        // In join context, reference CTE output columns: cteName.alias_column
                        // Resolve the actual CTE name that contains this alias's columns
                        String cteName = ctx.resolveCte(alias);
                        return cteName + "." + alias + "_" + column();
                    } else {
                        // In base context, reference actual table columns: (alias)."column"
                        return ctx.dialect().columnRef(alias, ctx.dialect().quoteIdent(column()));
                    }
                })
                .orElse(ctx.dialect().quoteIdent(column()));
            return Fragment.lit(colRef + " ");
        }
    }

    record IdField<T, R>(
        List<Path> path,
        String column,
        Function<R, T> get,
        Optional<String> sqlReadCast,
        Optional<String> sqlWriteCast,
        BiFunction<R, T, R> setter,
        DbType<T> pgType
    ) implements FieldLike<T, R> {
        @Override
        public Optional<T> get(R row) {
            return Optional.of(get.apply(row));
        }

        @Override
        public typo.runtime.Either<String, R> set(R row, Optional<T> value) {
            if (value.isPresent()) {
                return typo.runtime.Either.right(setter.apply(row, value.get()));
            } else {
                return typo.runtime.Either.left("Expected non-null value for " + column());
            }
        }

        @Override
        public Fragment render(RenderCtx ctx, AtomicInteger counter) {
            String colRef = ctx.alias(path())
                .map(alias -> {
                    if (ctx.inJoinContext()) {
                        // In join context, reference CTE output columns: cteName.alias_column
                        // Resolve the actual CTE name that contains this alias's columns
                        String cteName = ctx.resolveCte(alias);
                        return cteName + "." + alias + "_" + column();
                    } else {
                        // In base context, reference actual table columns: (alias)."column"
                        return ctx.dialect().columnRef(alias, ctx.dialect().quoteIdent(column()));
                    }
                })
                .orElse(ctx.dialect().quoteIdent(column()));
            return Fragment.lit(colRef + " ");
        }
    }

    // Constant types with DbType
    sealed interface Const<T> extends SqlExpr<T> permits ConstReq, ConstOpt {}

    record ConstReq<T>(T value, DbType<T> pgType) implements Const<T> {
        @Override
        public Fragment render(RenderCtx ctx, AtomicInteger counter) {
            return Fragment.value(value(),  pgType());
        }
    }

    record ConstOpt<T>(Optional<T> value, DbType<T> pgType) implements Const<T> {
        @Override
        public Fragment render(RenderCtx ctx, AtomicInteger counter) {
            return value()
                .<Fragment>map(v -> Fragment.value(v, pgType()))
                .orElse(Fragment.lit("NULL"));
        }
    }
    
    // Function applications
    record Apply1<T1, O>(
        SqlFunction1<T1, O> f,
        SqlExpr<T1> arg1
    ) implements SqlExpr<O> {
        @Override
        public Fragment render(RenderCtx ctx, AtomicInteger counter) {
            Fragment arg = arg1().render(ctx, counter);
            return Fragment.lit(f().name() + "(")
                .append(arg)
                .append(Fragment.lit(")"));
        }
    }
    
    record Apply2<T1, T2, O>(
        SqlFunction2<T1, T2, O> f,
        SqlExpr<T1> arg1,
        SqlExpr<T2> arg2
    ) implements SqlExpr<O> {
        @Override
        public Fragment render(RenderCtx ctx, AtomicInteger counter) {
            Fragment arg1Frag = arg1().render(ctx, counter);
            Fragment arg2Frag = arg2().render(ctx, counter);
            return Fragment.lit(f().name() + "(")
                .append(arg1Frag)
                .append(Fragment.lit(", "))
                .append(arg2Frag)
                .append(Fragment.lit(")"));
        }
    }
    
    record Apply3<T1, T2, T3, O>(
        SqlFunction3<T1, T2, T3, O> f,
        SqlExpr<T1> arg1,
        SqlExpr<T2> arg2,
        SqlExpr<T3> arg3
    ) implements SqlExpr<O> {
        @Override
        public Fragment render(RenderCtx ctx, AtomicInteger counter) {
            Fragment arg1Frag = arg1().render(ctx, counter);
            Fragment arg2Frag = arg2().render(ctx, counter);
            Fragment arg3Frag = arg3().render(ctx, counter);
            return Fragment.lit(f().name() + "(")
                .append(arg1Frag)
                .append(Fragment.lit(", "))
                .append(arg2Frag)
                .append(Fragment.lit(", "))
                .append(arg3Frag)
                .append(Fragment.lit(")"));
        }
    }
    
    // Operators
    record Binary<T1, T2, O>(
        SqlExpr<T1> left,
        SqlOperator<T1, T2, O> op,
        SqlExpr<T2> right
    ) implements SqlExpr<O> {
        @Override
        public Fragment render(RenderCtx ctx, AtomicInteger counter) {
            Fragment leftFrag = left().render(ctx, counter);
            Fragment rightFrag = right().render(ctx, counter);
            return Fragment.lit("(")
                .append(leftFrag)
                .append(Fragment.lit(" " + op().op() + " "))
                .append(rightFrag)
                .append(Fragment.lit(")"));
        }
    }
    
    record Not<T>(
        SqlExpr<T> expr,
        Bijection<T, Boolean> B
    ) implements SqlExpr<T> {
        @Override
        public Fragment render(RenderCtx ctx, AtomicInteger counter) {
            Fragment exprFrag = expr().render(ctx, counter);
            return Fragment.lit("NOT (")
                .append(exprFrag)
                .append(Fragment.lit(")"));
        }
    }
    
    record IsNull<T>(SqlExpr<T> expr) implements SqlExpr<Boolean> {
        @Override
        public Fragment render(RenderCtx ctx, AtomicInteger counter) {
            Fragment exprFrag = expr().render(ctx, counter);
            return Fragment.lit("(")
                .append(exprFrag)
                .append(Fragment.lit(" IS NULL)"));
        }
    }
    
    record Coalesce<T>(
        SqlExpr<T> expr,
        SqlExpr<T> getOrElse
    ) implements SqlExpr<T> {
        @Override
        public Fragment render(RenderCtx ctx, AtomicInteger counter) {
            Fragment exprFrag = expr().render(ctx, counter);
            Fragment defaultExpr = getOrElse().render(ctx, counter);
            return Fragment.lit("COALESCE(")
                .append(exprFrag)
                .append(Fragment.lit(", "))
                .append(defaultExpr)
                .append(Fragment.lit(")"));
        }
    }
    
    record Underlying<T, TT>(
        SqlExpr<T> expr,
        Bijection<T, TT> bijection
    ) implements SqlExpr<TT> {
        @Override
        public Fragment render(RenderCtx ctx, AtomicInteger counter) {
            return expr().render(ctx, counter);
        }
    }
    
    record In<T>(
        SqlExpr<T> expr,
        T[] values,
        DbType<T> pgType
    ) implements SqlExpr<Boolean> {
        @Override
        public Fragment render(RenderCtx ctx, AtomicInteger counter) {
            Fragment exprFrag = expr().render(ctx, counter);
            if (values().length == 0) {
                return Fragment.lit("FALSE");
            }
            
            Fragment result = Fragment.lit("(")
                .append(exprFrag)
                .append(Fragment.lit(" IN ("));
            
            List<Fragment> valueFragments = new ArrayList<>();
            for (T value : values()) {
                valueFragments.add(Fragment.value(value, pgType));
            }
            
            result = result.append(Fragment.comma(valueFragments));
            return result.append(Fragment.lit("))"));
        }
    }
    
    record ArrayIndex<T>(
        SqlExpr<T[]> arr,
        SqlExpr<Integer> idx
    ) implements SqlExpr<T> {
        @Override
        public Fragment render(RenderCtx ctx, AtomicInteger counter) {
            Fragment arrFrag = arr().render(ctx, counter);
            Fragment idxFrag = idx().render(ctx, counter);
            return Fragment.lit("(")
                .append(arrFrag)
                .append(Fragment.lit("["))
                .append(idxFrag)
                .append(Fragment.lit("])"));
        }
    }
    
    record RowExpr(List<SqlExpr<?>> exprs) implements SqlExpr<List<Optional<?>>> {
        @Override
        public Fragment render(RenderCtx ctx, AtomicInteger counter) {
            if (exprs().isEmpty()) {
                return Fragment.lit("ROW()");
            }
            
            List<Fragment> exprFragments = new ArrayList<>();
            for (SqlExpr<?> expr : exprs()) {
                exprFragments.add(expr.render(ctx, counter));
            }
            
            return Fragment.lit("ROW(")
                .append(Fragment.comma(exprFragments))
                .append(Fragment.lit(")"));
        }
    }
    
    public record CompositeIn<Tuple, Row>(
        List<Part<?, Tuple, Row>> parts,
        List<Tuple> tuples
    ) implements SqlExpr<Boolean> {
        
        public record Part<T, Tuple, Row>(
            SqlExpr.FieldLike<T, Row> field,
            Function<Tuple, T> extract,
            DbType<T> pgType
        ) {}
        
        @Override
        public Fragment render(RenderCtx ctx, AtomicInteger counter) {
            if (tuples().isEmpty()) {
                return Fragment.lit("FALSE");
            }
            
            // Render field list
            List<Fragment> fieldFragments = new ArrayList<>();
            for (var part : parts()) {
                fieldFragments.add(part.field().render(ctx, counter));
            }
            
            Fragment fields = Fragment.lit("(")
                .append(Fragment.comma(fieldFragments))
                .append(Fragment.lit(")"));
            
            // Render values
            List<Fragment> tupleFragments = new ArrayList<>();
            for (Tuple tuple : tuples()) {
                Fragment tupleFragment = renderTuple(parts(), tuple);
                tupleFragments.add(tupleFragment);
            }
            
            return fields
                .append(Fragment.lit(" IN ("))
                .append(Fragment.comma(tupleFragments))
                .append(Fragment.lit(")"));
        }
        
        private static <Tuple, Row> Fragment renderTuple(List<Part<?, Tuple, Row>> parts, Tuple tuple) {
            List<Fragment> valueFragments = new ArrayList<>();
            for (var part : parts) {
                valueFragments.add(renderPartValue(part, tuple));
            }
            return Fragment.lit("(")
                .append(Fragment.comma(valueFragments))
                .append(Fragment.lit(")"));
        }
        
        private static <T, Tuple, Row> Fragment renderPartValue(Part<T, Tuple, Row> part, Tuple tuple) {
            T value = part.extract().apply(tuple);
            return Fragment.value(value, part.pgType());
        }
    }
}