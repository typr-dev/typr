package typr.dsl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import typr.data.Json;
import typr.runtime.DbType;
import typr.runtime.Either;
import typr.runtime.Fragment;
import typr.runtime.PgTypes;

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
        SqlExpr.ArrayIndex,
        SqlExpr.RowExpr,
        SqlExpr.InSubquery,
        SqlExpr.Exists,
        SqlExpr.CompositeIn,
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
   * TupleExpr and FieldsExpr produce multiple.
   */
  default int columnCount() {
    return 1;
  }

  /**
   * Returns a flattened list of DbTypes for all columns this expression produces. Most expressions
   * return a single-element list, but TupleExpr and FieldsExpr return one DbType per column,
   * recursively flattening nested multi-column expressions.
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

  default SqlExpr<T> coalesce(SqlExpr<T> defaultValue) {
    return new Coalesce<>(this, defaultValue);
  }

  default <TT> SqlExpr<TT> underlying(Bijection<T, TT> bijection) {
    return new Underlying<>(this, bijection, this.dbType().to(bijection));
  }

  // Array operations
  default SqlExpr<Boolean> in(T[] values, DbType<T> pgType) {
    return new In<>(this, values, pgType);
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

  record In<T>(SqlExpr<T> expr, T[] values, DbType<T> pgType) implements SqlExpr<Boolean> {
    @Override
    public DbType<Boolean> dbType() {
      return PgTypes.bool;
    }

    @Override
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
      Fragment exprFrag = expr().render(ctx, counter);
      if (values().length == 0) {
        return Fragment.lit("FALSE");
      }

      Fragment result = Fragment.lit("(").append(exprFrag).append(Fragment.lit(" IN ("));

      List<Fragment> valueFragments = new ArrayList<>();
      for (T value : values()) {
        valueFragments.add(Fragment.value(value, pgType));
      }

      result = result.append(Fragment.comma(valueFragments));
      return result.append(Fragment.lit("))"));
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

  record CompositeIn<Tuple, Row>(List<Part<?, Tuple, Row>> parts, List<Tuple> tuples)
      implements SqlExpr<Boolean> {

    public record Part<T, Tuple, Row>(
        SqlExpr.FieldLike<T, Row> field, Function<Tuple, T> extract, DbType<T> pgType) {}

    @Override
    public DbType<Boolean> dbType() {
      return PgTypes.bool;
    }

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

      Fragment fields =
          Fragment.lit("(").append(Fragment.comma(fieldFragments)).append(Fragment.lit(")"));

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
      return Fragment.lit("(").append(Fragment.comma(valueFragments)).append(Fragment.lit(")"));
    }

    private static <T, Tuple, Row> Fragment renderPartValue(Part<T, Tuple, Row> part, Tuple tuple) {
      T value = part.extract().apply(tuple);
      return Fragment.value(value, part.pgType());
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
