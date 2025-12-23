package typr.dsl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Expresses that (tuples of) Fields structures can be joined. This also serves as the type-level
 * connection between Fields and Row.
 *
 * <p>Note: Java doesn't have Scala's tuple types, so we use a Tuple interface to represent joined
 * structures.
 */
public interface Structure<Fields, Row> {
  Fields fields();

  /**
   * Returns all field definitions in this structure. Used for field lookup and SQL column
   * generation.
   */
  List<SqlExpr.FieldLike<?, ?>> allFields();

  // Uses _path to avoid conflicts with tables that have a 'path' column
  List<Path> _path();

  Structure<Fields, Row> withPath(Path _path);

  /**
   * Get the value of a field from a row.
   *
   * <p><b>Mock-only:</b> This method is only used by {@link SelectBuilderMock} for in-memory query
   * evaluation. It is not used by {@link SelectBuilderSql} which generates SQL strings.
   *
   * <p>It's up to you to ensure that the Row in field is the same type as row.
   */
  <T> Optional<T> untypedGetFieldValue(SqlExpr.FieldLike<T, ?> field, Row row);

  /**
   * Evaluate a SQL expression against a row, returning the computed value.
   *
   * <p><b>Mock-only:</b> This method is only used by {@link SelectBuilderMock} for in-memory query
   * evaluation. It provides a visitor-based interpreter for all SqlExpr types, allowing mock
   * repositories to filter, sort, and project rows without a database.
   *
   * <p>{@link SelectBuilderSql} does not use this method - it renders expressions to SQL strings
   * instead.
   *
   * <p>It's up to you to ensure that the Row in field is the same type as row.
   */
  default <T> Optional<T> untypedEval(SqlExpr<T> expr, Row row) {

    SqlExprVisitor<T, Row, Optional<T>> visitor =
        new SqlExprVisitor<T, Row, Optional<T>>() {
          @Override
          public Optional<T> visitFieldLike(SqlExpr.FieldLike<T, Row> field) {
            return untypedGetFieldValue(field, row);
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
            return untypedEval(arrayIndex.arr(), row)
                .flatMap(
                    arr ->
                        untypedEval(arrayIndex.idx(), row)
                            .flatMap(
                                i -> {
                                  T[] array = (T[]) arr;
                                  if (i < 0 || i >= array.length) {
                                    return Optional.empty();
                                  }
                                  return Optional.of(array[i]);
                                }));
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
            return arg1.flatMap(v1 -> arg2.map(v2 -> apply2.f().eval().apply(v1, v2)));
          }

          @Override
          public <A, B, C> Optional<T> visitApply3(SqlExpr.Apply3<A, B, C, T> apply3) {
            Optional<A> arg1 = untypedEval(apply3.arg1(), row);
            Optional<B> arg2 = untypedEval(apply3.arg2(), row);
            Optional<C> arg3 = untypedEval(apply3.arg3(), row);
            return arg1.flatMap(
                v1 -> arg2.flatMap(v2 -> arg3.map(v3 -> apply3.f().eval().apply(v1, v2, v3))));
          }

          @Override
          public <A, B> Optional<T> visitBinary(SqlExpr.Binary<A, B, T> binary) {
            Optional<A> left = untypedEval(binary.left(), row);
            Optional<B> right = untypedEval(binary.right(), row);
            return left.flatMap(v1 -> right.map(v2 -> binary.op().eval().apply(v1, v2)));
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
            return untypedEval(not.expr(), row)
                .map(
                    v -> {
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
          public <Tuple> Optional<T> visitCompositeInExpr(
              SqlExpr.CompositeIn<Tuple, ?> compositeIn) {
            // For CompositeIn expressions, T is always Boolean, so we can safely cast the result
            Boolean result = evaluateCompositeInExpression(Structure.this, compositeIn, row);
            return Optional.of((T) result);
          }

          @Override
          public <F extends Tuples.TupleExpr<R>, R extends Tuples.Tuple>
              Optional<T> visitInSubqueryExpr(SqlExpr.InSubquery<?, F, R> inSubquery) {
            // For InSubquery expressions, T is always Boolean
            Boolean result = evaluateInSubqueryExpression(Structure.this, inSubquery, row);
            return Optional.of((T) result);
          }

          @Override
          public <F, R> Optional<T> visitExistsExpr(SqlExpr.Exists<F, R> exists) {
            // For Exists expressions, T is always Boolean
            Boolean result = evaluateExistsExpression(exists);
            return Optional.of((T) result);
          }

          @Override
          @SuppressWarnings("unchecked")
          public Optional<T> visitTupleExpr(Tuples.TupleExpr<?> tupleExpr) {
            // Evaluate each sub-expression and build the tuple
            return evaluateTupleExpr(Structure.this, tupleExpr, row).map(t -> (T) t);
          }

          @Override
          @SuppressWarnings("unchecked")
          public Optional<T> visitFieldsExpr(FieldsExpr<?> fieldsExpr) {
            // FieldsExpr can be evaluated by getting each column value and building the row
            FieldsExpr<Row> typed = (FieldsExpr<Row>) fieldsExpr;
            var columns = typed.columns();
            Object[] values = new Object[columns.size()];
            for (int i = 0; i < columns.size(); i++) {
              Optional<?> val = untypedGetFieldValue(columns.get(i), row);
              if (val.isEmpty()) {
                // If any column is null, return empty (or handle appropriately)
                values[i] = null;
              } else {
                values[i] = val.get();
              }
            }
            return Optional.of((T) typed.rowParser().decode().apply(values));
          }

          @Override
          @SuppressWarnings("unchecked")
          public <U> Optional<T> visitIncludeIf(SqlExpr.IncludeIf<U> includeIf) {
            // Evaluate the predicate
            Boolean include = untypedEval(includeIf.predicate(), row).orElse(false);
            if (include) {
              // Predicate is true: return Optional.of(value)
              Optional<U> value = untypedEval(includeIf.expr(), row);
              return Optional.of((T) value);
            } else {
              // Predicate is false: return Optional.empty()
              return Optional.of((T) Optional.empty());
            }
          }

          // ========== Aggregate functions ==========
          // These are evaluated by GroupedBuilderMock, not here.
          // Single-row evaluation of aggregates doesn't make sense.

          @Override
          public Optional<T> visitCountStar(SqlExpr.CountStar countStar) {
            throw new UnsupportedOperationException(
                "Aggregate function COUNT(*) cannot be evaluated against a single row");
          }

          @Override
          public <U> Optional<T> visitCount(SqlExpr.Count<U> count) {
            throw new UnsupportedOperationException(
                "Aggregate function COUNT cannot be evaluated against a single row");
          }

          @Override
          public <U> Optional<T> visitCountDistinct(SqlExpr.CountDistinct<U> countDistinct) {
            throw new UnsupportedOperationException(
                "Aggregate function COUNT(DISTINCT) cannot be evaluated against a single row");
          }

          @Override
          public <U, R> Optional<T> visitSum(SqlExpr.Sum<U, R> sum) {
            throw new UnsupportedOperationException(
                "Aggregate function SUM cannot be evaluated against a single row");
          }

          @Override
          public <U> Optional<T> visitAvg(SqlExpr.Avg<U> avg) {
            throw new UnsupportedOperationException(
                "Aggregate function AVG cannot be evaluated against a single row");
          }

          @Override
          public <U> Optional<T> visitMin(SqlExpr.Min<U> min) {
            throw new UnsupportedOperationException(
                "Aggregate function MIN cannot be evaluated against a single row");
          }

          @Override
          public <U> Optional<T> visitMax(SqlExpr.Max<U> max) {
            throw new UnsupportedOperationException(
                "Aggregate function MAX cannot be evaluated against a single row");
          }

          @Override
          public Optional<T> visitStringAgg(SqlExpr.StringAgg stringAgg) {
            throw new UnsupportedOperationException(
                "Aggregate function STRING_AGG cannot be evaluated against a single row");
          }

          @Override
          public <U> Optional<T> visitArrayAgg(SqlExpr.ArrayAgg<U> arrayAgg) {
            throw new UnsupportedOperationException(
                "Aggregate function ARRAY_AGG cannot be evaluated against a single row");
          }

          @Override
          public <U> Optional<T> visitJsonAgg(SqlExpr.JsonAgg<U> jsonAgg) {
            throw new UnsupportedOperationException(
                "Aggregate function JSON_AGG cannot be evaluated against a single row");
          }

          @Override
          public Optional<T> visitBoolAnd(SqlExpr.BoolAnd boolAnd) {
            throw new UnsupportedOperationException(
                "Aggregate function BOOL_AND cannot be evaluated against a single row");
          }

          @Override
          public Optional<T> visitBoolOr(SqlExpr.BoolOr boolOr) {
            throw new UnsupportedOperationException(
                "Aggregate function BOOL_OR cannot be evaluated against a single row");
          }

          @Override
          public Optional<T> visitDefault(SqlExpr<T> expr) {
            throw new UnsupportedOperationException("Unknown expression type: " + expr.getClass());
          }
        };

    return visitor.accept(expr);
  }

  default <Fields2, Row2> Structure<Tuple2<Fields, Fields2>, Tuple2<Row, Row2>> join(
      Structure<Fields2, Row2> other) {
    return new Tupled<>(sharedPrefix(this._path(), other._path()), this, other);
  }

  default <Fields2, Row2> Structure<Tuple2<Fields, Fields2>, Tuple2<Row, Optional<Row2>>> leftJoin(
      Structure<Fields2, Row2> other) {
    return new LeftTupled<>(sharedPrefix(this._path(), other._path()), this, other);
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

  // Implementation for joined structures
  record Tupled<Fields1, Fields2, Row1, Row2>(
      List<Path> path, Structure<Fields1, Row1> left, Structure<Fields2, Row2> right)
      implements Structure<Tuple2<Fields1, Fields2>, Tuple2<Row1, Row2>> {

    @Override
    public Tuple2<Fields1, Fields2> fields() {
      return Tuple2.of(left.fields(), right.fields());
    }

    @Override
    public List<SqlExpr.FieldLike<?, ?>> allFields() {
      List<SqlExpr.FieldLike<?, ?>> cols = new ArrayList<>();
      cols.addAll(left.allFields());
      cols.addAll(right.allFields());
      return cols;
    }

    @Override
    public List<Path> _path() {
      return path;
    }

    @Override
    public Structure<Tuple2<Fields1, Fields2>, Tuple2<Row1, Row2>> withPath(Path newPath) {
      List<Path> newPaths = new ArrayList<>();
      newPaths.add(newPath);
      newPaths.addAll(path);
      return new Tupled<>(newPaths, left.withPath(newPath), right.withPath(newPath));
    }

    @Override
    public <T> Optional<T> untypedGetFieldValue(
        SqlExpr.FieldLike<T, ?> field, Tuple2<Row1, Row2> row) {
      if (containsField(left.allFields(), field)) {
        return left.untypedGetFieldValue(field, row._1());
      } else {
        return right.untypedGetFieldValue(field, row._2());
      }
    }
  }

  // Implementation for left joined structures
  record LeftTupled<Fields1, Fields2, Row1, Row2>(
      List<Path> path, Structure<Fields1, Row1> left, Structure<Fields2, Row2> right)
      implements Structure<Tuple2<Fields1, Fields2>, Tuple2<Row1, Optional<Row2>>> {

    @Override
    public Tuple2<Fields1, Fields2> fields() {
      return Tuple2.of(left.fields(), right.fields());
    }

    @Override
    public List<SqlExpr.FieldLike<?, ?>> allFields() {
      List<SqlExpr.FieldLike<?, ?>> cols = new ArrayList<>();
      cols.addAll(left.allFields());
      cols.addAll(right.allFields());
      return cols;
    }

    @Override
    public List<Path> _path() {
      return path;
    }

    @Override
    public Structure<Tuple2<Fields1, Fields2>, Tuple2<Row1, Optional<Row2>>> withPath(
        Path newPath) {
      List<Path> newPaths = new ArrayList<>();
      newPaths.add(newPath);
      newPaths.addAll(path);
      return new LeftTupled<>(newPaths, left.withPath(newPath), right.withPath(newPath));
    }

    @Override
    public <T> Optional<T> untypedGetFieldValue(
        SqlExpr.FieldLike<T, ?> field, Tuple2<Row1, Optional<Row2>> row) {
      if (containsField(left.allFields(), field)) {
        return left.untypedGetFieldValue(field, row._1());
      } else {
        return row._2().flatMap(r2 -> right.untypedGetFieldValue(field, r2));
      }
    }
  }

  /**
   * Mock-only: Check if a field exists in a columns list by path and column name. This is needed
   * because Field records contain Function/BiFunction which don't have structural equality. Used by
   * Tupled and LeftTupled to route field lookups to the correct side of a join.
   */
  private static boolean containsField(
      List<? extends SqlExpr.FieldLike<?, ?>> columns, SqlExpr.FieldLike<?, ?> field) {
    for (var col : columns) {
      if (col._path().equals(field._path()) && col.column().equals(field.column())) {
        return true;
      }
    }
    return false;
  }

  // ========================================================================
  // Helper methods for mock expression evaluation
  // All methods below are only used by untypedEval() for in-memory query execution.
  // They are not used by SelectBuilderSql which generates SQL strings.
  // ========================================================================

  /** Mock-only: Helper for evaluating IN expressions. */
  private static <T, Row> Optional<Boolean> evaluateInExpressionHelper(
      Structure<?, Row> structure, SqlExpr.In<T> in, Row row) {
    return structure
        .untypedEval(in.expr(), row)
        .map(
            v -> {
              for (T value : in.values()) {
                if (v.equals(value)) {
                  return Boolean.TRUE;
                }
              }
              return Boolean.FALSE;
            });
  }

  /** Mock-only: Helper for evaluating IS NULL expressions. */
  private static <Row> Boolean evaluateIsNullExpression(
      Structure<?, Row> structure, SqlExpr.IsNull<?> isNull, Row row) {
    return structure.untypedEval(isNull.expr(), row).isEmpty();
  }

  /** Mock-only: Helper for evaluating composite IN expressions (multiple columns). */
  private static <Row, Tuple> Boolean evaluateCompositeInExpression(
      Structure<?, Row> structure, SqlExpr.CompositeIn<Tuple, ?> compositeIn, Row row) {
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

  /** Mock-only: Helper for extracting values from composite IN tuples. */
  private static <T, Tuple, Row> T extractPartValue(
      SqlExpr.CompositeIn.Part<T, Tuple, Row> part, Tuple tuple) {
    return part.extract().apply(tuple);
  }

  /**
   * Mock-only: Evaluate an IN subquery expression against a row. Returns true if the expression
   * value is in the subquery results.
   */
  private static <T, F extends Tuples.TupleExpr<R>, R extends Tuples.Tuple, Row>
      Boolean evaluateInSubqueryExpression(
          Structure<?, Row> structure, SqlExpr.InSubquery<T, F, R> inSubquery, Row row) {
    // Evaluate the expression to get the value we're looking for
    Optional<T> exprValue = structure.untypedEval(inSubquery.expr(), row);
    if (exprValue.isEmpty()) {
      return Boolean.FALSE;
    }
    T searchValue = exprValue.get();

    // Execute the subquery to get all values
    // For mock queries, we pass null for the connection since mock doesn't use it
    List<R> subqueryResults = inSubquery.subquery().toList(null);

    // Check if any result matches
    for (R result : subqueryResults) {
      // The subquery should return single-column tuples, get the first value
      Object[] values = result.asArray();
      if (values.length > 0 && searchValue.equals(values[0])) {
        return Boolean.TRUE;
      }
    }
    return Boolean.FALSE;
  }

  /**
   * Mock-only: Evaluate an EXISTS expression. Returns true if the subquery returns at least one
   * row.
   */
  private static <F, R> Boolean evaluateExistsExpression(SqlExpr.Exists<F, R> exists) {
    // Execute the subquery and check if any rows exist
    // For mock queries, we pass null for the connection since mock doesn't use it
    List<R> subqueryResults = exists.subquery().toList(null);
    return !subqueryResults.isEmpty();
  }

  /**
   * Mock-only: Evaluate a TupleExpr by evaluating all its sub-expressions and constructing the
   * corresponding Tuple.
   */
  private static <Row> Optional<Tuples.Tuple> evaluateTupleExpr(
      Structure<?, Row> structure, Tuples.TupleExpr<?> tupleExpr, Row row) {
    List<SqlExpr<?>> exprs = tupleExpr.exprs();
    Object[] values = new Object[exprs.size()];

    for (int i = 0; i < exprs.size(); i++) {
      Optional<?> value = structure.untypedEval(exprs.get(i), row);
      if (value.isEmpty()) {
        return Optional.empty();
      }
      values[i] = value.get();
    }

    // Create the appropriate tuple based on arity
    return Optional.of(Tuples.createTuple(values));
  }
}
