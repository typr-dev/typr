package typr.dsl;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import typr.dsl.internal.RowComparator;
import typr.runtime.Fragment;

/** Mock implementation of SelectBuilder for testing without a database. */
public class SelectBuilderMock<Fields, Row> implements SelectBuilder<Fields, Row> {
  private final Structure<Fields, Row> structure;
  private final Supplier<List<Row>> allRowsSupplier;
  private final SelectParams<Fields, Row> params;

  public SelectBuilderMock(
      Structure<Fields, Row> structure,
      Supplier<List<Row>> allRowsSupplier,
      SelectParams<Fields, Row> params) {
    this.structure = structure;
    this.allRowsSupplier = allRowsSupplier;
    this.params = params;
  }

  public SelectBuilderMock<Fields, Row> withPath(Path path) {
    return new SelectBuilderMock<>(structure.withPath(path), allRowsSupplier, params);
  }

  @Override
  public RenderCtx renderCtx() {
    // Mock doesn't generate SQL, but RenderCtx is needed for structure operations
    return RenderCtx.of(Dialect.POSTGRESQL);
  }

  @Override
  public Structure<Fields, Row> structure() {
    return structure;
  }

  @Override
  public SelectParams<Fields, Row> params() {
    return params;
  }

  @Override
  public SelectBuilder<Fields, Row> withParams(SelectParams<Fields, Row> newParams) {
    return new SelectBuilderMock<>(structure, allRowsSupplier, newParams);
  }

  @Override
  public List<Row> toList(Connection connection) {
    return applyParams(structure, allRowsSupplier.get(), params);
  }

  @Override
  public int count(Connection connection) {
    return toList(connection).size();
  }

  @Override
  public Optional<Fragment> sql() {
    return Optional.empty(); // Mock doesn't generate SQL
  }

  @Override
  public <Fields2, Row2> SelectBuilder<Tuple2<Fields, Fields2>, Tuple2<Row, Row2>> joinOn(
      SelectBuilder<Fields2, Row2> other,
      Function<Tuple2<Fields, Fields2>, SqlExpr<Boolean>> pred) {

    if (!(other instanceof SelectBuilderMock<Fields2, Row2> otherMock)) {
      throw new IllegalArgumentException("Cannot mix mock and SQL repos");
    }

    SelectBuilderMock<Fields, Row> self = this.withPath(Path.of("left"));
    SelectBuilderMock<Fields2, Row2> right = otherMock.withPath(Path.of("right"));

    Structure<Tuple2<Fields, Fields2>, Tuple2<Row, Row2>> newStructure =
        self.structure.join(right.structure);

    Supplier<List<Tuple2<Row, Row2>>> newRowsSupplier =
        () -> {
          List<Tuple2<Row, Row2>> result = new ArrayList<>();
          List<Row> leftRows = self.toList(null);
          List<Row2> rightRows = right.toList(null);

          for (Row left : leftRows) {
            for (Row2 rightRow : rightRows) {
              Tuple2<Row, Row2> tuple = Tuple2.of(left, rightRow);
              Boolean matches =
                  newStructure.untypedEval(pred.apply(newStructure.fields()), tuple).orElse(false);
              if (matches) {
                result.add(tuple);
              }
            }
          }
          return result;
        };

    return new SelectBuilderMock<>(newStructure, newRowsSupplier, SelectParams.empty());
  }

  @Override
  public <Fields2, Row2>
      SelectBuilder<Tuple2<Fields, Fields2>, Tuple2<Row, Optional<Row2>>> leftJoinOn(
          SelectBuilder<Fields2, Row2> other,
          Function<Tuple2<Fields, Fields2>, SqlExpr<Boolean>> pred) {

    if (!(other instanceof SelectBuilderMock<Fields2, Row2> otherMock)) {
      throw new IllegalArgumentException("Cannot mix mock and SQL repos");
    }

    SelectBuilderMock<Fields, Row> self = this.withPath(Path.of("left"));
    SelectBuilderMock<Fields2, Row2> right = otherMock.withPath(Path.of("right"));

    Structure<Tuple2<Fields, Fields2>, Tuple2<Row, Optional<Row2>>> newStructure =
        self.structure.leftJoin(right.structure);

    Supplier<List<Tuple2<Row, Optional<Row2>>>> newRowsSupplier =
        () -> {
          List<Tuple2<Row, Optional<Row2>>> result = new ArrayList<>();
          List<Row> leftRows = self.toList(null);
          List<Row2> rightRows = right.toList(null);

          for (Row left : leftRows) {
            Optional<Row2> matchedRight = Optional.empty();

            for (Row2 rightRow : rightRows) {
              Structure<Tuple2<Fields, Fields2>, Tuple2<Row, Row2>> tempStructure =
                  self.structure.join(((SelectBuilderMock<Fields2, Row2>) right).structure);
              Tuple2<Row, Row2> tempTuple = Tuple2.of(left, rightRow);
              Boolean matches =
                  tempStructure
                      .untypedEval(pred.apply(tempStructure.fields()), tempTuple)
                      .orElse(false);
              if (matches) {
                matchedRight = Optional.of(rightRow);
                break;
              }
            }

            result.add(Tuple2.of(left, matchedRight));
          }
          return result;
        };

    return new SelectBuilderMock<>(newStructure, newRowsSupplier, SelectParams.empty());
  }

  /** Apply the query parameters to filter, sort, and limit the rows. */
  public static <Fields, Row> List<Row> applyParams(
      Structure<Fields, Row> structure, List<Row> rows, SelectParams<Fields, Row> params) {

    var expanded = OrderByOrSeek.expand(structure.fields(), params);
    List<SqlExpr<Boolean>> filters = expanded.filters();
    List<SortOrder<?>> orderBys = expanded.orderBys();

    // Filter rows
    List<Row> filtered = new ArrayList<>();
    for (Row row : rows) {
      boolean includeRow = true;
      for (SqlExpr<Boolean> filter : filters) {
        Boolean passes = structure.untypedEval(filter, row).orElse(false);
        if (!passes) {
          includeRow = false;
          break;
        }
      }
      if (includeRow) {
        filtered.add(row);
      }
    }

    // Sort rows
    if (!orderBys.isEmpty()) {
      filtered.sort(new RowComparator<>(structure, orderBys));
    }

    // Apply offset and limit
    int start = params.offset().orElse(0);
    int end =
        params
            .limit()
            .map(limit -> Math.min(start + limit, filtered.size()))
            .orElse(filtered.size());

    if (start >= filtered.size()) {
      return new ArrayList<>();
    }

    return filtered.subList(start, end);
  }

  @Override
  public <NewFields extends Tuples.TupleExpr<NewRow>, NewRow extends Tuples.Tuple>
      SelectBuilder<NewFields, NewRow> mapExpr(Function<Fields, NewFields> projection) {
    NewFields tupleExpr = projection.apply(structure.fields());
    List<SqlExpr<?>> projectedExprs = tupleExpr.exprs();

    // Create a structure for the projected results
    ProjectedMockStructure<NewFields, NewRow> projectedStructure =
        new ProjectedMockStructure<>(tupleExpr, projectedExprs);

    // Create supplier that evaluates the projection for each row
    Supplier<List<NewRow>> projectedRowsSupplier =
        () -> {
          List<NewRow> result = new ArrayList<>();
          for (Row row : allRowsSupplier.get()) {
            // Evaluate each expression against the row
            Object[] values = new Object[projectedExprs.size()];
            for (int i = 0; i < projectedExprs.size(); i++) {
              SqlExpr<?> expr = projectedExprs.get(i);
              Optional<?> value = structure.untypedEval(expr, row);
              values[i] = value.orElse(null);
            }
            @SuppressWarnings("unchecked")
            NewRow tuple = (NewRow) Tuples.createTuple(values);
            result.add(tuple);
          }
          return result;
        };

    return new SelectBuilderMock<>(projectedStructure, projectedRowsSupplier, SelectParams.empty());
  }

  /** Structure implementation for projected results. */
  record ProjectedMockStructure<
          NewFields extends Tuples.TupleExpr<NewRow>, NewRow extends Tuples.Tuple>(
      NewFields fields, List<SqlExpr<?>> projectedExprs, List<Path> path)
      implements Structure<NewFields, NewRow> {

    ProjectedMockStructure(NewFields fields, List<SqlExpr<?>> projectedExprs) {
      this(fields, projectedExprs, List.of());
    }

    @Override
    public List<SqlExpr.FieldLike<?, ?>> allFields() {
      // Projected structure doesn't have traditional columns
      return List.of();
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
      return new ProjectedMockStructure<>(fields, projectedExprs, newPaths);
    }

    @Override
    public <T> Optional<T> untypedGetFieldValue(SqlExpr.FieldLike<T, ?> field, NewRow row) {
      // For projected rows, we need to find which position the field corresponds to
      // and get that value from the tuple
      Object[] values = row.asArray();
      for (int i = 0; i < projectedExprs.size(); i++) {
        SqlExpr<?> expr = projectedExprs.get(i);
        if (expr instanceof SqlExpr.FieldLike<?, ?> fieldExpr) {
          if (fieldExpr._path().equals(field._path())
              && fieldExpr.column().equals(field.column())) {
            @SuppressWarnings("unchecked")
            T result = (T) values[i];
            return Optional.ofNullable(result);
          }
        }
      }
      return Optional.empty();
    }

    @Override
    public <T> Optional<T> untypedEval(SqlExpr<T> expr, NewRow row) {
      // For expressions, check if it matches any of our projected expressions
      Object[] values = row.asArray();
      for (int i = 0; i < projectedExprs.size(); i++) {
        SqlExpr<?> projExpr = projectedExprs.get(i);
        if (projExpr.equals(expr)) {
          @SuppressWarnings("unchecked")
          T result = (T) values[i];
          return Optional.ofNullable(result);
        }
      }
      // For other expressions like predicates, delegate to default implementation
      return Structure.super.untypedEval(expr, row);
    }
  }

  @Override
  public <Fields2, Row2> SelectBuilder<Tuple2<Fields, Fields2>, Tuple2<Row, List<Row2>>> multisetOn(
      SelectBuilder<Fields2, Row2> other,
      Function<Tuple2<Fields, Fields2>, SqlExpr<Boolean>> pred) {

    if (!(other instanceof SelectBuilderMock<Fields2, Row2> otherMock)) {
      throw new IllegalArgumentException("Cannot mix mock and SQL repos");
    }

    SelectBuilderMock<Fields, Row> self = this.withPath(Path.of("parent"));
    SelectBuilderMock<Fields2, Row2> child = otherMock.withPath(Path.of("child"));

    // Create the combined structure for evaluating the predicate
    Structure<Tuple2<Fields, Fields2>, Tuple2<Row, Row2>> joinStructure =
        self.structure.join(child.structure);

    // Create a multiset structure
    MultisetMockStructure<Fields, Row, Fields2, Row2> multisetStructure =
        new MultisetMockStructure<>(self.structure, child.structure);

    // Create supplier that for each parent row, collects matching child rows
    Supplier<List<Tuple2<Row, List<Row2>>>> resultSupplier =
        () -> {
          List<Tuple2<Row, List<Row2>>> result = new ArrayList<>();
          List<Row> parentRows = self.toList(null);
          List<Row2> childRows = child.toList(null);

          for (Row parentRow : parentRows) {
            // Collect all matching child rows
            List<Row2> matchingChildren = new ArrayList<>();
            for (Row2 childRow : childRows) {
              Tuple2<Row, Row2> tuple = Tuple2.of(parentRow, childRow);
              Boolean matches =
                  joinStructure
                      .untypedEval(pred.apply(joinStructure.fields()), tuple)
                      .orElse(false);
              if (matches) {
                matchingChildren.add(childRow);
              }
            }

            result.add(Tuple2.of(parentRow, matchingChildren));
          }
          return result;
        };

    return new SelectBuilderMock<>(multisetStructure, resultSupplier, SelectParams.empty());
  }

  @Override
  public GroupedBuilder<Fields, Row> groupByExpr(Function<Fields, List<SqlExpr<?>>> groupKeys) {
    return new GroupedBuilderMock<>(this, groupKeys);
  }

  /** Structure implementation for multiset results. */
  record MultisetMockStructure<Fields1, Row1, Fields2, Row2>(
      Structure<Fields1, Row1> parentStructure,
      Structure<Fields2, Row2> childStructure,
      List<Path> path)
      implements Structure<Tuple2<Fields1, Fields2>, Tuple2<Row1, List<Row2>>> {

    MultisetMockStructure(
        Structure<Fields1, Row1> parentStructure, Structure<Fields2, Row2> childStructure) {
      this(parentStructure, childStructure, List.of());
    }

    @Override
    public Tuple2<Fields1, Fields2> fields() {
      return Tuple2.of(parentStructure.fields(), childStructure.fields());
    }

    @Override
    public List<SqlExpr.FieldLike<?, ?>> allFields() {
      // Return parent fields (child list column is synthetic)
      return new ArrayList<>(parentStructure.allFields());
    }

    @Override
    public List<Path> _path() {
      return path;
    }

    @Override
    public Structure<Tuple2<Fields1, Fields2>, Tuple2<Row1, List<Row2>>> withPath(Path newPath) {
      List<Path> newPaths = new ArrayList<>();
      newPaths.add(newPath);
      newPaths.addAll(path);
      return new MultisetMockStructure<>(
          parentStructure.withPath(newPath), childStructure.withPath(newPath), newPaths);
    }

    @Override
    public <T> Optional<T> untypedGetFieldValue(
        SqlExpr.FieldLike<T, ?> field, Tuple2<Row1, List<Row2>> row) {
      // Try to get from parent structure
      return parentStructure.untypedGetFieldValue(field, row._1());
    }
  }
}
