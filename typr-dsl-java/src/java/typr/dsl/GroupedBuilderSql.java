package typr.dsl;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import typr.runtime.DbType;
import typr.runtime.Fragment;
import typr.runtime.RowParser;

/**
 * SQL implementation of GroupedBuilder that generates GROUP BY queries.
 *
 * <p>Generates SQL like:
 *
 * <pre>
 * SELECT department, COUNT(*), AVG(salary)
 * FROM persons
 * WHERE active = true
 * GROUP BY department
 * HAVING COUNT(*) > 5
 * ORDER BY department
 * </pre>
 */
public class GroupedBuilderSql<Fields, Row> implements GroupedBuilder<Fields, Row> {

  private final SelectBuilderSql<Fields, Row> source;
  private final Function<Fields, List<SqlExpr<?>>> groupByExprs;
  private final List<Function<Fields, SqlExpr<Boolean>>> havingPredicates;

  public GroupedBuilderSql(
      SelectBuilderSql<Fields, Row> source, Function<Fields, List<SqlExpr<?>>> groupByExprs) {
    this.source = source;
    this.groupByExprs = groupByExprs;
    this.havingPredicates = List.of();
  }

  private GroupedBuilderSql(
      SelectBuilderSql<Fields, Row> source,
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
    return new GroupedBuilderSql<>(source, groupByExprs, List.copyOf(newHaving));
  }

  @Override
  public <NewFields extends Tuples.TupleExpr<NewRow>, NewRow extends Tuples.Tuple>
      SelectBuilder<NewFields, NewRow> select(Function<Fields, NewFields> projection) {
    return new GroupedSelectBuilderSql<>(source, groupByExprs, havingPredicates, projection);
  }

  @Override
  public Optional<Fragment> sql() {
    // Return a placeholder - the actual SQL is generated when select() is called
    return Optional.empty();
  }

  /** SelectBuilder implementation that renders GROUP BY queries. */
  static class GroupedSelectBuilderSql<
          Fields, Row, NewFields extends Tuples.TupleExpr<NewRow>, NewRow extends Tuples.Tuple>
      extends SelectBuilderSql<NewFields, NewRow> {

    private final SelectBuilderSql<Fields, Row> source;
    private final Function<Fields, List<SqlExpr<?>>> groupByExprs;
    private final List<Function<Fields, SqlExpr<Boolean>>> havingPredicates;
    private final Function<Fields, NewFields> projection;
    private final SelectParams<NewFields, NewRow> params;

    GroupedSelectBuilderSql(
        SelectBuilderSql<Fields, Row> source,
        Function<Fields, List<SqlExpr<?>>> groupByExprs,
        List<Function<Fields, SqlExpr<Boolean>>> havingPredicates,
        Function<Fields, NewFields> projection) {
      this.source = source;
      this.groupByExprs = groupByExprs;
      this.havingPredicates = havingPredicates;
      this.projection = projection;
      this.params = SelectParams.empty();
    }

    private GroupedSelectBuilderSql(
        SelectBuilderSql<Fields, Row> source,
        Function<Fields, List<SqlExpr<?>>> groupByExprs,
        List<Function<Fields, SqlExpr<Boolean>>> havingPredicates,
        Function<Fields, NewFields> projection,
        SelectParams<NewFields, NewRow> params) {
      this.source = source;
      this.groupByExprs = groupByExprs;
      this.havingPredicates = havingPredicates;
      this.projection = projection;
      this.params = params;
    }

    @Override
    public Dialect dialect() {
      return source.dialect();
    }

    @Override
    public Structure<NewFields, NewRow> structure() {
      NewFields tupleExpr = projection.apply(source.structure().fields());
      return new GroupedStructure<>(tupleExpr, tupleExpr.exprs());
    }

    @Override
    public SelectParams<NewFields, NewRow> params() {
      return params;
    }

    @Override
    public SelectBuilder<NewFields, NewRow> withParams(SelectParams<NewFields, NewRow> newParams) {
      return new GroupedSelectBuilderSql<>(
          source, groupByExprs, havingPredicates, projection, newParams);
    }

    @Override
    public SelectBuilderSql<NewFields, NewRow> withPath(Path path) {
      return new GroupedSelectBuilderSql<>(
          source.withPath(path), groupByExprs, havingPredicates, projection, params);
    }

    @Override
    public Query<NewFields, NewRow> collectQuery(RenderCtx ctx, AtomicInteger counter) {
      String alias = ctx.alias(structure()._path()).orElse("grouped_" + counter.getAndIncrement());

      // Collect the underlying query structure
      Query<Fields, Row> sourceQuery = source.collectQuery(ctx, counter);

      // Get projected expressions and group by expressions
      Fields fields = source.structure().fields();
      NewFields projectedFields = projection.apply(fields);
      List<SqlExpr<?>> projectedExprs = projectedFields.exprs();
      List<SqlExpr<?>> groupByExprList = groupByExprs.apply(fields);

      // Evaluate having predicates
      List<SqlExpr<Boolean>> evaluatedHaving = new ArrayList<>();
      for (Function<Fields, SqlExpr<Boolean>> predFn : havingPredicates) {
        evaluatedHaving.add(predFn.apply(fields));
      }

      // Evaluate where predicates from source params
      List<SqlExpr<Boolean>> evaluatedWhere = new ArrayList<>();
      SelectParams<Fields, Row> sourceParams = source.params();
      for (Function<Fields, SqlExpr<Boolean>> whereFn : sourceParams.where()) {
        evaluatedWhere.add(whereFn.apply(fields));
      }

      // Build columns for the GroupedTableState
      // These are synthetic columns representing the grouped result
      List<ColumnTuple> columns = new ArrayList<>();
      int colIdx = 0;
      for (SqlExpr<?> expr : projectedExprs) {
        // Create a synthetic field-like for each projected expression
        SyntheticField<?> syntheticField =
            new SyntheticField<>(expr.dbType(), "col_" + colIdx, alias);
        columns.add(new ColumnTuple(alias, syntheticField));
        colIdx++;
      }

      // Build row parser for the grouped result
      RowParser<NewRow> rowParser = buildGroupedRowParser(projectedExprs);

      // Create a GroupedTableState with all data needed for rendering
      GroupedTableState groupedTable =
          new GroupedTableState(
              alias,
              columns,
              sourceQuery,
              groupByExprList,
              evaluatedHaving,
              evaluatedWhere,
              projectedExprs,
              ctx);

      return new Query<>(
          groupedTable, List.of(), SelectParams.empty(), projectedFields, startCol -> rowParser);
    }

    @SuppressWarnings("unchecked")
    private RowParser<NewRow> buildGroupedRowParser(List<SqlExpr<?>> projectedExprs) {
      // Delegate to the buildRowParser that returns a proper RowParser
      return (RowParser<NewRow>) buildRowParser(projectedExprs);
    }

    @Override
    public <Fields2, Row2>
        SelectBuilder<typr.dsl.Tuple2<NewFields, Fields2>, typr.dsl.Tuple2<NewRow, Row2>> joinOn(
            SelectBuilder<Fields2, Row2> other,
            Function<typr.dsl.Tuple2<NewFields, Fields2>, SqlExpr<Boolean>> pred) {
      if (!(other instanceof SelectBuilderSql<Fields2, Row2> otherSql)) {
        throw new IllegalArgumentException("Can only join with SQL-based SelectBuilder");
      }
      return new SelectBuilderSql.TableJoin<>(
          this.withPath(Path.LEFT_IN_JOIN),
          otherSql.withPath(Path.RIGHT_IN_JOIN),
          pred,
          SelectParams.empty(),
          false);
    }

    @Override
    public <Fields2, Row2>
        SelectBuilder<typr.dsl.Tuple2<NewFields, Fields2>, typr.dsl.Tuple2<NewRow, Optional<Row2>>>
            leftJoinOn(
                SelectBuilder<Fields2, Row2> other,
                Function<typr.dsl.Tuple2<NewFields, Fields2>, SqlExpr<Boolean>> pred) {
      if (!(other instanceof SelectBuilderSql<Fields2, Row2> otherSql)) {
        throw new IllegalArgumentException("Can only join with SQL-based SelectBuilder");
      }
      return new SelectBuilderSql.TableLeftJoin<>(
          this.withPath(Path.LEFT_IN_JOIN),
          otherSql.withPath(Path.RIGHT_IN_JOIN),
          pred,
          SelectParams.empty());
    }

    @Override
    public <Fields2, Row2>
        SelectBuilder<typr.dsl.Tuple2<NewFields, Fields2>, typr.dsl.Tuple2<NewRow, List<Row2>>>
            multisetOn(
                SelectBuilder<Fields2, Row2> other,
                Function<typr.dsl.Tuple2<NewFields, Fields2>, SqlExpr<Boolean>> pred) {
      if (!(other instanceof SelectBuilderSql<Fields2, Row2> otherSql)) {
        throw new IllegalArgumentException("Can only use multiset with SQL-based SelectBuilder");
      }
      return new SelectBuilderSql.MultisetSelectBuilder<>(
          this, otherSql, pred, SelectParams.empty());
    }

    @Override
    public GroupedBuilder<NewFields, NewRow> groupByExpr(
        Function<NewFields, List<SqlExpr<?>>> groupKeys) {
      // Allow nested grouping by creating a new GroupedBuilderSql on top of this
      return new GroupedBuilderSql<>(this, groupKeys);
    }

    @Override
    protected Tuple2<Fragment, RowParser<NewRow>> getSqlAndRowParser() {
      RenderCtx ctx = RenderCtx.from(source, dialect());
      AtomicInteger counter = new AtomicInteger(0);

      // Collect the underlying query structure
      Query<Fields, Row> underlyingQuery = source.collectQuery(ctx, counter);
      Fields fields = source.structure().fields();

      Dialect dialect = ctx.dialect();
      List<TableState> allTables = underlyingQuery.allTables();

      RenderCtx joinCtx =
          ctx.withJoinContext(true)
              .withAliasToCteMap(SelectBuilderSql.buildFullAliasMap(allTables));

      // Get projected expressions
      NewFields projected = projection.apply(fields);
      List<SqlExpr<?>> projectedExprs = projected.exprs();

      // SELECT clause - projected expressions
      List<Fragment> selectFragments = new ArrayList<>();
      for (SqlExpr<?> expr : projectedExprs) {
        selectFragments.add(expr.render(joinCtx, counter));
      }
      Fragment selectClause = Fragment.lit("select ").append(Fragment.comma(selectFragments));

      // FROM clause
      Fragment fromClause =
          Fragment.lit("\nfrom ").append(renderTableRef(allTables.getFirst(), dialect, counter));

      // JOINs
      Fragment joins = Fragment.empty();
      for (JoinInfo join : underlyingQuery.joins()) {
        String joinType = join.isLeftJoin() ? "left join" : "join";
        joins =
            joins
                .append(Fragment.lit("\n" + joinType + " "))
                .append(renderTableRef(join.table(), dialect, counter))
                .append(Fragment.lit("\n  on "))
                .append(join.onCondition().apply(joinCtx, counter));
      }

      // WHERE clause from the underlying query params and source params
      SelectParams<Fields, Row> underlyingParams = underlyingQuery.topLevelParams();
      var expandedUnderlying = OrderByOrSeek.expand(fields, underlyingParams);
      SelectParams<Fields, Row> sourceParams = source.params();
      var expandedSource = OrderByOrSeek.expand(fields, sourceParams);

      // Combine both filter sources
      List<SqlExpr<Boolean>> allFilters = new ArrayList<>();
      expandedUnderlying.combinedFilter().ifPresent(allFilters::add);
      expandedSource.combinedFilter().ifPresent(allFilters::add);

      Fragment whereClause = Fragment.empty();
      if (!allFilters.isEmpty()) {
        SqlExpr<Boolean> combined = allFilters.getFirst();
        for (int i = 1; i < allFilters.size(); i++) {
          combined = combined.and(allFilters.get(i), Bijection.asBool());
        }
        whereClause = Fragment.lit("\nwhere ").append(combined.render(joinCtx, counter));
      }

      // GROUP BY clause
      List<SqlExpr<?>> groupExprs = groupByExprs.apply(fields);
      List<Fragment> groupFragments = new ArrayList<>();
      for (SqlExpr<?> expr : groupExprs) {
        groupFragments.add(expr.render(joinCtx, counter));
      }
      Fragment groupByClause = Fragment.lit("\ngroup by ").append(Fragment.comma(groupFragments));

      // HAVING clause
      Fragment havingClause = Fragment.empty();
      if (!havingPredicates.isEmpty()) {
        List<Fragment> havingFragments = new ArrayList<>();
        for (var predFn : havingPredicates) {
          SqlExpr<Boolean> pred = predFn.apply(fields);
          havingFragments.add(pred.render(joinCtx, counter));
        }
        // Combine with AND
        Fragment havingCombined = havingFragments.getFirst();
        for (int i = 1; i < havingFragments.size(); i++) {
          havingCombined =
              havingCombined.append(Fragment.lit(" AND ")).append(havingFragments.get(i));
        }
        havingClause = Fragment.lit("\nhaving ").append(havingCombined);
      }

      // ORDER BY clause (from params on this builder)
      Fragment orderByClause = Fragment.empty();
      if (!params.orderBy().isEmpty()) {
        NewFields newFields = projection.apply(fields);
        var expanded = OrderByOrSeek.expand(newFields, params);
        if (!expanded.orderBys().isEmpty()) {
          List<Fragment> orderFragments = new ArrayList<>();
          for (SortOrder<?> order : expanded.orderBys()) {
            orderFragments.add(order.render(joinCtx, counter));
          }
          orderByClause = Fragment.lit("\norder by ").append(Fragment.comma(orderFragments));
        }
      }

      // OFFSET and LIMIT (from params on this builder)
      Fragment offsetClause = Fragment.empty();
      if (params.offset().isPresent()) {
        offsetClause = Fragment.lit("\noffset " + params.offset().get());
      }

      Fragment limitClause = Fragment.empty();
      if (params.limit().isPresent()) {
        limitClause = Fragment.lit("\nlimit " + params.limit().get());
      }

      Fragment sql =
          selectClause
              .append(fromClause)
              .append(joins)
              .append(whereClause)
              .append(groupByClause)
              .append(havingClause)
              .append(orderByClause)
              .append(offsetClause)
              .append(limitClause);

      // Build row parser
      RowParser<NewRow> rowParser = buildRowParser(projectedExprs);

      return new Tuple2<>(sql, rowParser);
    }

    private Fragment renderTableRef(TableState table, Dialect dialect, AtomicInteger counter) {
      return switch (table) {
        case SimpleTableState simple -> {
          boolean needsSubquery =
              simple.whereFragment().isPresent()
                  || simple.orderByFragment().isPresent()
                  || simple.limit().isPresent()
                  || simple.offset().isPresent();

          if (!needsSubquery) {
            yield Fragment.lit(dialect.quoteTableName(simple.tableName()) + " " + simple.alias());
          }

          Fragment subquery =
              Fragment.lit("(select * from ")
                  .append(Fragment.lit(dialect.quoteTableName(simple.tableName())))
                  .append(Fragment.lit(" "))
                  .append(Fragment.lit(simple.alias()));

          if (simple.whereFragment().isPresent()) {
            subquery =
                subquery.append(Fragment.lit(" where ")).append(simple.whereFragment().get());
          }
          if (simple.orderByFragment().isPresent()) {
            subquery =
                subquery.append(Fragment.lit(" order by ")).append(simple.orderByFragment().get());
          }
          if (simple.offset().isPresent()) {
            subquery = subquery.append(Fragment.lit(" offset " + simple.offset().get()));
          }
          if (simple.limit().isPresent()) {
            subquery = subquery.append(Fragment.lit(" limit " + simple.limit().get()));
          }

          yield subquery.append(Fragment.lit(") ")).append(Fragment.lit(simple.alias()));
        }
        case CompositeTableState composite -> renderCompositeTableRef(composite, dialect, counter);
        case GroupedTableState grouped -> renderGroupedTableRefNested(grouped, dialect, counter);
        case ProjectedTableState projected ->
            throw new IllegalStateException(
                "ProjectedTableState should not appear in grouped query rendering");
      };
    }

    private Fragment renderGroupedTableRefNested(
        GroupedTableState grouped, Dialect dialect, AtomicInteger counter) {
      // Delegate to the standard GroupedTableState rendering in SelectBuilderSql
      // This handles nested grouped queries within a grouped query
      return renderGroupedTableRefFull(grouped, dialect, counter);
    }

    /** Full rendering of a GroupedTableState - used by nested grouped queries. */
    private Fragment renderGroupedTableRefFull(
        GroupedTableState grouped, Dialect dialect, AtomicInteger counter) {
      Query<?, ?> sourceQuery = grouped.sourceQuery();
      List<TableState> allTables = sourceQuery.allTables();
      RenderCtx ctx = grouped.renderCtx();

      RenderCtx joinCtx =
          ctx.withJoinContext(true)
              .withAliasToCteMap(SelectBuilderSql.buildFullAliasMap(allTables));

      // SELECT clause - projected expressions with aliases
      List<Fragment> selectFragments = new ArrayList<>();
      int colIdx = 0;
      for (SqlExpr<?> expr : grouped.projectedExprs()) {
        Fragment exprFrag = expr.render(joinCtx, counter);
        String colAlias = "col_" + colIdx;
        selectFragments.add(exprFrag.append(Fragment.lit("AS " + colAlias)));
        colIdx++;
      }
      Fragment select = Fragment.lit("SELECT ").append(Fragment.comma(selectFragments));

      // FROM clause
      Fragment from =
          Fragment.lit(" FROM ").append(renderTableRef(sourceQuery.firstTable(), dialect, counter));

      // JOINs from source query
      Fragment joins = Fragment.empty();
      for (JoinInfo join : sourceQuery.joins()) {
        String joinType = join.isLeftJoin() ? " LEFT JOIN " : " JOIN ";
        joins =
            joins
                .append(Fragment.lit(joinType))
                .append(renderTableRef(join.table(), dialect, counter))
                .append(Fragment.lit(" ON "))
                .append(join.onCondition().apply(joinCtx, counter));
      }

      // WHERE clause from evaluated predicates
      Fragment where = Fragment.empty();
      if (!grouped.wherePredicates().isEmpty()) {
        List<Fragment> whereFrags = new ArrayList<>();
        for (SqlExpr<Boolean> pred : grouped.wherePredicates()) {
          whereFrags.add(pred.render(joinCtx, counter));
        }
        where = Fragment.lit(" WHERE ").append(Fragment.and(whereFrags));
      }

      // GROUP BY clause
      List<Fragment> groupByFrags = new ArrayList<>();
      for (SqlExpr<?> expr : grouped.groupByExprs()) {
        groupByFrags.add(expr.render(joinCtx, counter));
      }
      Fragment groupBy = Fragment.lit(" GROUP BY ").append(Fragment.comma(groupByFrags));

      // HAVING clause
      Fragment having = Fragment.empty();
      if (!grouped.havingPredicates().isEmpty()) {
        List<Fragment> havingFrags = new ArrayList<>();
        for (SqlExpr<Boolean> pred : grouped.havingPredicates()) {
          havingFrags.add(pred.render(joinCtx, counter));
        }
        having = Fragment.lit(" HAVING ").append(Fragment.and(havingFrags));
      }

      // Wrap as subquery with alias
      return Fragment.lit("(")
          .append(select)
          .append(from)
          .append(joins)
          .append(where)
          .append(groupBy)
          .append(having)
          .append(Fragment.lit(") "))
          .append(Fragment.lit(grouped.alias()));
    }

    private Fragment renderCompositeTableRef(
        CompositeTableState composite, Dialect dialect, AtomicInteger counter) {
      Query<?, ?> innerQuery = composite.innerQuery();
      List<TableState> allTables = innerQuery.allTables();

      RenderCtx innerCtx =
          composite
              .renderCtx()
              .withJoinContext(true)
              .withAliasToCteMap(SelectBuilderSql.buildSimpleAliasMap(allTables));

      List<Fragment> colFragments = new ArrayList<>();
      for (TableState table : allTables) {
        for (ColumnTuple col : table.columns()) {
          String uniqueAlias = table.alias() + "_" + col.column().name();
          Fragment colRef =
              Fragment.lit(
                  table.alias()
                      + "."
                      + dialect.quoteIdent(col.column().name())
                      + " AS "
                      + uniqueAlias);
          colFragments.add(colRef);
        }
      }
      Fragment select = Fragment.lit("select ").append(Fragment.comma(colFragments));

      Fragment from =
          Fragment.lit(" from ").append(renderTableRef(innerQuery.firstTable(), dialect, counter));

      Fragment joins = Fragment.empty();
      for (JoinInfo join : innerQuery.joins()) {
        String joinType = join.isLeftJoin() ? " left join " : " join ";
        joins =
            joins
                .append(Fragment.lit(joinType))
                .append(renderTableRef(join.table(), dialect, counter))
                .append(Fragment.lit(" on "))
                .append(join.onCondition().apply(innerCtx, counter));
      }

      String compositeAlias = allTables.getFirst().alias();

      return Fragment.lit("(")
          .append(select)
          .append(from)
          .append(joins)
          .append(Fragment.lit(") "))
          .append(Fragment.lit(compositeAlias));
    }

    @SuppressWarnings("unchecked")
    private RowParser<NewRow> buildRowParser(List<SqlExpr<?>> projectedExprs) {
      List<DbType<?>> dbTypes = new ArrayList<>();
      List<Integer> exprColumnCounts = new ArrayList<>();

      for (SqlExpr<?> expr : projectedExprs) {
        exprColumnCounts.add(expr.columnCount());
        dbTypes.addAll(expr.flattenedDbTypes());
      }

      return new RowParser<>(
          dbTypes,
          values -> createNestedTuple(values, projectedExprs, exprColumnCounts),
          Tuples.Tuple::asArray);
    }

    @SuppressWarnings("unchecked")
    private NewRow createNestedTuple(
        Object[] values, List<SqlExpr<?>> exprs, List<Integer> columnCounts) {
      Object[] tupleElements = new Object[exprs.size()];
      int valueIndex = 0;

      for (int i = 0; i < exprs.size(); i++) {
        int count = columnCounts.get(i);
        if (count == 1) {
          tupleElements[i] = values[valueIndex++];
        } else {
          Object[] subValues = new Object[count];
          for (int j = 0; j < count; j++) {
            subValues[j] = values[valueIndex++];
          }
          tupleElements[i] = Tuples.createTuple(subValues);
        }
      }

      return (NewRow) Tuples.createTuple(tupleElements);
    }
  }

  /** Minimal Structure for grouped queries. */
  record GroupedStructure<NewFields extends Tuples.TupleExpr<NewRow>, NewRow extends Tuples.Tuple>(
      NewFields fields, List<SqlExpr<?>> projectedExprs) implements Structure<NewFields, NewRow> {

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
      return List.of();
    }

    @Override
    public Structure<NewFields, NewRow> withPath(Path path) {
      return this;
    }

    @Override
    public <T> Optional<T> untypedGetFieldValue(SqlExpr.FieldLike<T, ?> field, NewRow row) {
      for (int i = 0; i < projectedExprs.size(); i++) {
        var expr = projectedExprs.get(i);
        if (expr instanceof SqlExpr.FieldLike<?, ?> pf) {
          if (pf._path().equals(field._path()) && pf.column().equals(field.column())) {
            Object[] values = row.asArray();
            @SuppressWarnings("unchecked")
            T value = (T) values[i];
            return Optional.ofNullable(value);
          }
        }
      }
      return Optional.empty();
    }
  }

  /**
   * A synthetic field representing a computed expression in a grouped query. Used when grouped
   * results need to be joined with other tables. The row type R is Object since we don't know the
   * actual row type at this point.
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public record SyntheticField<T>(typr.runtime.DbType<T> pgType, String column, String tableAlias)
      implements SqlExpr.FieldLike<T, Object> {

    @Override
    public List<Path> _path() {
      return List.of();
    }

    @Override
    public Optional<T> get(Object row) {
      // Not used for grouped results
      return Optional.empty();
    }

    @Override
    public typr.runtime.Either<String, Object> set(Object row, Optional<T> value) {
      // Not used for grouped results
      return typr.runtime.Either.left("SyntheticField.set is not supported");
    }

    @Override
    public Optional<String> sqlReadCast() {
      return Optional.empty();
    }

    @Override
    public Optional<String> sqlWriteCast() {
      return Optional.empty();
    }

    @Override
    public typr.runtime.DbType<T> dbType() {
      return pgType;
    }

    @Override
    public String name() {
      return column;
    }

    @Override
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
      return Fragment.lit(tableAlias + "." + column);
    }
  }
}
