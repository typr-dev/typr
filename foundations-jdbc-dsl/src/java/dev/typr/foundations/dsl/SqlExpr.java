package dev.typr.foundations.dsl;

import dev.typr.foundations.DbType;
import dev.typr.foundations.Either;
import dev.typr.foundations.Fragment;
import dev.typr.foundations.PgTypes;
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
        SqlExpr.InSubquery,
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
        Tuples.TupleExpr,
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

  /** Combine multiple boolean expressions with AND. Returns TRUE if all expressions are true. */
  @SafeVarargs
  static SqlExpr<Boolean> all(SqlExpr<Boolean>... exprs) {
    if (exprs.length == 0) {
      return new ConstReq<>(true, PgTypes.bool);
    }
    SqlExpr<Boolean> result = exprs[0];
    for (int i = 1; i < exprs.length; i++) {
      result = new Binary<>(result, SqlOperator.and(Bijection.asBool(), PgTypes.bool), exprs[i]);
    }
    return result;
  }

  /** Combine multiple boolean expressions with OR. Returns TRUE if any expression is true. */
  @SafeVarargs
  static SqlExpr<Boolean> any(SqlExpr<Boolean>... exprs) {
    if (exprs.length == 0) {
      return new ConstReq<>(false, PgTypes.bool);
    }
    SqlExpr<Boolean> result = exprs[0];
    for (int i = 1; i < exprs.length; i++) {
      result = new Binary<>(result, SqlOperator.or(Bijection.asBool(), PgTypes.bool), exprs[i]);
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
    return new Binary<>(this, SqlOperator.like(bijection), new ConstReq<>(pattern, PgTypes.text));
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
    return new Underlying<>(this, bijection, this.dbType().to(bijection));
  }

  // Range operations
  default SqlExpr<Boolean> between(SqlExpr<T> low, SqlExpr<T> high) {
    return new Between<>(this, low, high, false);
  }

  default SqlExpr<Boolean> notBetween(SqlExpr<T> low, SqlExpr<T> high) {
    return new Between<>(this, low, high, true);
  }

  // Array operations
  default SqlExpr<Boolean> in(T[] values, DbType<T> pgType) {
    return new In<>(this, Rows.of(this, java.util.Arrays.asList(values)));
  }

  default SqlExpr<Boolean> notIn(T[] values, DbType<T> pgType) {
    return new Not<>(
        new In<>(this, Rows.of(this, java.util.Arrays.asList(values))), Bijection.asBool());
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
  default <T1> Tuples.TupleExpr2<T, T1> tupleWith(SqlExpr<T1> e1) {
    return Tuples.of(this, e1);
  }

  /** Combine this expression with 2 others to create a 3-tuple. */
  default <T1, T2> Tuples.TupleExpr3<T, T1, T2> tupleWith(SqlExpr<T1> e1, SqlExpr<T2> e2) {
    return Tuples.of(this, e1, e2);
  }

  /** Combine this expression with 3 others to create a 4-tuple. */
  default <T1, T2, T3> Tuples.TupleExpr4<T, T1, T2, T3> tupleWith(
      SqlExpr<T1> e1, SqlExpr<T2> e2, SqlExpr<T3> e3) {
    return Tuples.of(this, e1, e2, e3);
  }

  /** Combine this expression with 4 others to create a 5-tuple. */
  default <T1, T2, T3, T4> Tuples.TupleExpr5<T, T1, T2, T3, T4> tupleWith(
      SqlExpr<T1> e1, SqlExpr<T2> e2, SqlExpr<T3> e3, SqlExpr<T4> e4) {
    return Tuples.of(this, e1, e2, e3, e4);
  }

  /** Combine this expression with 5 others to create a 6-tuple. */
  default <T1, T2, T3, T4, T5> Tuples.TupleExpr6<T, T1, T2, T3, T4, T5> tupleWith(
      SqlExpr<T1> e1, SqlExpr<T2> e2, SqlExpr<T3> e3, SqlExpr<T4> e4, SqlExpr<T5> e5) {
    return Tuples.of(this, e1, e2, e3, e4, e5);
  }

  /** Combine this expression with 6 others to create a 7-tuple. */
  default <T1, T2, T3, T4, T5, T6> Tuples.TupleExpr7<T, T1, T2, T3, T4, T5, T6> tupleWith(
      SqlExpr<T1> e1,
      SqlExpr<T2> e2,
      SqlExpr<T3> e3,
      SqlExpr<T4> e4,
      SqlExpr<T5> e5,
      SqlExpr<T6> e6) {
    return Tuples.of(this, e1, e2, e3, e4, e5, e6);
  }

  /** Combine this expression with 7 others to create a 8-tuple. */
  default <T1, T2, T3, T4, T5, T6, T7> Tuples.TupleExpr8<T, T1, T2, T3, T4, T5, T6, T7> tupleWith(
      SqlExpr<T1> e1,
      SqlExpr<T2> e2,
      SqlExpr<T3> e3,
      SqlExpr<T4> e4,
      SqlExpr<T5> e5,
      SqlExpr<T6> e6,
      SqlExpr<T7> e7) {
    return Tuples.of(this, e1, e2, e3, e4, e5, e6, e7);
  }

  /** Combine this expression with 8 others to create a 9-tuple. */
  default <T1, T2, T3, T4, T5, T6, T7, T8>
      Tuples.TupleExpr9<T, T1, T2, T3, T4, T5, T6, T7, T8> tupleWith(
          SqlExpr<T1> e1,
          SqlExpr<T2> e2,
          SqlExpr<T3> e3,
          SqlExpr<T4> e4,
          SqlExpr<T5> e5,
          SqlExpr<T6> e6,
          SqlExpr<T7> e7,
          SqlExpr<T8> e8) {
    return Tuples.of(this, e1, e2, e3, e4, e5, e6, e7, e8);
  }

  /** Combine this expression with 9 others to create a 10-tuple. */
  default <T1, T2, T3, T4, T5, T6, T7, T8, T9>
      Tuples.TupleExpr10<T, T1, T2, T3, T4, T5, T6, T7, T8, T9> tupleWith(
          SqlExpr<T1> e1,
          SqlExpr<T2> e2,
          SqlExpr<T3> e3,
          SqlExpr<T4> e4,
          SqlExpr<T5> e5,
          SqlExpr<T6> e6,
          SqlExpr<T7> e7,
          SqlExpr<T8> e8,
          SqlExpr<T9> e9) {
    return Tuples.of(this, e1, e2, e3, e4, e5, e6, e7, e8, e9);
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
   *     .map(f -> Tuples.of(
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
   * Check if this expression's value is in the result set of a subquery. The subquery should be a
   * projection to a single column of the same type.
   *
   * <p>Example:
   *
   * <pre>{@code
   * personRepo.select()
   *     .where(p -> p.businessentityid().inSubquery(
   *         employeeRepo.select()
   *             .map1(e -> e.businessentityid())
   *     ))
   * }</pre>
   */
  default <F extends Tuples.TupleExpr<R>, R extends Tuples.Tuple> SqlExpr<Boolean> inSubquery(
      SelectBuilder<F, R> subquery) {
    return new InSubquery<>(this, subquery);
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
    return new Binary<>(this, new SqlOperator<>(op, eval, PgTypes.bool), right);
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

    DbType<T> pgType();

    @Override
    default DbType<T> dbType() {
      return pgType();
    }

    /**
     * Render this field reference as SQL. Default implementation for Field, OptField, and IdField.
     */
    @Override
    default Fragment render(RenderCtx ctx, AtomicInteger counter) {
      // Check if this field is a projected expression - if so, use the projected column reference
      Optional<String> projectedRef = ctx.projectedColumnRef(this);
      if (projectedRef.isPresent()) {
        return Fragment.lit(projectedRef.get() + " ");
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
      return Fragment.lit(colRef + " ");
    }

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

    /** Kotlin-friendly alias for {@link #in(Object...)} since 'in' is a reserved keyword. */
    @SuppressWarnings("unchecked")
    default SqlExpr<Boolean> among(T... values) {
      return in(values);
    }

    @SuppressWarnings("unchecked")
    default SqlExpr<Boolean> notIn(T... values) {
      return notIn(values, pgType());
    }

    default SqlExpr<Boolean> between(T low, T high) {
      return between(new ConstReq<>(low, pgType()), new ConstReq<>(high, pgType()));
    }

    default SqlExpr<Boolean> notBetween(T low, T high) {
      return notBetween(new ConstReq<>(low, pgType()), new ConstReq<>(high, pgType()));
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
      List<Path> _path,
      String column,
      Function<R, T> get,
      Optional<String> sqlReadCast,
      Optional<String> sqlWriteCast,
      BiFunction<R, T, R> setter,
      DbType<T> pgType)
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
  }

  record OptField<T, R>(
      List<Path> _path,
      String column,
      Function<R, Optional<T>> get,
      Optional<String> sqlReadCast,
      Optional<String> sqlWriteCast,
      BiFunction<R, Optional<T>, R> setter,
      DbType<T> pgType)
      implements FieldLikeNotId<T, R> {
    @Override
    public Optional<T> get(R row) {
      return get.apply(row);
    }

    @Override
    public Either<String, R> set(R row, Optional<T> value) {
      return Either.right(setter.apply(row, value));
    }
  }

  record IdField<T, R>(
      List<Path> _path,
      String column,
      Function<R, T> get,
      Optional<String> sqlReadCast,
      Optional<String> sqlWriteCast,
      BiFunction<R, T, R> setter,
      DbType<T> pgType)
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
  }

  // Constant types with DbType
  sealed interface Const<T> extends SqlExpr<T> permits ConstReq, ConstOpt {
    DbType<T> pgType();

    @Override
    default DbType<T> dbType() {
      return pgType();
    }
  }

  record ConstReq<T>(T value, DbType<T> pgType) implements Const<T> {
    @Override
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
      return Fragment.value(value(), pgType());
    }
  }

  record ConstOpt<T>(Optional<T> value, DbType<T> pgType) implements Const<T> {
    @Override
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
      return value().<Fragment>map(v -> Fragment.value(v, pgType())).orElse(Fragment.lit("NULL"));
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
      return Fragment.lit(f().name() + "(").append(arg).append(Fragment.lit(")"));
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
      return Fragment.lit(f().name() + "(")
          .append(arg1Frag)
          .append(Fragment.lit(", "))
          .append(arg2Frag)
          .append(Fragment.lit(")"));
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
      return Fragment.lit("(")
          .append(leftFrag)
          .append(Fragment.lit(" " + op().op() + " "))
          .append(rightFrag)
          .append(Fragment.lit(")"));
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
      return Fragment.lit("NOT (").append(exprFrag).append(Fragment.lit(")"));
    }
  }

  record IsNull<T>(SqlExpr<T> expr) implements SqlExpr<Boolean> {
    @Override
    public DbType<Boolean> dbType() {
      return PgTypes.bool;
    }

    @Override
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
      Fragment exprFrag = expr().render(ctx, counter);
      return Fragment.lit("(").append(exprFrag).append(Fragment.lit(" IS NULL)"));
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
      return Fragment.lit("COALESCE(")
          .append(exprFrag)
          .append(Fragment.lit(", "))
          .append(defaultExpr)
          .append(Fragment.lit(")"));
    }
  }

  record Underlying<T, TT>(SqlExpr<T> expr, Bijection<T, TT> bijection, DbType<TT> underlyingType)
      implements SqlExpr<TT> {
    @Override
    public DbType<TT> dbType() {
      return underlyingType();
    }

    @Override
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
      return expr().render(ctx, counter);
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
      return PgTypes.bool;
    }

    @Override
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
      // Check for empty Rows first
      if (rhs() instanceof Rows<?> rows && rows.isEmpty()) {
        // SQL Server doesn't support FALSE literal, use 1=0 instead
        if (ctx.dialect() == Dialect.SQLSERVER) {
          return Fragment.lit("1=0");
        }
        return Fragment.lit("FALSE");
      }

      // Render lhs - wrap in parens if tuple
      Fragment lhsFrag;
      RenderCtx rhsCtx = ctx;
      boolean isTupleIn = false;
      if (lhs() instanceof Tuples.TupleExpr<?> tupleExpr) {
        List<Fragment> exprFragments = new ArrayList<>();
        for (SqlExpr<?> e : tupleExpr.exprs()) {
          exprFragments.add(e.render(ctx, counter));
        }
        lhsFrag = Fragment.lit("(").append(Fragment.comma(exprFragments)).append(Fragment.lit(")"));
        // Pass tuple context to RHS so it can render EXISTS pattern if needed
        rhsCtx = ctx.withTupleInLhs(tupleExpr.exprs());
        isTupleIn = true;
      } else {
        lhsFrag = lhs().render(ctx, counter);
      }

      // Render rhs - it will check context and render EXISTS if needed for SQL Server/DuckDB
      Fragment rhsFrag = rhs().render(rhsCtx, counter);

      // If RHS used EXISTS pattern (for SQL Server/DuckDB tuple IN), return just the EXISTS
      if (isTupleIn
          && rhsCtx.inTupleIn()
          && (ctx.dialect() == Dialect.SQLSERVER || ctx.dialect() == Dialect.DUCKDB)
          && (rhs() instanceof Rows<?> || rhs() instanceof Subquery<?, ?>)) {
        return rhsFrag;
      }

      return lhsFrag.append(Fragment.lit(" IN (")).append(rhsFrag).append(Fragment.lit(")"));
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
    public static <T> Rows<T> of(SqlExpr<T> template, List<T> values) {
      DbType<T> dbType = template.dbType();
      List<SqlExpr<T>> exprs = new ArrayList<>();
      for (T value : values) {
        exprs.add(new ConstReq<>(value, dbType));
      }
      return new Rows<>(exprs);
    }

    /**
     * Create Rows from tuple values, using the template TupleExpr for type info. The values can be
     * any type V that extends the tuple type T (e.g., ID types that implement TupleN).
     */
    public static <T extends Tuples.Tuple, V extends T> Rows<V> ofTuples(
        Tuples.TupleExpr<T> template, List<V> values) {
      // Extract dbTypes from template
      List<DbType<?>> dbTypes = new ArrayList<>();
      for (SqlExpr<?> expr : template.exprs()) {
        dbTypes.add(expr.dbType());
      }
      // Create a ConstTuple for each value
      List<SqlExpr<V>> exprs = new ArrayList<>();
      for (V value : values) {
        exprs.add(new ConstTuple<>(value, dbTypes));
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
        return Fragment.lit("");
      }

      // SQL Server and DuckDB don't support tuple IN syntax - use EXISTS with VALUES instead
      if ((ctx.dialect() == Dialect.SQLSERVER || ctx.dialect() == Dialect.DUCKDB)
          && ctx.inTupleIn()) {
        return renderExistsValues(ctx, counter);
      }

      List<Fragment> fragments = new ArrayList<>();
      for (SqlExpr<T> row : rows) {
        fragments.add(row.render(ctx, counter));
      }
      return Fragment.comma(fragments);
    }

    /**
     * Render using EXISTS with VALUES for SQL Server tuple IN. Returns just the EXISTS expression
     * (caller wraps in parens if needed).
     */
    private Fragment renderExistsValues(RenderCtx ctx, AtomicInteger counter) {
      List<SqlExpr<?>> lhsExprs = ctx.tupleInLhs();
      int numCols = lhsExprs.size();

      // Build VALUES clause: VALUES (?, ?), (?, ?), ...
      List<Fragment> valueRows = new ArrayList<>();
      for (SqlExpr<T> row : rows) {
        valueRows.add(row.render(ctx, counter));
      }
      Fragment valuesClause = Fragment.lit("VALUES ").append(Fragment.comma(valueRows));

      // Build column aliases: c1, c2, ...
      StringBuilder colAliases = new StringBuilder();
      for (int i = 0; i < numCols; i++) {
        if (i > 0) colAliases.append(", ");
        colAliases.append("c").append(i + 1);
      }

      // Build WHERE clause: col1 = v.c1 AND col2 = v.c2 ...
      List<Fragment> conditions = new ArrayList<>();
      for (int i = 0; i < numCols; i++) {
        Fragment colFrag = lhsExprs.get(i).render(ctx, counter);
        conditions.add(colFrag.append(Fragment.lit(" = v.c" + (i + 1))));
      }
      Fragment whereClause = Fragment.and(conditions);

      // Return full EXISTS expression - caller won't wrap in IN ( )
      return Fragment.lit("EXISTS (SELECT 1 FROM (")
          .append(valuesClause)
          .append(Fragment.lit(") AS v(" + colAliases + ") WHERE "))
          .append(whereClause)
          .append(Fragment.lit(")"));
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
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
      Optional<Fragment> sql = selectBuilder.sql();
      if (sql.isEmpty()) {
        throw new UnsupportedOperationException("Subquery requires a SQL-backed SelectBuilder");
      }

      // SQL Server and DuckDB don't support tuple IN subquery - use EXISTS pattern
      if ((ctx.dialect() == Dialect.SQLSERVER || ctx.dialect() == Dialect.DUCKDB)
          && ctx.inTupleIn()) {
        return renderExistsSubquery(ctx, counter, sql.get());
      }

      return sql.get();
    }

    /**
     * Render using EXISTS pattern for SQL Server/DuckDB tuple IN subquery. Returns just the EXISTS
     * expression (caller wraps in parens if needed).
     */
    private Fragment renderExistsSubquery(
        RenderCtx ctx, AtomicInteger counter, Fragment subquerySql) {
      List<SqlExpr<?>> lhsExprs = ctx.tupleInLhs();
      int numCols = lhsExprs.size();

      // Build column aliases: c1, c2, ...
      StringBuilder colAliases = new StringBuilder();
      for (int i = 0; i < numCols; i++) {
        if (i > 0) colAliases.append(", ");
        colAliases.append("c").append(i + 1);
      }

      // Build WHERE clause: col1 = sq.c1 AND col2 = sq.c2 ...
      List<Fragment> conditions = new ArrayList<>();
      for (int i = 0; i < numCols; i++) {
        Fragment colFrag = lhsExprs.get(i).render(ctx, counter);
        conditions.add(colFrag.append(Fragment.lit(" = sq.c" + (i + 1))));
      }
      Fragment whereClause = Fragment.and(conditions);

      // Return full EXISTS expression - caller won't wrap in IN ( )
      return Fragment.lit("EXISTS (SELECT 1 FROM (")
          .append(subquerySql)
          .append(Fragment.lit(") AS sq(" + colAliases + ") WHERE "))
          .append(whereClause)
          .append(Fragment.lit(")"));
    }
  }

  /**
   * A constant tuple value - renders as {@code (v1, v2, ...)}.
   *
   * @param <T> the Tuple type
   */
  record ConstTuple<T extends Tuples.Tuple>(T value, List<DbType<?>> dbTypes)
      implements SqlExpr<T> {

    @Override
    @SuppressWarnings("unchecked")
    public DbType<T> dbType() {
      throw new UnsupportedOperationException("ConstTuple.dbType() - use dbTypes list");
    }

    @Override
    @SuppressWarnings("unchecked")
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
      Object[] arr = value.asArray();
      List<Fragment> parts = new ArrayList<>();
      for (int i = 0; i < arr.length; i++) {
        DbType<Object> dbType = (DbType<Object>) dbTypes.get(i);
        parts.add(Fragment.value(arr[i], dbType));
      }
      return Fragment.lit("(").append(Fragment.comma(parts)).append(Fragment.lit(")"));
    }
  }

  record Between<T>(SqlExpr<T> expr, SqlExpr<T> low, SqlExpr<T> high, boolean negated)
      implements SqlExpr<Boolean> {
    @Override
    public DbType<Boolean> dbType() {
      return PgTypes.bool;
    }

    @Override
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
      Fragment exprFrag = expr().render(ctx, counter);
      Fragment lowFrag = low().render(ctx, counter);
      Fragment highFrag = high().render(ctx, counter);
      String op = negated() ? " NOT BETWEEN " : " BETWEEN ";
      return Fragment.lit("(")
          .append(exprFrag)
          .append(Fragment.lit(op))
          .append(lowFrag)
          .append(Fragment.lit(" AND "))
          .append(highFrag)
          .append(Fragment.lit(")"));
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
      return Fragment.lit("(")
          .append(arrFrag)
          .append(Fragment.lit("["))
          .append(idxFrag)
          .append(Fragment.lit("])"));
    }
  }

  record RowExpr(List<SqlExpr<?>> exprs) implements SqlExpr<List<Optional<?>>> {
    @Override
    @SuppressWarnings("unchecked")
    public DbType<List<Optional<?>>> dbType() {
      // ROW expressions return a composite type - we use a placeholder
      // This isn't commonly used in projections
      return (DbType<List<Optional<?>>>) (DbType<?>) PgTypes.text;
    }

    @Override
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
      if (exprs().isEmpty()) {
        return Fragment.lit("ROW()");
      }

      List<Fragment> exprFragments = new ArrayList<>();
      for (SqlExpr<?> expr : exprs()) {
        exprFragments.add(expr.render(ctx, counter));
      }

      return Fragment.lit("ROW(").append(Fragment.comma(exprFragments)).append(Fragment.lit(")"));
    }
  }

  /** Check if a value is in the result set of a subquery. Renders as: expr IN (SELECT ...) */
  record InSubquery<T, F extends Tuples.TupleExpr<R>, R extends Tuples.Tuple>(
      SqlExpr<T> expr, SelectBuilder<F, R> subquery) implements SqlExpr<Boolean> {
    @Override
    public DbType<Boolean> dbType() {
      return PgTypes.bool;
    }

    @Override
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
      Fragment exprFrag = expr().render(ctx, counter);

      // Wrap tuple expressions in parentheses for correct SQL syntax: (a, b) IN (SELECT...)
      if (expr() instanceof Tuples.TupleExpr<?>) {
        exprFrag = Fragment.lit("(").append(exprFrag).append(Fragment.lit(")"));
      }

      // Get the SQL from the subquery
      Optional<Fragment> subquerySql = subquery().sql();
      if (subquerySql.isEmpty()) {
        throw new UnsupportedOperationException("IN subquery requires a SQL-backed SelectBuilder");
      }

      return Fragment.lit("(")
          .append(exprFrag)
          .append(Fragment.lit(" IN ("))
          .append(subquerySql.get())
          .append(Fragment.lit("))"));
    }
  }

  /** Check if a subquery returns any rows. Renders as: EXISTS (SELECT ...) */
  record Exists<F, R>(SelectBuilder<F, R> subquery) implements SqlExpr<Boolean> {
    @Override
    public DbType<Boolean> dbType() {
      return PgTypes.bool;
    }

    @Override
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
      // Get the SQL from the subquery
      Optional<Fragment> subquerySql = subquery().sql();
      if (subquerySql.isEmpty()) {
        throw new UnsupportedOperationException("EXISTS requires a SQL-backed SelectBuilder");
      }

      return Fragment.lit("EXISTS (").append(subquerySql.get()).append(Fragment.lit(")"));
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
      return Fragment.lit("CASE WHEN ")
          .append(this.predicate().render(ctx, counter))
          .append(Fragment.lit(" THEN "))
          .append(this.expr().render(ctx, counter))
          .append(Fragment.lit(" ELSE NULL END"));
    }
  }

  // ========== Aggregate Functions ==========

  /** COUNT(*) - counts all rows in the group. */
  record CountStar() implements SqlExpr<Long> {
    @Override
    public DbType<Long> dbType() {
      return PgTypes.int8;
    }

    @Override
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
      return Fragment.lit("COUNT(*)");
    }
  }

  /** COUNT(expr) - counts non-null values. */
  record Count<T>(SqlExpr<T> expr) implements SqlExpr<Long> {
    @Override
    public DbType<Long> dbType() {
      return PgTypes.int8;
    }

    @Override
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
      return Fragment.lit("COUNT(").append(expr.render(ctx, counter)).append(Fragment.lit(")"));
    }
  }

  /** COUNT(DISTINCT expr) - counts distinct non-null values. */
  record CountDistinct<T>(SqlExpr<T> expr) implements SqlExpr<Long> {
    @Override
    public DbType<Long> dbType() {
      return PgTypes.int8;
    }

    @Override
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
      return Fragment.lit("COUNT(DISTINCT ")
          .append(expr.render(ctx, counter))
          .append(Fragment.lit(")"));
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
      return Fragment.lit("SUM(").append(expr.render(ctx, counter)).append(Fragment.lit(")"));
    }
  }

  /** AVG(expr) - average of numeric values. Always returns Double. */
  record Avg<T>(SqlExpr<T> expr) implements SqlExpr<Double> {
    @Override
    public DbType<Double> dbType() {
      return PgTypes.float8;
    }

    @Override
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
      return Fragment.lit("AVG(").append(expr.render(ctx, counter)).append(Fragment.lit(")"));
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
      return Fragment.lit("MIN(").append(expr.render(ctx, counter)).append(Fragment.lit(")"));
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
      return Fragment.lit("MAX(").append(expr.render(ctx, counter)).append(Fragment.lit(")"));
    }
  }

  /**
   * STRING_AGG(expr, delimiter) - PostgreSQL GROUP_CONCAT(expr SEPARATOR delimiter) - MariaDB
   * Concatenates string values with a delimiter.
   */
  record StringAgg(SqlExpr<String> expr, String delimiter) implements SqlExpr<String> {
    @Override
    public DbType<String> dbType() {
      return PgTypes.text;
    }

    @Override
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
      if (ctx.dialect() == Dialect.MARIADB) {
        return Fragment.lit("GROUP_CONCAT(")
            .append(expr.render(ctx, counter))
            .append(Fragment.lit(" SEPARATOR "))
            .append(Fragment.value(delimiter, PgTypes.text))
            .append(Fragment.lit(")"));
      } else {
        return Fragment.lit("STRING_AGG(")
            .append(expr.render(ctx, counter))
            .append(Fragment.lit(", "))
            .append(Fragment.value(delimiter, PgTypes.text))
            .append(Fragment.lit(")"));
      }
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
      return Fragment.lit("ARRAY_AGG(").append(expr.render(ctx, counter)).append(Fragment.lit(")"));
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
      return Fragment.lit(fn + "(").append(expr.render(ctx, counter)).append(Fragment.lit(")"));
    }
  }

  /** BOOL_AND(expr) - returns true if all values are true. */
  record BoolAnd(SqlExpr<Boolean> expr) implements SqlExpr<Boolean> {
    @Override
    public DbType<Boolean> dbType() {
      return PgTypes.bool;
    }

    @Override
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
      return Fragment.lit("BOOL_AND(").append(expr.render(ctx, counter)).append(Fragment.lit(")"));
    }
  }

  /** BOOL_OR(expr) - returns true if any value is true. */
  record BoolOr(SqlExpr<Boolean> expr) implements SqlExpr<Boolean> {
    @Override
    public DbType<Boolean> dbType() {
      return PgTypes.bool;
    }

    @Override
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
      return Fragment.lit("BOOL_OR(").append(expr.render(ctx, counter)).append(Fragment.lit(")"));
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
    return new Sum<>(expr, PgTypes.int8);
  }

  /** SUM(expr) for Long - returns Long. */
  static SqlExpr<Long> sumLong(SqlExpr<Long> expr) {
    return new Sum<>(expr, PgTypes.int8);
  }

  /** SUM(expr) for BigDecimal - returns BigDecimal. */
  static SqlExpr<BigDecimal> sumBigDecimal(SqlExpr<BigDecimal> expr) {
    return new Sum<>(expr, PgTypes.numeric);
  }

  /** SUM(expr) for Double - returns Double. */
  static SqlExpr<Double> sumDouble(SqlExpr<Double> expr) {
    return new Sum<>(expr, PgTypes.float8);
  }

  /** SUM(expr) for Short - returns Long. */
  static SqlExpr<Long> sumShort(SqlExpr<Short> expr) {
    return new Sum<>(expr, PgTypes.int8);
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
    return new JsonAgg<>(expr, PgTypes.json);
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
