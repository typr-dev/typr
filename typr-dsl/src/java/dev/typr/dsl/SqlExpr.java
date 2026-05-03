package dev.typr.dsl;

import dev.typr.foundations.Bijection;
import dev.typr.foundations.DbType;
import dev.typr.foundations.Either;
import dev.typr.foundations.Fragment;
import dev.typr.foundations.Tuple;
import dev.typr.foundations.data.Json;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;

public sealed interface SqlExpr<T>
    permits SqlExpr.FieldLike,
        SqlExpr.Const,
        SqlExpr.Apply1,
        SqlExpr.Apply2,
        SqlExpr.Apply3,
        SqlExpr.Binary,
        SqlExpr.Not,
        SqlExpr.IsNull,
        SqlExpr.Coalesce,
        SqlExpr.Underlying,
        SqlExpr.In,
        SqlExpr.Rows,
        SqlExpr.Subquery,
        SqlExpr.ConstTuple,
        SqlExpr.Between,
        SqlExpr.ArrayIndex,
        SqlExpr.RowExpr,
        SqlExpr.Exists,
        SqlExpr.IncludeIf,
        // Aggregate functions
        SqlExpr.CountStar,
        SqlExpr.Count,
        SqlExpr.CountDistinct,
        SqlExpr.Sum,
        SqlExpr.Avg,
        SqlExpr.Min,
        SqlExpr.Max,
        SqlExpr.StringAgg,
        SqlExpr.ArrayAgg,
        SqlExpr.JsonAgg,
        SqlExpr.BoolAnd,
        SqlExpr.BoolOr,
        TupleExpr,
        FieldsExpr {

  /**
   * Returns the database type for this expression's result. Used for row parsing when the
   * expression is projected in a SELECT clause.
   */
  DbType<T> dbType();

  /**
   * Returns the number of columns this expression produces. Most expressions produce 1 column, but
   * TupleExpr produces multiple.
   */
  default int columnCount() {
    return 1;
  }

  /**
   * Returns a flattened list of DbTypes for all columns this expression produces. Most expressions
   * return a single-element list, but TupleExpr returns one DbType per column, recursively
   * flattening nested multi-column expressions.
   */
  default List<DbType<?>> flattenedDbTypes() {
    return List.of(dbType());
  }

  /**
   * Returns the immediate child expressions of this expression. Used for tree traversal to detect
   * properties like nullability. Leaf nodes (constants, fields) return empty list.
   */
  List<SqlExpr<?>> children();

  /**
   * Returns true if this expression might produce NULL values. An expression is nullable if:
   *
   * <ul>
   *   <li>It is an {@link OptField} (nullable column)
   *   <li>It is a {@link ConstOpt} with no value
   *   <li>Any of its children are nullable (for composite expressions)
   *   <li>It is a function/operation that doesn't preserve non-nullability
   * </ul>
   *
   * <p>Default implementation checks if this is an OptField or if any children are nullable.
   * Override for expressions with special nullability semantics.
   */
  default boolean isNullable() {
    // Check children recursively - if any child is nullable, this is nullable
    for (SqlExpr<?> child : children()) {
      if (child.isNullable()) {
        return true;
      }
    }
    return false;
  }

  /** Combine multiple boolean expressions with AND. Returns TRUE if all expressions are true. */
  @SafeVarargs
  static SqlExpr<Boolean> all(SqlExpr<Boolean>... exprs) {
    if (exprs.length == 0) {
      return new ConstReq<>(true, GenericDbTypes.bool);
    }
    SqlExpr<Boolean> result = exprs[0];
    for (int i = 1; i < exprs.length; i++) {
      result =
          new Binary<>(result, SqlOperator.and(Bijection.asBool(), GenericDbTypes.bool), exprs[i]);
    }
    return result;
  }

  /** Combine multiple boolean expressions with OR. Returns TRUE if any expression is true. */
  @SafeVarargs
  static SqlExpr<Boolean> any(SqlExpr<Boolean>... exprs) {
    if (exprs.length == 0) {
      return new ConstReq<>(false, GenericDbTypes.bool);
    }
    SqlExpr<Boolean> result = exprs[0];
    for (int i = 1; i < exprs.length; i++) {
      result =
          new Binary<>(result, SqlOperator.or(Bijection.asBool(), GenericDbTypes.bool), exprs[i]);
    }
    return result;
  }

  /**
   * Recursively unwrap Underlying nodes to find the core expression. This is needed for pattern
   * matching through Scala wrappers that add bijection conversions.
   *
   * @param expr the expression that may be wrapped in Underlying nodes
   * @return the innermost non-Underlying expression
   */
  static SqlExpr<?> unwrapUnderlying(SqlExpr<?> expr) {
    if (expr instanceof Underlying<?, ?> u) {
      return unwrapUnderlying(u.expr());
    }
    return expr;
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
   * <p>These methods require a {@link Bijection} to prove that T is compatible with Boolean. For
   * {@code SqlExpr<Boolean>}, use {@link Bijection#asBool()} as the proof:
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
    return new Binary<>(this, SqlOperator.or(bijection, dbType()), other);
  }

  default SqlExpr<T> and(SqlExpr<T> other, Bijection<T, Boolean> bijection) {
    return new Binary<>(this, SqlOperator.and(bijection, dbType()), other);
  }

  default SqlExpr<T> not(Bijection<T, Boolean> bijection) {
    return new Not<>(this, bijection);
  }

  // Arithmetic operators
  default SqlExpr<T> plus(SqlExpr<T> other) {
    return new Binary<>(this, SqlOperator.plus(dbType()), other);
  }

  default SqlExpr<T> minus(SqlExpr<T> other) {
    return new Binary<>(this, SqlOperator.minus(dbType()), other);
  }

  default SqlExpr<T> multiply(SqlExpr<T> other) {
    return new Binary<>(this, SqlOperator.mul(dbType()), other);
  }

  // String operations
  default SqlExpr<Boolean> like(String pattern, Bijection<T, String> bijection) {
    return new Binary<>(
        this, SqlOperator.like(bijection), new ConstReq<>(pattern, GenericDbTypes.text));
  }

  default SqlExpr<T> stringAppend(SqlExpr<T> other, Bijection<T, String> bijection) {
    return new Binary<>(this, SqlOperator.strAdd(bijection, dbType()), other);
  }

  default SqlExpr<T> lower(Bijection<T, String> bijection) {
    return new Apply1<>(SqlFunction1.lower(bijection, dbType()), this);
  }

  default SqlExpr<T> upper(Bijection<T, String> bijection) {
    return new Apply1<>(SqlFunction1.upper(bijection, dbType()), this);
  }

  default SqlExpr<T> reverse(Bijection<T, String> bijection) {
    return new Apply1<>(SqlFunction1.reverse(bijection, dbType()), this);
  }

  default SqlExpr<Integer> strpos(SqlExpr<String> substring, Bijection<T, String> bijection) {
    return new Apply2<>(SqlFunction2.strpos(bijection), this, substring);
  }

  default SqlExpr<Integer> strLength(Bijection<T, String> bijection) {
    return new Apply1<>(SqlFunction1.length(bijection), this);
  }

  default SqlExpr<T> substring(
      SqlExpr<Integer> from, SqlExpr<Integer> count, Bijection<T, String> bijection) {
    return new Apply3<>(SqlFunction3.substring(bijection, dbType()), this, from, count);
  }

  // Null handling
  default SqlExpr<Boolean> isNull() {
    return new IsNull<>(this);
  }

  default SqlExpr<Boolean> isNotNull() {
    return new Not<>(new IsNull<>(this), Bijection.asBool());
  }

  default SqlExpr<T> coalesce(SqlExpr<T> defaultValue) {
    return new Coalesce<>(this, defaultValue);
  }

  default <TT> SqlExpr<TT> underlying(Bijection<T, TT> bijection) {
    return new Underlying<>(this, bijection);
  }

  // Range operations
  default SqlExpr<Boolean> between(SqlExpr<T> low, SqlExpr<T> high) {
    return new Between<>(this, low, high, false);
  }

  default SqlExpr<Boolean> notBetween(SqlExpr<T> low, SqlExpr<T> high) {
    return new Between<>(this, low, high, true);
  }

  // Array operations
  default SqlExpr<Boolean> in(T... values) {
    return new In<>(this, Rows.of(this, java.util.Arrays.asList(values)));
  }

  default SqlExpr<Boolean> notIn(T... values) {
    return new Not<>(in(values), Bijection.asBool());
  }

  /**
   * Check if this expression's value is in the given collection of values. This is the unified IN
   * method that takes any SqlExpr returning a list.
   */
  default SqlExpr<Boolean> in(SqlExpr<List<T>> rhs) {
    return new In<>(this, rhs);
  }

  /**
   * Check if this expression's value is among the given collection of values. Kotlin-friendly alias
   * for {@link #in(SqlExpr)} since 'in' is a reserved keyword.
   */
  default SqlExpr<Boolean> among(SqlExpr<List<T>> rhs) {
    return in(rhs);
  }

  // ==================== Tuple creation with tupleWith() ====================

  /**
   * Combine this expression with another to create a tuple expression. Use for multi-column IN
   * queries or composite key matching.
   *
   * <pre>{@code
   * d.code().tupleWith(d.region()).in(idList)
   * }</pre>
   */
  default TupleExpr.TupleExpr1<T> tupleWith() {
    return TupleExpr.of(this);
  }

  default <T1> TupleExpr.TupleExpr2<T, T1> tupleWith(SqlExpr<T1> e1) {
    return TupleExpr.of(this, e1);
  }

  /** Combine this expression with 2 others to create a 3-tuple. */
  default <T1, T2> TupleExpr.TupleExpr3<T, T1, T2> tupleWith(SqlExpr<T1> e1, SqlExpr<T2> e2) {
    return TupleExpr.of(this, e1, e2);
  }

  /** Combine this expression with 3 others to create a 4-tuple. */
  default <T1, T2, T3> TupleExpr.TupleExpr4<T, T1, T2, T3> tupleWith(
      SqlExpr<T1> e1, SqlExpr<T2> e2, SqlExpr<T3> e3) {
    return TupleExpr.of(this, e1, e2, e3);
  }

  /** Combine this expression with 4 others to create a 5-tuple. */
  default <T1, T2, T3, T4> TupleExpr.TupleExpr5<T, T1, T2, T3, T4> tupleWith(
      SqlExpr<T1> e1, SqlExpr<T2> e2, SqlExpr<T3> e3, SqlExpr<T4> e4) {
    return TupleExpr.of(this, e1, e2, e3, e4);
  }

  /** Combine this expression with 5 others to create a 6-tuple. */
  default <T1, T2, T3, T4, T5> TupleExpr.TupleExpr6<T, T1, T2, T3, T4, T5> tupleWith(
      SqlExpr<T1> e1, SqlExpr<T2> e2, SqlExpr<T3> e3, SqlExpr<T4> e4, SqlExpr<T5> e5) {
    return TupleExpr.of(this, e1, e2, e3, e4, e5);
  }

  /** Combine this expression with 6 others to create a 7-tuple. */
  default <T1, T2, T3, T4, T5, T6> TupleExpr.TupleExpr7<T, T1, T2, T3, T4, T5, T6> tupleWith(
      SqlExpr<T1> e1,
      SqlExpr<T2> e2,
      SqlExpr<T3> e3,
      SqlExpr<T4> e4,
      SqlExpr<T5> e5,
      SqlExpr<T6> e6) {
    return TupleExpr.of(this, e1, e2, e3, e4, e5, e6);
  }

  /** Combine this expression with 7 others to create a 8-tuple. */
  default <T1, T2, T3, T4, T5, T6, T7>
      TupleExpr.TupleExpr8<T, T1, T2, T3, T4, T5, T6, T7> tupleWith(
          SqlExpr<T1> e1,
          SqlExpr<T2> e2,
          SqlExpr<T3> e3,
          SqlExpr<T4> e4,
          SqlExpr<T5> e5,
          SqlExpr<T6> e6,
          SqlExpr<T7> e7) {
    return TupleExpr.of(this, e1, e2, e3, e4, e5, e6, e7);
  }

  /** Combine this expression with 8 others to create a 9-tuple. */
  default <T1, T2, T3, T4, T5, T6, T7, T8>
      TupleExpr.TupleExpr9<T, T1, T2, T3, T4, T5, T6, T7, T8> tupleWith(
          SqlExpr<T1> e1,
          SqlExpr<T2> e2,
          SqlExpr<T3> e3,
          SqlExpr<T4> e4,
          SqlExpr<T5> e5,
          SqlExpr<T6> e6,
          SqlExpr<T7> e7,
          SqlExpr<T8> e8) {
    return TupleExpr.of(this, e1, e2, e3, e4, e5, e6, e7, e8);
  }

  /** Combine this expression with 9 others to create a 10-tuple. */
  default <T1, T2, T3, T4, T5, T6, T7, T8, T9>
      TupleExpr.TupleExpr10<T, T1, T2, T3, T4, T5, T6, T7, T8, T9> tupleWith(
          SqlExpr<T1> e1,
          SqlExpr<T2> e2,
          SqlExpr<T3> e3,
          SqlExpr<T4> e4,
          SqlExpr<T5> e5,
          SqlExpr<T6> e6,
          SqlExpr<T7> e7,
          SqlExpr<T8> e8,
          SqlExpr<T9> e9) {
    return TupleExpr.of(this, e1, e2, e3, e4, e5, e6, e7, e8, e9);
  }

  /**
   * Conditionally include this expression's value based on a predicate. Returns Optional.of(value)
   * when predicate is true, Optional.empty() when false.
   *
   * <p>Renders as: CASE WHEN predicate THEN expr ELSE NULL END
   *
   * <p>Example:
   *
   * <pre>{@code
   * personRepo.select()
   *     .map(f -> TupleExpr.of(
   *         f.name(),                                    // Required: SqlExpr<String>
   *         f.email().includeIf(f.isActive()),          // Optional: SqlExpr<Optional<String>>
   *         f.salary().includeIf(f.role().isEqual("admin"))  // Optional: SqlExpr<Optional<BigDecimal>>
   *     ))
   * }</pre>
   *
   * @param predicate the condition that determines whether to include the value
   * @return an expression that yields Optional.of(value) when predicate is true, Optional.empty()
   *     otherwise
   */
  default SqlExpr<Optional<T>> includeIf(SqlExpr<Boolean> predicate) {
    return new IncludeIf<>(this, predicate, dbType().opt());
  }

  /**
   * Check if a subquery returns any rows (EXISTS predicate).
   *
   * <p>Example:
   *
   * <pre>{@code
   * personRepo.select()
   *     .where(p -> SqlExpr.exists(
   *         emailRepo.select()
   *             .where(e -> e.businessentityid().isEqual(p.businessentityid()))
   *     ))
   * }</pre>
   */
  static <F, R> SqlExpr<Boolean> exists(SelectBuilder<F, R> subquery) {
    return new Exists<>(subquery);
  }

  /** Check if a subquery returns no rows (NOT EXISTS predicate). */
  static <F, R> SqlExpr<Boolean> notExists(SelectBuilder<F, R> subquery) {
    return new Not<>(new Exists<>(subquery), Bijection.asBool());
  }

  // This method should only be available for array types
  // The caller needs to cast to the appropriate type

  // Custom operators
  default <T2> SqlExpr<Boolean> customBinaryOp(
      String op, SqlExpr<T2> right, BiFunction<T, T2, Boolean> eval) {
    return new Binary<>(this, new SqlOperator<>(op, eval, GenericDbTypes.bool), right);
  }

  // Rendering
  Fragment render(RenderCtx ctx, AtomicInteger counter);

  // Field types
  // Uses _path to avoid conflicts with tables that have a 'path' column
  sealed interface FieldLike<T, R> extends SqlExpr<T>
      permits IdField, FieldLikeNotId, GroupedBuilderSql.SyntheticField {
    List<Path> _path();

    String column();

    default String name() {
      return column();
    }

    Optional<T> get(R row);

    Either<String, R> set(R row, Optional<T> value);

    Optional<String> sqlReadCast();

    Optional<String> sqlWriteCast();

    /**
     * Render this field reference as SQL. Default implementation for Field, OptField, and IdField.
     */
    @Override
    default Fragment render(RenderCtx ctx, AtomicInteger counter) {
      // Check if this field is a projected expression - if so, use the projected column reference
      Optional<String> projectedRef = ctx.projectedColumnRef(this);
      if (projectedRef.isPresent()) {
        return Fragment.of(projectedRef.get() + " ");
      }

      String colRef =
          ctx.alias(_path())
              .map(
                  alias -> {
                    if (ctx.inJoinContext()) {
                      // In join context, reference columns via resolved table alias
                      String tableAlias = ctx.resolveCte(alias);
                      // If alias maps to a different table (CTE), use unique column format
                      if (!tableAlias.equals(alias)) {
                        // Column is in a CTE/composite - use alias_column format
                        return tableAlias + "." + alias + "_" + column();
                      }
                      return tableAlias + "." + ctx.dialect().quoteIdent(column());
                    } else {
                      // In base context, reference actual table columns: (alias)."column"
                      return ctx.dialect().columnRef(alias, ctx.dialect().quoteIdent(column()));
                    }
                  })
              .orElse(ctx.dialect().quoteIdent(column()));
      return Fragment.of(colRef + " ");
    }

    // Convenience methods for type-safe value comparisons
    default SqlExpr<Boolean> isEqual(T value) {
      return isEqual(new ConstReq<>(value, dbType()));
    }

    default SqlExpr<Boolean> isNotEqual(T value) {
      return isNotEqual(new ConstReq<>(value, dbType()));
    }

    default SqlExpr<Boolean> greaterThan(T value) {
      return greaterThan(new ConstReq<>(value, dbType()));
    }

    default SqlExpr<Boolean> greaterThanOrEqual(T value) {
      return greaterThanOrEqual(new ConstReq<>(value, dbType()));
    }

    default SqlExpr<Boolean> lessThan(T value) {
      return lessThan(new ConstReq<>(value, dbType()));
    }

    default SqlExpr<Boolean> lessThanOrEqual(T value) {
      return lessThanOrEqual(new ConstReq<>(value, dbType()));
    }

    @SuppressWarnings("unchecked")
    default SqlExpr<Boolean> in(T... values) {
      return SqlExpr.super.in(values);
    }

    /** Kotlin-friendly alias for {@link #in(Object...)} since 'in' is a reserved keyword. */
    @SuppressWarnings("unchecked")
    default SqlExpr<Boolean> among(T... values) {
      return in(values);
    }

    @SuppressWarnings("unchecked")
    default SqlExpr<Boolean> notIn(T... values) {
      return SqlExpr.super.notIn(values);
    }

    default SqlExpr<Boolean> between(T low, T high) {
      return between(new ConstReq<>(low, dbType()), new ConstReq<>(high, dbType()));
    }

    default SqlExpr<Boolean> notBetween(T low, T high) {
      return notBetween(new ConstReq<>(low, dbType()), new ConstReq<>(high, dbType()));
    }

    default SqlExpr<T> coalesce(T defaultValue) {
      return coalesce(new ConstReq<>(defaultValue, dbType()));
    }

    // Arithmetic operations
    default SqlExpr<T> plus(T value) {
      return plus(new ConstReq<>(value, dbType()));
    }

    default SqlExpr<T> minus(T value) {
      return minus(new ConstReq<>(value, dbType()));
    }

    default SqlExpr<T> multiply(T value) {
      return multiply(new ConstReq<>(value, dbType()));
    }

    // String operations with value
    default SqlExpr<T> stringAppend(T value, Bijection<T, String> bijection) {
      return stringAppend(new ConstReq<>(value, dbType()), bijection);
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
      List<Path> _path,
      String column,
      Function<R, T> get,
      Optional<String> sqlReadCast,
      Optional<String> sqlWriteCast,
      BiFunction<R, T, R> setter,
      DbType<T> dbType)
      implements FieldLikeNotId<T, R> {
    @Override
    public Optional<T> get(R row) {
      return Optional.ofNullable(get.apply(row));
    }

    @Override
    public Either<String, R> set(R row, Optional<T> value) {
      if (value.isPresent()) {
        return Either.right(setter.apply(row, value.get()));
      } else {
        return Either.left("Expected non-null value for " + column());
      }
    }

    @Override
    public List<SqlExpr<?>> children() {
      return List.of();
    }
  }

  record OptField<T, R>(
      List<Path> _path,
      String column,
      Function<R, Optional<T>> get,
      Optional<String> sqlReadCast,
      Optional<String> sqlWriteCast,
      BiFunction<R, Optional<T>, R> setter,
      DbType<T> dbType)
      implements FieldLikeNotId<T, R> {
    @Override
    public Optional<T> get(R row) {
      return get.apply(row);
    }

    @Override
    public Either<String, R> set(R row, Optional<T> value) {
      return Either.right(setter.apply(row, value));
    }

    @Override
    public List<SqlExpr<?>> children() {
      return List.of();
    }

    @Override
    public boolean isNullable() {
      return true; // OptField is always nullable
    }
  }

  record IdField<T, R>(
      List<Path> _path,
      String column,
      Function<R, T> get,
      Optional<String> sqlReadCast,
      Optional<String> sqlWriteCast,
      BiFunction<R, T, R> setter,
      DbType<T> dbType)
      implements FieldLike<T, R> {
    @Override
    public Optional<T> get(R row) {
      return Optional.ofNullable(get.apply(row));
    }

    @Override
    public Either<String, R> set(R row, Optional<T> value) {
      if (value.isPresent()) {
        return Either.right(setter.apply(row, value.get()));
      } else {
        return Either.left("Expected non-null value for " + column());
      }
    }

    @Override
    public List<SqlExpr<?>> children() {
      return List.of();
    }
  }

  // Constant types with DbType
  sealed interface Const<T> extends SqlExpr<T> permits ConstReq, ConstOpt {}

  record ConstReq<T>(T value, DbType<T> dbType) implements Const<T> {
    @Override
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
      return Fragment.value(value(), dbType());
    }

    @Override
    public List<SqlExpr<?>> children() {
      return List.of();
    }
  }

  record ConstOpt<T>(Optional<T> value, DbType<T> dbType) implements Const<T> {
    @Override
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
      return value().<Fragment>map(v -> Fragment.value(v, dbType())).orElse(Fragment.of("NULL"));
    }

    @Override
    public List<SqlExpr<?>> children() {
      return List.of();
    }

    @Override
    public boolean isNullable() {
      return value.isEmpty(); // Nullable when value is empty (NULL)
    }
  }

  // Function applications
  record Apply1<T1, O>(SqlFunction1<T1, O> f, SqlExpr<T1> arg1) implements SqlExpr<O> {
    @Override
    public DbType<O> dbType() {
      return f().outputType();
    }

    @Override
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
      Fragment arg = arg1().render(ctx, counter);
      return Fragment.of(f().name() + "(").append(arg).append(Fragment.of(")"));
    }

    @Override
    public List<SqlExpr<?>> children() {
      return List.of(arg1);
    }
  }

  record Apply2<T1, T2, O>(SqlFunction2<T1, T2, O> f, SqlExpr<T1> arg1, SqlExpr<T2> arg2)
      implements SqlExpr<O> {
    @Override
    public DbType<O> dbType() {
      return f().outputType();
    }

    @Override
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
      Fragment arg1Frag = arg1().render(ctx, counter);
      Fragment arg2Frag = arg2().render(ctx, counter);
      return Fragment.of(f().name() + "(")
          .append(arg1Frag)
          .append(Fragment.of(", "))
          .append(arg2Frag)
          .append(Fragment.of(")"));
    }

    @Override
    public List<SqlExpr<?>> children() {
      return List.of(arg1, arg2);
    }
  }

  record Apply3<T1, T2, T3, O>(
      SqlFunction3<T1, T2, T3, O> f, SqlExpr<T1> arg1, SqlExpr<T2> arg2, SqlExpr<T3> arg3)
      implements SqlExpr<O> {
    @Override
    public DbType<O> dbType() {
      return f().outputType();
    }

    @Override
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
      Fragment arg1Frag = arg1().render(ctx, counter);
      Fragment arg2Frag = arg2().render(ctx, counter);
      Fragment arg3Frag = arg3().render(ctx, counter);
      return Fragment.of(f().name() + "(")
          .append(arg1Frag)
          .append(Fragment.of(", "))
          .append(arg2Frag)
          .append(Fragment.of(", "))
          .append(arg3Frag)
          .append(Fragment.of(")"));
    }

    @Override
    public List<SqlExpr<?>> children() {
      return List.of(arg1, arg2, arg3);
    }
  }

  // Operators
  record Binary<T1, T2, O>(SqlExpr<T1> left, SqlOperator<T1, T2, O> op, SqlExpr<T2> right)
      implements SqlExpr<O> {
    @Override
    public DbType<O> dbType() {
      return op().outputType();
    }

    @Override
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
      Fragment leftFrag = left().render(ctx, counter);
      Fragment rightFrag = right().render(ctx, counter);
      return Fragment.of("(")
          .append(leftFrag)
          .append(Fragment.of(" " + op().op() + " "))
          .append(rightFrag)
          .append(Fragment.of(")"));
    }

    @Override
    public List<SqlExpr<?>> children() {
      return List.of(left, right);
    }
  }

  record Not<T>(SqlExpr<T> expr, Bijection<T, Boolean> B) implements SqlExpr<T> {
    @Override
    public DbType<T> dbType() {
      return expr().dbType();
    }

    @Override
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
      Fragment exprFrag = expr().render(ctx, counter);
      return Fragment.of("NOT (").append(exprFrag).append(Fragment.of(")"));
    }

    @Override
    public List<SqlExpr<?>> children() {
      return List.of(expr);
    }
  }

  record IsNull<T>(SqlExpr<T> expr) implements SqlExpr<Boolean> {
    @Override
    public DbType<Boolean> dbType() {
      return GenericDbTypes.bool;
    }

    @Override
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
      Fragment exprFrag = expr().render(ctx, counter);
      return Fragment.of("(").append(exprFrag).append(Fragment.of(" IS NULL)"));
    }

    @Override
    public List<SqlExpr<?>> children() {
      return List.of(expr);
    }

    @Override
    public boolean isNullable() {
      return false; // IS NULL always returns true/false, never null
    }
  }

  record Coalesce<T>(SqlExpr<T> expr, SqlExpr<T> getOrElse) implements SqlExpr<T> {
    @Override
    public DbType<T> dbType() {
      return expr().dbType();
    }

    @Override
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
      Fragment exprFrag = expr().render(ctx, counter);
      Fragment defaultExpr = getOrElse().render(ctx, counter);
      return Fragment.of("COALESCE(")
          .append(exprFrag)
          .append(Fragment.of(", "))
          .append(defaultExpr)
          .append(Fragment.of(")"));
    }

    @Override
    public List<SqlExpr<?>> children() {
      return List.of(expr, getOrElse);
    }

    @Override
    public boolean isNullable() {
      // COALESCE is only nullable if BOTH arguments are nullable
      return expr.isNullable() && getOrElse.isNullable();
    }
  }

  record Underlying<T, TT>(SqlExpr<T> expr, Bijection<T, TT> bijection) implements SqlExpr<TT> {
    @Override
    public DbType<TT> dbType() {
      return expr.dbType().to(bijection);
    }

    @Override
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
      return expr().render(ctx, counter);
    }

    @Override
    public List<SqlExpr<?>> children() {
      return List.of(expr);
    }
  }

  /**
   * Unified IN expression: {@code lhs IN rhs} where rhs is any source of values.
   *
   * <p>The rhs can be:
   *
   * <ul>
   *   <li>{@link Rows} - inline constant values
   *   <li>{@link SelectBuilder} - a subquery (SelectBuilder extends SqlExpr)
   * </ul>
   *
   * <p>For scalar: {@code expr IN (v1, v2, ...)} or {@code expr IN (SELECT ...)}
   *
   * <p>For tuples: {@code (e1, e2) IN ((v1, v2), ...)} or {@code (e1, e2) IN (SELECT ...)}
   */
  /**
   * IN expression: lhs IN rhs. The type parameters are flexible to allow tuple expressions with ID
   * types that extend the tuple type.
   */
  record In<T, V extends T>(SqlExpr<T> lhs, SqlExpr<List<V>> rhs) implements SqlExpr<Boolean> {

    @Override
    public DbType<Boolean> dbType() {
      return GenericDbTypes.bool;
    }

    @Override
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
      // Unwrap Underlying nodes from Scala wrappers
      SqlExpr<?> unwrappedLhs = unwrapUnderlying(lhs());
      SqlExpr<?> unwrappedRhs = unwrapUnderlying(rhs());

      // Handle tuple IN - delegate to Dialect for full rendering
      if (unwrappedLhs instanceof TupleExpr<?> tupleExpr) {
        List<SqlExpr<?>> tupleChildren = tupleExpr.children();
        List<Fragment> lhsCols = new ArrayList<>();
        for (SqlExpr<?> e : tupleChildren) {
          lhsCols.add(e.render(ctx, counter));
        }
        boolean hasNullable = tupleChildren.stream().anyMatch(SqlExpr::isNullable);

        // Tuple IN with literal Rows
        if (unwrappedRhs instanceof Rows<?> rows) {
          if (rows.isEmpty()) {
            return Fragment.of("1=0");
          }
          List<List<Fragment>> rhsRows = new ArrayList<>();
          for (SqlExpr<?> row : rows.rows()) {
            rhsRows.add(renderTupleRow(row, ctx, counter));
          }
          return ctx.dialect().renderTupleIn(lhsCols, rhsRows, hasNullable);
        }

        // Tuple IN with subquery
        if (unwrappedRhs instanceof Subquery<?, ?> subquery) {
          Optional<Fragment> sql = subquery.selectBuilder().sql();
          if (sql.isEmpty()) {
            throw new UnsupportedOperationException("Subquery requires a SQL-backed SelectBuilder");
          }
          return ctx.dialect().renderTupleInSubquery(lhsCols, sql.get(), hasNullable);
        }
      }

      // Non-tuple IN: simple lhs IN (rhs1, rhs2, ...)
      Fragment lhsFrag = lhs().render(ctx, counter);

      // Check for empty Rows
      if (unwrappedRhs instanceof Rows<?> rows && rows.isEmpty()) {
        return Fragment.of("1=0");
      }

      Fragment rhsFrag = rhs().render(ctx, counter);
      return lhsFrag.append(Fragment.of(" IN (")).append(rhsFrag).append(Fragment.of(")"));
    }

    /** Render a tuple row (ConstTuple or similar) to a list of column fragments. */
    private List<Fragment> renderTupleRow(SqlExpr<?> row, RenderCtx ctx, AtomicInteger counter) {
      SqlExpr<?> unwrapped = unwrapUnderlying(row);
      if (unwrapped instanceof ConstTuple<?> constTuple) {
        Object[] arr = constTuple.value().asArray();
        List<Fragment> cols = new ArrayList<>();
        for (int i = 0; i < arr.length; i++) {
          @SuppressWarnings("unchecked")
          DbType<Object> dbType = (DbType<Object>) constTuple.dbTypes().get(i);
          cols.add(Fragment.value(arr[i], dbType));
        }
        return cols;
      }
      // Fallback: render as single fragment (shouldn't normally happen for tuples)
      return List.of(row.render(ctx, counter));
    }

    @Override
    public List<SqlExpr<?>> children() {
      return List.of(lhs, rhs);
    }

    @Override
    public boolean isNullable() {
      return false; // IN always returns true/false, never null
    }
  }

  /**
   * A collection of row expressions - represents inline constant values for IN clauses.
   *
   * <p>Renders as: {@code (row1), (row2), ...} for tuples or {@code v1, v2, ...} for scalars.
   *
   * @param <T> the row type (can be scalar or Tuple)
   */
  record Rows<T>(List<SqlExpr<T>> rows) implements SqlExpr<List<T>> {

    /** Create Rows from a list of values, using the template expression for type info. */
    @SuppressWarnings("unchecked")
    public static <T> Rows<T> of(SqlExpr<T> template, List<? extends T> values) {
      // For TupleExpr, use flattenedDbTypes() and ConstTuple since dbType() throws
      SqlExpr<?> unwrapped = unwrapUnderlying(template);
      if (unwrapped instanceof TupleExpr<?> tupleExpr) {
        List<DbType<?>> dbTypes = tupleExpr.flattenedDbTypes();
        List<SqlExpr<T>> exprs = new ArrayList<>();
        for (T value : values) {
          exprs.add((SqlExpr<T>) new ConstTuple<>((Tuple) value, dbTypes));
        }
        return new Rows<>(exprs);
      }
      // For regular expressions, use dbType() and ConstReq
      DbType<T> dbType = template.dbType();
      List<SqlExpr<T>> exprs = new ArrayList<>();
      for (T value : values) {
        exprs.add(new ConstReq<>(value, dbType));
      }
      return new Rows<>(exprs);
    }

    public boolean isEmpty() {
      return rows.isEmpty();
    }

    @Override
    @SuppressWarnings("unchecked")
    public DbType<List<T>> dbType() {
      // This is a collection type - not typically used for parsing
      throw new UnsupportedOperationException("Rows.dbType() - use individual row dbTypes");
    }

    @Override
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
      if (rows.isEmpty()) {
        return Fragment.of("");
      }

      List<Fragment> fragments = new ArrayList<>();
      for (SqlExpr<T> row : rows) {
        fragments.add(row.render(ctx, counter));
      }
      return Fragment.comma(fragments);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<SqlExpr<?>> children() {
      return (List<SqlExpr<?>>) (List<?>) rows;
    }
  }

  /**
   * A subquery that produces a list of rows - for use with IN expressions. Renders as just the
   * subquery SQL when used inside IN.
   *
   * @param <F> the Fields type of the subquery
   * @param <R> the Row type (result type) of the subquery
   */
  record Subquery<F, R>(SelectBuilder<F, R> selectBuilder) implements SqlExpr<List<R>> {

    @Override
    @SuppressWarnings("unchecked")
    public DbType<List<R>> dbType() {
      throw new UnsupportedOperationException("Subquery.dbType() - subqueries are used inside IN");
    }

    @Override
    public List<SqlExpr<?>> children() {
      return List.of(); // SelectBuilder is not a SqlExpr child
    }

    @Override
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
      Optional<Fragment> sql = selectBuilder.sql();
      if (sql.isEmpty()) {
        throw new UnsupportedOperationException("Subquery requires a SQL-backed SelectBuilder");
      }
      return sql.get();
    }
  }

  /**
   * A constant tuple value - renders as {@code (v1, v2, ...)}.
   *
   * @param <T> the Tuple type
   */
  record ConstTuple<T extends Tuple>(T value, List<DbType<?>> dbTypes) implements SqlExpr<T> {

    @Override
    @SuppressWarnings("unchecked")
    public DbType<T> dbType() {
      throw new UnsupportedOperationException("ConstTuple.dbType() - use dbTypes list");
    }

    @Override
    @SuppressWarnings("unchecked")
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
      // Flatten nested tuples to match flattenedDbTypes()
      Object[] arr = flattenTupleValues(value);
      List<Fragment> parts = new ArrayList<>();
      for (int i = 0; i < arr.length; i++) {
        Object val = arr[i];
        DbType<Object> dbType = (DbType<Object>) dbTypes.get(i);
        // Handle null and Optional values by rendering as literal NULL
        if (val == null) {
          parts.add(Fragment.of("NULL"));
        } else if (val instanceof java.util.Optional<?> opt) {
          if (opt.isEmpty()) {
            parts.add(Fragment.of("NULL"));
          } else {
            parts.add(Fragment.value(opt.get(), dbType));
          }
        } else if (val instanceof Tuple) {
          // This should never happen if flattenTupleValues works correctly
          throw new IllegalStateException(
              "ConstTuple.render: nested Tuple not flattened! value="
                  + value
                  + ", arr["
                  + i
                  + "]="
                  + val
                  + " (class="
                  + val.getClass().getName()
                  + ")");
        } else {
          parts.add(Fragment.value(val, dbType));
        }
      }
      return Fragment.of("(").append(Fragment.comma(parts)).append(Fragment.of(")"));
    }

    /** Recursively flatten nested Tuple values to match flattenedDbTypes(). */
    private static Object[] flattenTupleValues(Tuple tuple) {
      List<Object> flat = new ArrayList<>();
      for (Object val : tuple.asArray()) {
        if (val instanceof dev.typr.foundations.Tuple nestedTuple) {
          java.util.Collections.addAll(flat, flattenTupleValues(nestedTuple));
        } else {
          flat.add(val);
        }
      }
      return flat.toArray();
    }

    @Override
    public List<SqlExpr<?>> children() {
      return List.of(); // Constant values, no SqlExpr children
    }
  }

  record Between<T>(SqlExpr<T> expr, SqlExpr<T> low, SqlExpr<T> high, boolean negated)
      implements SqlExpr<Boolean> {
    @Override
    public DbType<Boolean> dbType() {
      return GenericDbTypes.bool;
    }

    @Override
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
      Fragment exprFrag = expr().render(ctx, counter);
      Fragment lowFrag = low().render(ctx, counter);
      Fragment highFrag = high().render(ctx, counter);
      String op = negated() ? " NOT BETWEEN " : " BETWEEN ";
      return Fragment.of("(")
          .append(exprFrag)
          .append(Fragment.of(op))
          .append(lowFrag)
          .append(Fragment.of(" AND "))
          .append(highFrag)
          .append(Fragment.of(")"));
    }

    @Override
    public List<SqlExpr<?>> children() {
      return List.of(expr, low, high);
    }

    @Override
    public boolean isNullable() {
      return false; // BETWEEN always returns true/false
    }
  }

  record ArrayIndex<T>(SqlExpr<T[]> arr, SqlExpr<Integer> idx, DbType<T> elementType)
      implements SqlExpr<T> {
    @Override
    public DbType<T> dbType() {
      return elementType();
    }

    @Override
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
      Fragment arrFrag = arr().render(ctx, counter);
      Fragment idxFrag = idx().render(ctx, counter);
      return Fragment.of("(")
          .append(arrFrag)
          .append(Fragment.of("["))
          .append(idxFrag)
          .append(Fragment.of("])"));
    }

    @Override
    public List<SqlExpr<?>> children() {
      return List.of(arr, idx);
    }
  }

  record RowExpr(List<SqlExpr<?>> exprs) implements SqlExpr<List<Optional<?>>> {
    @Override
    @SuppressWarnings("unchecked")
    public DbType<List<Optional<?>>> dbType() {
      // ROW expressions return a composite type - we use a placeholder
      // This isn't commonly used in projections
      return (DbType<List<Optional<?>>>) (DbType<?>) GenericDbTypes.text;
    }

    @Override
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
      if (exprs().isEmpty()) {
        return Fragment.of("ROW()");
      }

      List<Fragment> exprFragments = new ArrayList<>();
      for (SqlExpr<?> expr : exprs()) {
        exprFragments.add(expr.render(ctx, counter));
      }

      return Fragment.of("ROW(").append(Fragment.comma(exprFragments)).append(Fragment.of(")"));
    }

    @Override
    public List<SqlExpr<?>> children() {
      return exprs;
    }
  }

  /** Check if a subquery returns any rows. Renders as: EXISTS (SELECT ...) */
  record Exists<F, R>(SelectBuilder<F, R> subquery) implements SqlExpr<Boolean> {
    @Override
    public DbType<Boolean> dbType() {
      return GenericDbTypes.bool;
    }

    @Override
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
      // Get the SQL from the subquery
      Optional<Fragment> subquerySql = subquery().sql();
      if (subquerySql.isEmpty()) {
        throw new UnsupportedOperationException("EXISTS requires a SQL-backed SelectBuilder");
      }

      return Fragment.of("EXISTS (").append(subquerySql.get()).append(Fragment.of(")"));
    }

    @Override
    public List<SqlExpr<?>> children() {
      return List.of(); // SelectBuilder is not a SqlExpr child
    }

    @Override
    public boolean isNullable() {
      return false; // EXISTS always returns true/false
    }
  }

  /**
   * Conditionally include a value based on a predicate. Renders as: CASE WHEN predicate THEN expr
   * ELSE NULL END
   *
   * @param <T> the type of the inner value
   */
  record IncludeIf<T>(
      SqlExpr<T> expr, SqlExpr<Boolean> predicate, DbType<Optional<T>> optionalDbType)
      implements SqlExpr<Optional<T>> {
    @Override
    public DbType<Optional<T>> dbType() {
      return optionalDbType();
    }

    @Override
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
      return Fragment.of("CASE WHEN ")
          .append(this.predicate().render(ctx, counter))
          .append(Fragment.of(" THEN "))
          .append(this.expr().render(ctx, counter))
          .append(Fragment.of(" ELSE NULL END"));
    }

    @Override
    public List<SqlExpr<?>> children() {
      return List.of(expr, predicate);
    }

    @Override
    public boolean isNullable() {
      return true; // IncludeIf can return NULL when predicate is false
    }
  }

  // ========== Aggregate Functions ==========

  /** COUNT(*) - counts all rows in the group. */
  record CountStar() implements SqlExpr<Long> {
    @Override
    public DbType<Long> dbType() {
      return GenericDbTypes.int8;
    }

    @Override
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
      return Fragment.of("COUNT(*)");
    }

    @Override
    public List<SqlExpr<?>> children() {
      return List.of();
    }

    @Override
    public boolean isNullable() {
      return false; // COUNT always returns a non-null value
    }
  }

  /** COUNT(expr) - counts non-null values. */
  record Count<T>(SqlExpr<T> expr) implements SqlExpr<Long> {
    @Override
    public DbType<Long> dbType() {
      return GenericDbTypes.int8;
    }

    @Override
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
      return Fragment.of("COUNT(").append(expr.render(ctx, counter)).append(Fragment.of(")"));
    }

    @Override
    public List<SqlExpr<?>> children() {
      return List.of(expr);
    }

    @Override
    public boolean isNullable() {
      return false; // COUNT always returns a non-null value
    }
  }

  /** COUNT(DISTINCT expr) - counts distinct non-null values. */
  record CountDistinct<T>(SqlExpr<T> expr) implements SqlExpr<Long> {
    @Override
    public DbType<Long> dbType() {
      return GenericDbTypes.int8;
    }

    @Override
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
      return Fragment.of("COUNT(DISTINCT ")
          .append(expr.render(ctx, counter))
          .append(Fragment.of(")"));
    }

    @Override
    public List<SqlExpr<?>> children() {
      return List.of(expr);
    }

    @Override
    public boolean isNullable() {
      return false; // COUNT always returns a non-null value
    }
  }

  /**
   * SUM(expr) - sum of numeric values. Note: SUM of integers returns Long, SUM of decimals returns
   * BigDecimal.
   */
  record Sum<T, R>(SqlExpr<T> expr, DbType<R> resultType) implements SqlExpr<R> {
    @Override
    public DbType<R> dbType() {
      return resultType;
    }

    @Override
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
      return Fragment.of("SUM(").append(expr.render(ctx, counter)).append(Fragment.of(")"));
    }

    @Override
    public List<SqlExpr<?>> children() {
      return List.of(expr);
    }

    @Override
    public boolean isNullable() {
      return true; // SUM returns NULL when no rows or all values are NULL
    }
  }

  /** AVG(expr) - average of numeric values. Always returns Double. */
  record Avg<T>(SqlExpr<T> expr) implements SqlExpr<Double> {
    @Override
    public DbType<Double> dbType() {
      return GenericDbTypes.float8;
    }

    @Override
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
      return Fragment.of("AVG(").append(expr.render(ctx, counter)).append(Fragment.of(")"));
    }

    @Override
    public List<SqlExpr<?>> children() {
      return List.of(expr);
    }

    @Override
    public boolean isNullable() {
      return true; // AVG returns NULL when no rows or all values are NULL
    }
  }

  /** MIN(expr) - minimum value. */
  record Min<T>(SqlExpr<T> expr, DbType<T> resultType) implements SqlExpr<T> {
    @Override
    public DbType<T> dbType() {
      return resultType;
    }

    @Override
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
      return Fragment.of("MIN(").append(expr.render(ctx, counter)).append(Fragment.of(")"));
    }

    @Override
    public List<SqlExpr<?>> children() {
      return List.of(expr);
    }

    @Override
    public boolean isNullable() {
      return true; // MIN returns NULL when no rows or all values are NULL
    }
  }

  /** MAX(expr) - maximum value. */
  record Max<T>(SqlExpr<T> expr, DbType<T> resultType) implements SqlExpr<T> {
    @Override
    public DbType<T> dbType() {
      return resultType;
    }

    @Override
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
      return Fragment.of("MAX(").append(expr.render(ctx, counter)).append(Fragment.of(")"));
    }

    @Override
    public List<SqlExpr<?>> children() {
      return List.of(expr);
    }

    @Override
    public boolean isNullable() {
      return true; // MAX returns NULL when no rows or all values are NULL
    }
  }

  /**
   * STRING_AGG(expr, delimiter) - PostgreSQL GROUP_CONCAT(expr SEPARATOR delimiter) - MariaDB
   * Concatenates string values with a delimiter.
   */
  record StringAgg(SqlExpr<String> expr, String delimiter) implements SqlExpr<String> {
    @Override
    public DbType<String> dbType() {
      return GenericDbTypes.text;
    }

    @Override
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
      if (ctx.dialect() == Dialect.MARIADB) {
        return Fragment.of("GROUP_CONCAT(")
            .append(expr.render(ctx, counter))
            .append(Fragment.of(" SEPARATOR "))
            .append(Fragment.value(delimiter, GenericDbTypes.text))
            .append(Fragment.of(")"));
      } else {
        return Fragment.of("STRING_AGG(")
            .append(expr.render(ctx, counter))
            .append(Fragment.of(", "))
            .append(Fragment.value(delimiter, GenericDbTypes.text))
            .append(Fragment.of(")"));
      }
    }

    @Override
    public List<SqlExpr<?>> children() {
      return List.of(expr);
    }

    @Override
    public boolean isNullable() {
      return true; // STRING_AGG returns NULL when no rows or all values are NULL
    }
  }

  /** ARRAY_AGG(expr) - collects values into an array. PostgreSQL only. */
  record ArrayAgg<T>(SqlExpr<T> expr, DbType<List<T>> arrayType) implements SqlExpr<List<T>> {
    @Override
    public DbType<List<T>> dbType() {
      return arrayType;
    }

    @Override
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
      return Fragment.of("ARRAY_AGG(").append(expr.render(ctx, counter)).append(Fragment.of(")"));
    }

    @Override
    public List<SqlExpr<?>> children() {
      return List.of(expr);
    }

    @Override
    public boolean isNullable() {
      return true; // ARRAY_AGG returns NULL when no rows
    }
  }

  /**
   * JSON_AGG(expr) - PostgreSQL JSON_ARRAYAGG(expr) - MariaDB Collects values into a JSON array.
   */
  record JsonAgg<T>(SqlExpr<T> expr, DbType<Json> jsonType) implements SqlExpr<Json> {
    @Override
    public DbType<Json> dbType() {
      return jsonType;
    }

    @Override
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
      String fn = ctx.dialect() == Dialect.MARIADB ? "JSON_ARRAYAGG" : "JSON_AGG";
      return Fragment.of(fn + "(").append(expr.render(ctx, counter)).append(Fragment.of(")"));
    }

    @Override
    public List<SqlExpr<?>> children() {
      return List.of(expr);
    }

    @Override
    public boolean isNullable() {
      return true; // JSON_AGG returns NULL when no rows
    }
  }

  /** BOOL_AND(expr) - returns true if all values are true. */
  record BoolAnd(SqlExpr<Boolean> expr) implements SqlExpr<Boolean> {
    @Override
    public DbType<Boolean> dbType() {
      return GenericDbTypes.bool;
    }

    @Override
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
      return Fragment.of("BOOL_AND(").append(expr.render(ctx, counter)).append(Fragment.of(")"));
    }

    @Override
    public List<SqlExpr<?>> children() {
      return List.of(expr);
    }

    @Override
    public boolean isNullable() {
      return true; // BOOL_AND returns NULL when no rows
    }
  }

  /** BOOL_OR(expr) - returns true if any value is true. */
  record BoolOr(SqlExpr<Boolean> expr) implements SqlExpr<Boolean> {
    @Override
    public DbType<Boolean> dbType() {
      return GenericDbTypes.bool;
    }

    @Override
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
      return Fragment.of("BOOL_OR(").append(expr.render(ctx, counter)).append(Fragment.of(")"));
    }

    @Override
    public List<SqlExpr<?>> children() {
      return List.of(expr);
    }

    @Override
    public boolean isNullable() {
      return true; // BOOL_OR returns NULL when no rows
    }
  }

  // ========== Aggregate Factory Methods ==========

  /** COUNT(*) - count all rows. */
  static SqlExpr<Long> count() {
    return new CountStar();
  }

  /** COUNT(expr) - count non-null values. */
  static <T> SqlExpr<Long> count(SqlExpr<T> expr) {
    return new Count<>(expr);
  }

  /** COUNT(DISTINCT expr) - count distinct non-null values. */
  static <T> SqlExpr<Long> countDistinct(SqlExpr<T> expr) {
    return new CountDistinct<>(expr);
  }

  /** SUM(expr) for Integer - returns Long. */
  static SqlExpr<Long> sum(SqlExpr<Integer> expr) {
    return new Sum<>(expr, GenericDbTypes.int8);
  }

  /** SUM(expr) for Long - returns Long. */
  static SqlExpr<Long> sumLong(SqlExpr<Long> expr) {
    return new Sum<>(expr, GenericDbTypes.int8);
  }

  /** SUM(expr) for BigDecimal - returns BigDecimal. */
  static SqlExpr<BigDecimal> sumBigDecimal(SqlExpr<BigDecimal> expr) {
    return new Sum<>(expr, GenericDbTypes.numeric);
  }

  /** SUM(expr) for Double - returns Double. */
  static SqlExpr<Double> sumDouble(SqlExpr<Double> expr) {
    return new Sum<>(expr, GenericDbTypes.float8);
  }

  /** SUM(expr) for Short - returns Long. */
  static SqlExpr<Long> sumShort(SqlExpr<Short> expr) {
    return new Sum<>(expr, GenericDbTypes.int8);
  }

  /** AVG(expr) - average of numeric values, returns Double. */
  static <T extends Number> SqlExpr<Double> avg(SqlExpr<T> expr) {
    return new Avg<>(expr);
  }

  /** MIN(expr) - minimum value, preserves type. */
  static <T> SqlExpr<T> min(SqlExpr<T> expr) {
    return new Min<>(expr, expr.dbType());
  }

  /** MAX(expr) - maximum value, preserves type. */
  static <T> SqlExpr<T> max(SqlExpr<T> expr) {
    return new Max<>(expr, expr.dbType());
  }

  /**
   * STRING_AGG(expr, delimiter) - concatenate strings. Uses STRING_AGG for PostgreSQL, GROUP_CONCAT
   * for MariaDB.
   */
  static SqlExpr<String> stringAgg(SqlExpr<String> expr, String delimiter) {
    return new StringAgg(expr, delimiter);
  }

  /** ARRAY_AGG(expr) - collect values into array. */
  static <T> SqlExpr<List<T>> arrayAgg(SqlExpr<T> expr, DbType<List<T>> arrayType) {
    return new ArrayAgg<>(expr, arrayType);
  }

  /**
   * JSON_AGG(expr) - collect values into JSON array. Uses JSON_AGG for PostgreSQL, JSON_ARRAYAGG
   * for MariaDB.
   */
  static <T> SqlExpr<Json> jsonAgg(SqlExpr<T> expr) {
    return new JsonAgg<>(expr, GenericDbTypes.json);
  }

  /** BOOL_AND(expr) - true if all values are true. */
  static SqlExpr<Boolean> boolAnd(SqlExpr<Boolean> expr) {
    return new BoolAnd(expr);
  }

  /** BOOL_OR(expr) - true if any value is true. */
  static SqlExpr<Boolean> boolOr(SqlExpr<Boolean> expr) {
    return new BoolOr(expr);
  }
}
