package typr.dsl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import typr.runtime.Fragment;

/**
 * Mock implementation of GroupedBuilder for in-memory testing.
 *
 * <p>Evaluates GROUP BY and aggregate functions against in-memory data, allowing tests to run
 * without a database.
 */
public class GroupedBuilderMock<Fields, Row> implements GroupedBuilder<Fields, Row> {

  private final SelectBuilderMock<Fields, Row> source;
  private final Function<Fields, List<SqlExpr<?>>> groupByExprs;
  private final List<Function<Fields, SqlExpr<Boolean>>> havingPredicates;

  public GroupedBuilderMock(
      SelectBuilderMock<Fields, Row> source, Function<Fields, List<SqlExpr<?>>> groupByExprs) {
    this.source = source;
    this.groupByExprs = groupByExprs;
    this.havingPredicates = List.of();
  }

  private GroupedBuilderMock(
      SelectBuilderMock<Fields, Row> source,
      Function<Fields, List<SqlExpr<?>>> groupByExprs,
      List<Function<Fields, SqlExpr<Boolean>>> havingPredicates) {
    this.source = source;
    this.groupByExprs = groupByExprs;
    this.havingPredicates = havingPredicates;
  }

  @Override
  public GroupedBuilder<Fields, Row> having(Function<Fields, SqlExpr<Boolean>> predicate) {
    var newHaving = new ArrayList<>(havingPredicates);
    newHaving.add(predicate);
    return new GroupedBuilderMock<>(source, groupByExprs, List.copyOf(newHaving));
  }

  @Override
  public <NewFields extends Tuples.TupleExpr<NewRow>, NewRow extends Tuples.Tuple>
      SelectBuilder<NewFields, NewRow> select(Function<Fields, NewFields> projection) {
    return new GroupedSelectBuilderMock<>(
        source, groupByExprs, havingPredicates, projection, SelectParams.empty());
  }

  @Override
  public Optional<Fragment> sql() {
    return Optional.empty(); // Mock doesn't generate SQL
  }

  /** SelectBuilder implementation that evaluates grouped queries in memory. */
  record GroupedSelectBuilderMock<
          Fields, Row, NewFields extends Tuples.TupleExpr<NewRow>, NewRow extends Tuples.Tuple>(
      SelectBuilderMock<Fields, Row> source,
      Function<Fields, List<SqlExpr<?>>> groupByExprs,
      List<Function<Fields, SqlExpr<Boolean>>> havingPredicates,
      Function<Fields, NewFields> projection,
      SelectParams<NewFields, NewRow> params)
      implements SelectBuilder<NewFields, NewRow> {

    @Override
    public RenderCtx renderCtx() {
      return RenderCtx.of(Dialect.POSTGRESQL);
    }

    @Override
    public Structure<NewFields, NewRow> structure() {
      NewFields tupleExpr = projection.apply(source.structure().fields());
      return new GroupedMockStructure<>(tupleExpr, tupleExpr.exprs());
    }

    @Override
    public SelectParams<NewFields, NewRow> params() {
      return params;
    }

    @Override
    public SelectBuilder<NewFields, NewRow> withParams(SelectParams<NewFields, NewRow> newParams) {
      return new GroupedSelectBuilderMock<>(
          source, groupByExprs, havingPredicates, projection, newParams);
    }

    @Override
    public List<NewRow> toList(Connection connection) {
      // 1. Get all source rows (with WHERE applied)
      List<Row> allRows = source.toList(connection);
      Structure<Fields, Row> structure = source.structure();
      Fields fields = structure.fields();

      // 2. Group rows by key
      Map<List<Object>, List<Row>> groups = new LinkedHashMap<>();
      List<SqlExpr<?>> keyExprs = groupByExprs.apply(fields);

      for (Row row : allRows) {
        List<Object> key = evaluateGroupKey(structure, keyExprs, row);
        groups.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
      }

      // 3. For each group, evaluate HAVING and projection
      NewFields projectedFields = projection.apply(fields);
      List<SqlExpr<?>> projectedExprs = projectedFields.exprs();
      List<NewRow> results = new ArrayList<>();

      for (var entry : groups.entrySet()) {
        List<Row> groupRows = entry.getValue();

        // Check HAVING predicates
        if (!havingPredicates.isEmpty()) {
          boolean passesHaving = true;
          for (var predFn : havingPredicates) {
            SqlExpr<Boolean> pred = predFn.apply(fields);
            Boolean passes = evaluateAggregate(structure, pred, groupRows);
            if (passes == null || !passes) {
              passesHaving = false;
              break;
            }
          }
          if (!passesHaving) {
            continue;
          }
        }

        // Evaluate projection
        Object[] values = new Object[projectedExprs.size()];
        for (int i = 0; i < projectedExprs.size(); i++) {
          SqlExpr<?> expr = projectedExprs.get(i);
          values[i] = evaluateAggregate(structure, expr, groupRows);
        }

        @SuppressWarnings("unchecked")
        NewRow tuple = (NewRow) Tuples.createTuple(values);
        results.add(tuple);
      }

      // 4. Apply ORDER BY, OFFSET, LIMIT from params
      return SelectBuilderMock.applyParams(structure(), results, params);
    }

    @Override
    public int count(Connection connection) {
      return toList(connection).size();
    }

    @Override
    public Optional<Fragment> sql() {
      return Optional.empty();
    }

    /**
     * Convert this grouped builder to a regular SelectBuilderMock by materializing results. This
     * enables joins and other operations on grouped results.
     */
    private SelectBuilderMock<NewFields, NewRow> toSelectBuilderMock() {
      Supplier<List<NewRow>> rowSupplier = () -> this.toList(null);
      GroupedMockStructure<NewFields, NewRow> groupedStructure =
          new GroupedMockStructure<>(structure().fields(), structure().fields().exprs());
      return new SelectBuilderMock<>(groupedStructure, rowSupplier, SelectParams.empty());
    }

    @Override
    public <Fields2, Row2> SelectBuilder<Tuple2<NewFields, Fields2>, Tuple2<NewRow, Row2>> joinOn(
        SelectBuilder<Fields2, Row2> other,
        Function<Tuple2<NewFields, Fields2>, SqlExpr<Boolean>> pred) {
      // Convert to regular SelectBuilderMock and delegate
      return toSelectBuilderMock().joinOn(other, pred);
    }

    @Override
    public <Fields2, Row2>
        SelectBuilder<Tuple2<NewFields, Fields2>, Tuple2<NewRow, Optional<Row2>>> leftJoinOn(
            SelectBuilder<Fields2, Row2> other,
            Function<Tuple2<NewFields, Fields2>, SqlExpr<Boolean>> pred) {
      // Convert to regular SelectBuilderMock and delegate
      return toSelectBuilderMock().leftJoinOn(other, pred);
    }

    @Override
    public <Fields2, Row2>
        SelectBuilder<Tuple2<NewFields, Fields2>, Tuple2<NewRow, List<Row2>>> multisetOn(
            SelectBuilder<Fields2, Row2> other,
            Function<Tuple2<NewFields, Fields2>, SqlExpr<Boolean>> pred) {
      // Convert to regular SelectBuilderMock and delegate
      return toSelectBuilderMock().multisetOn(other, pred);
    }

    @Override
    public GroupedBuilder<NewFields, NewRow> groupByExpr(
        Function<NewFields, List<SqlExpr<?>>> groupKeys) {
      // Convert to regular SelectBuilderMock and delegate - allows grouping grouped results
      return toSelectBuilderMock().groupByExpr(groupKeys);
    }

    @Override
    public <NewFields2 extends Tuples.TupleExpr<NewRow2>, NewRow2 extends Tuples.Tuple>
        SelectBuilder<NewFields2, NewRow2> mapExpr(Function<NewFields, NewFields2> projection) {
      // Allow mapping on grouped results
      Supplier<List<NewRow>> rowSupplier = () -> this.toList(null);
      SelectBuilderMock.ProjectedMockStructure<NewFields, NewRow> projectedStructure =
          new SelectBuilderMock.ProjectedMockStructure<>(
              structure().fields(), structure().fields().exprs());

      SelectBuilderMock<NewFields, NewRow> mockBuilder =
          new SelectBuilderMock<>(projectedStructure, rowSupplier, SelectParams.empty());
      return mockBuilder.mapExpr(projection);
    }

    /** Evaluate the group key for a row. */
    private List<Object> evaluateGroupKey(
        Structure<Fields, Row> structure, List<SqlExpr<?>> keyExprs, Row row) {
      List<Object> key = new ArrayList<>();
      for (SqlExpr<?> expr : keyExprs) {
        Optional<?> value = structure.untypedEval(expr, row);
        key.add(value.orElse(null));
      }
      return key;
    }

    /** Evaluate an expression (potentially an aggregate) against a group of rows. */
    @SuppressWarnings("unchecked")
    private <T> T evaluateAggregate(
        Structure<Fields, Row> structure, SqlExpr<T> expr, List<Row> groupRows) {
      return switch (expr) {
        case SqlExpr.CountStar c -> (T) Long.valueOf(groupRows.size());

        case SqlExpr.Count<?> c -> {
          long count = 0;
          for (Row row : groupRows) {
            if (structure.untypedEval(c.expr(), row).isPresent()) {
              count++;
            }
          }
          yield (T) Long.valueOf(count);
        }

        case SqlExpr.CountDistinct<?> cd -> {
          Set<Object> seen = new HashSet<>();
          for (Row row : groupRows) {
            Optional<?> val = structure.untypedEval(cd.expr(), row);
            if (val.isPresent()) {
              seen.add(val.get());
            }
          }
          yield (T) Long.valueOf(seen.size());
        }

        case SqlExpr.Sum<?, ?> s -> {
          BigDecimal sum = BigDecimal.ZERO;
          for (Row row : groupRows) {
            Optional<?> val = structure.untypedEval(s.expr(), row);
            if (val.isPresent() && val.get() instanceof Number n) {
              sum = sum.add(new BigDecimal(n.toString()));
            }
          }
          yield (T) convertSumToObject(sum, s.resultType());
        }

        case SqlExpr.Avg<?> a -> {
          BigDecimal sum = BigDecimal.ZERO;
          long count = 0;
          for (Row row : groupRows) {
            Optional<?> val = structure.untypedEval(a.expr(), row);
            if (val.isPresent() && val.get() instanceof Number n) {
              sum = sum.add(new BigDecimal(n.toString()));
              count++;
            }
          }
          yield (T)
              (count == 0
                  ? null
                  : sum.divide(BigDecimal.valueOf(count), 10, RoundingMode.HALF_UP).doubleValue());
        }

        case SqlExpr.Min<?> m -> {
          Comparable min = null;
          for (Row row : groupRows) {
            Optional<?> val = structure.untypedEval(m.expr(), row);
            if (val.isPresent()) {
              Comparable v = (Comparable) val.get();
              if (min == null || v.compareTo(min) < 0) {
                min = v;
              }
            }
          }
          yield (T) min;
        }

        case SqlExpr.Max<?> mx -> {
          Comparable max = null;
          for (Row row : groupRows) {
            Optional<?> val = structure.untypedEval(mx.expr(), row);
            if (val.isPresent()) {
              Comparable v = (Comparable) val.get();
              if (max == null || v.compareTo(max) > 0) {
                max = v;
              }
            }
          }
          yield (T) max;
        }

        case SqlExpr.StringAgg sa -> {
          List<String> values = new ArrayList<>();
          for (Row row : groupRows) {
            Optional<?> val = structure.untypedEval(sa.expr(), row);
            if (val.isPresent()) {
              values.add((String) val.get());
            }
          }
          yield (T) String.join(sa.delimiter(), values);
        }

        case SqlExpr.ArrayAgg<?> aa -> {
          List<Object> values = new ArrayList<>();
          for (Row row : groupRows) {
            Optional<?> val = structure.untypedEval(aa.expr(), row);
            if (val.isPresent()) {
              values.add(val.get());
            }
          }
          yield (T) values;
        }

        case SqlExpr.BoolAnd ba -> {
          for (Row row : groupRows) {
            Optional<?> val = structure.untypedEval(ba.expr(), row);
            if (val.isEmpty() || !((Boolean) val.get())) {
              yield (T) Boolean.FALSE;
            }
          }
          yield (T) Boolean.TRUE;
        }

        case SqlExpr.BoolOr bo -> {
          for (Row row : groupRows) {
            Optional<?> val = structure.untypedEval(bo.expr(), row);
            if (val.isPresent() && ((Boolean) val.get())) {
              yield (T) Boolean.TRUE;
            }
          }
          yield (T) Boolean.FALSE;
        }

        // Binary expressions (for HAVING predicates like count > 5)
        case SqlExpr.Binary<?, ?, ?> binary -> {
          Object leftVal = evaluateAggregate(structure, binary.left(), groupRows);
          Object rightVal = evaluateAggregate(structure, binary.right(), groupRows);
          @SuppressWarnings("unchecked")
          java.util.function.BiFunction<Object, Object, Object> evalFn =
              (java.util.function.BiFunction<Object, Object, Object>) binary.op().eval();
          yield (T) evalFn.apply(leftVal, rightVal);
        }

        // Constants
        case SqlExpr.ConstReq<?> c -> (T) c.value();
        case SqlExpr.ConstOpt<?> c -> (T) c.value().orElse(null);

        // For non-aggregate expressions (like GROUP BY columns), use the first row
        default -> {
          if (groupRows.isEmpty()) yield null;
          yield structure.untypedEval(expr, groupRows.getFirst()).orElse(null);
        }
      };
    }

    /**
     * Convert a BigDecimal to the appropriate numeric type based on the result type. Uses the
     * PgTypes constants to determine the target type. Returns Object to make type inference
     * simpler.
     */
    private static Object convertSumToObject(BigDecimal value, typr.runtime.DbType<?> targetType) {
      // Check for common numeric types by comparing to known PgTypes
      if (targetType == typr.runtime.PgTypes.int8) {
        return Long.valueOf(value.longValue());
      } else if (targetType == typr.runtime.PgTypes.int4) {
        return Integer.valueOf(value.intValue());
      } else if (targetType == typr.runtime.PgTypes.int2) {
        return Short.valueOf(value.shortValue());
      } else if (targetType == typr.runtime.PgTypes.float8) {
        return Double.valueOf(value.doubleValue());
      } else if (targetType == typr.runtime.PgTypes.float4) {
        return Float.valueOf(value.floatValue());
      } else if (targetType == typr.runtime.PgTypes.numeric) {
        return value;
      } else {
        // Default - return the BigDecimal and let the caller handle it
        return value;
      }
    }
  }

  /**
   * Structure implementation for grouped query results. Supports joins by properly implementing
   * withPath and field access.
   */
  record GroupedMockStructure<
          NewFields extends Tuples.TupleExpr<NewRow>, NewRow extends Tuples.Tuple>(
      NewFields fields, List<SqlExpr<?>> projectedExprs, List<Path> path)
      implements Structure<NewFields, NewRow> {

    GroupedMockStructure(NewFields fields, List<SqlExpr<?>> projectedExprs) {
      this(fields, projectedExprs, List.of());
    }

    @Override
    public List<SqlExpr.FieldLike<?, ?>> allFields() {
      List<SqlExpr.FieldLike<?, ?>> result = new ArrayList<>();
      for (SqlExpr<?> expr : projectedExprs) {
        if (expr instanceof SqlExpr.FieldLike<?, ?> f) {
          result.add(f);
        }
      }
      return result;
    }

    @Override
    public List<Path> _path() {
      return path;
    }

    @Override
    public Structure<NewFields, NewRow> withPath(Path newPath) {
      List<Path> newPaths = new ArrayList<>();
      newPaths.add(newPath);
      newPaths.addAll(path);
      return new GroupedMockStructure<>(fields, projectedExprs, newPaths);
    }

    @Override
    public <T> Optional<T> untypedGetFieldValue(SqlExpr.FieldLike<T, ?> field, NewRow row) {
      Object[] values = row.asArray();
      for (int i = 0; i < projectedExprs.size(); i++) {
        SqlExpr<?> expr = projectedExprs.get(i);
        if (expr instanceof SqlExpr.FieldLike<?, ?> pf) {
          if (pf._path().equals(field._path()) && pf.column().equals(field.column())) {
            @SuppressWarnings("unchecked")
            T value = (T) values[i];
            return Optional.ofNullable(value);
          }
        }
      }
      return Optional.empty();
    }

    @Override
    public <T> Optional<T> untypedEval(SqlExpr<T> expr, NewRow row) {
      // For grouped results, first check if the expression matches a projected expression by
      // position
      Object[] values = row.asArray();
      for (int i = 0; i < projectedExprs.size(); i++) {
        SqlExpr<?> projExpr = projectedExprs.get(i);
        if (projExpr.equals(expr)) {
          @SuppressWarnings("unchecked")
          T result = (T) values[i];
          return Optional.ofNullable(result);
        }
      }

      // If the expression is the fields object itself, return the whole row
      if (expr == fields) {
        @SuppressWarnings("unchecked")
        T result = (T) row;
        return Optional.ofNullable(result);
      }

      // Fall back to default implementation for other expressions
      return Structure.super.untypedEval(expr, row);
    }
  }
}
