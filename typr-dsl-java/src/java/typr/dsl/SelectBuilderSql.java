package typr.dsl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import typr.runtime.And;
import typr.runtime.DbType;
import typr.runtime.Fragment;
import typr.runtime.RowParser;

/**
 * SQL implementation of SelectBuilder that generates and executes SQL queries.
 *
 * <p>The new rendering approach generates direct JOINs with subqueries only where needed, producing
 * SQL like:
 *
 * <pre>
 * SELECT t0.col1 AS t0_col1, t1.col2 AS t1_col2
 * FROM (SELECT * FROM table1 WHERE cond) t0
 * JOIN table2 t1 ON t0.col1 = t1.col1
 * WHERE ...
 * ORDER BY ...
 * </pre>
 *
 * <p>A table needs a subquery when it has any of: WHERE, ORDER BY, or LIMIT clauses.
 */
public abstract class SelectBuilderSql<Fields, Row> implements SelectBuilder<Fields, Row> {

  /** Returns a copy with the specified path. */
  public abstract SelectBuilderSql<Fields, Row> withPath(Path path);

  /** Get the dialect for this builder. */
  public abstract Dialect dialect();

  /** Collect all tables from this builder into a Query structure. */
  public abstract Query<Fields, Row> collectQuery(RenderCtx ctx, AtomicInteger counter);

  /** Get the lazy SQL and row parser. */
  protected Tuple2<Fragment, RowParser<Row>> getSqlAndRowParser() {
    RenderCtx ctx = RenderCtx.from(this, dialect());
    AtomicInteger counter = new AtomicInteger(0);
    Query<Fields, Row> query = collectQuery(ctx, counter);

    Fragment sql = renderQuery(query, ctx, counter);
    return new Tuple2<>(sql, query.rowParser().apply(1));
  }

  /**
   * Render a complete query with SELECT, FROM, JOINs, WHERE, ORDER BY, LIMIT.
   *
   * <p>Strategy: - Simple tables: direct table reference (schema.table alias) - Tables with
   * WHERE/ORDER BY/LIMIT: inline subquery (SELECT * FROM table WHERE ...) alias - Composite tables
   * (queries with joins): inline subquery with aliased columns to avoid duplicates
   */
  private Fragment renderQuery(Query<Fields, Row> query, RenderCtx ctx, AtomicInteger counter) {
    Dialect dialect = ctx.dialect();
    List<TableState> allTables = query.allTables();

    RenderCtx joinCtx = ctx.withJoinContext(true).withAliasToCteMap(buildFullAliasMap(allTables));

    // SELECT: list all columns, applying read casts
    List<Fragment> colFragments = new ArrayList<>();
    for (TableState table : allTables) {
      switch (table) {
        case CompositeTableState composite -> {
          // CTE columns: cteAlias.innerAlias_column
          for (ColumnTuple col : composite.columns()) {
            String uniqueColName = col.alias() + "_" + col.column().name();
            Fragment baseRef = Fragment.lit(table.alias() + "." + uniqueColName);
            Fragment colRef =
                col.column()
                    .sqlReadCast()
                    .map(cast -> dialect.typeCast(baseRef, cast))
                    .orElse(baseRef);
            colFragments.add(colRef);
          }
        }
        case GroupedTableState grouped -> {
          // Grouped table columns: tableAlias.col_N (just the column name, no prefix)
          for (ColumnTuple col : grouped.columns()) {
            Fragment baseRef = Fragment.lit(table.alias() + "." + col.column().name());
            Fragment colRef =
                col.column()
                    .sqlReadCast()
                    .map(cast -> dialect.typeCast(baseRef, cast))
                    .orElse(baseRef);
            colFragments.add(colRef);
          }
        }
        default -> {
          // Simple tables: alias."column"
          for (ColumnTuple col : table.columns()) {
            Fragment baseRef =
                Fragment.lit(table.alias() + "." + dialect.quoteIdent(col.column().name()));
            Fragment colRef =
                col.column()
                    .sqlReadCast()
                    .map(cast -> dialect.typeCast(baseRef, cast))
                    .orElse(baseRef);
            colFragments.add(colRef);
          }
        }
      }
    }
    Fragment select = Fragment.lit("select ").append(Fragment.comma(colFragments));

    // FROM: first table
    Fragment from =
        Fragment.lit("\nfrom ").append(renderTableRef(query.firstTable(), dialect, counter));

    // JOINs
    Fragment joins = Fragment.empty();
    for (JoinInfo join : query.joins()) {
      String joinType = join.isLeftJoin() ? "left join" : "join";
      joins =
          joins
              .append(Fragment.lit("\n" + joinType + " "))
              .append(renderTableRef(join.table(), dialect, counter))
              .append(Fragment.lit("\n  on "))
              .append(join.onCondition().apply(joinCtx, counter));
    }

    // Top-level WHERE, ORDER BY, LIMIT, OFFSET
    Fragment whereFrag = Fragment.empty();
    Fragment orderByFrag = Fragment.empty();
    Fragment limitFrag = Fragment.empty();
    Fragment offsetFrag = Fragment.empty();

    SelectParams<Fields, Row> params = query.topLevelParams();
    var expanded = OrderByOrSeek.expand(query.fields(), params);

    // WHERE
    whereFrag =
        expanded
            .combinedFilter()
            .map(combined -> Fragment.lit("\nwhere ").append(combined.render(joinCtx, counter)))
            .orElse(Fragment.empty());

    // ORDER BY
    if (!expanded.orderBys().isEmpty()) {
      List<Fragment> orderFragments = new ArrayList<>();
      for (SortOrder<?> order : expanded.orderBys()) {
        orderFragments.add(order.render(joinCtx, counter));
      }
      orderByFrag = Fragment.lit("\norder by ").append(Fragment.comma(orderFragments));
    }

    // OFFSET
    if (params.offset().isPresent()) {
      offsetFrag = Fragment.lit("\n" + dialect.offsetClause(params.offset().get()));
    }

    // LIMIT
    if (params.limit().isPresent()) {
      limitFrag = Fragment.lit("\n" + dialect.limitClause(params.limit().get()));
    }

    return select
        .append(from)
        .append(joins)
        .append(whereFrag)
        .append(orderByFrag)
        .append(offsetFrag)
        .append(limitFrag);
  }

  /**
   * Render a table reference as a subquery with aliased columns.
   *
   * <p>We always use subqueries to ensure consistent column aliasing: (SELECT col1 AS t0_col1, col2
   * AS t0_col2 FROM table t0 [WHERE ...] [ORDER BY ...]) t0
   *
   * <p>This allows the outer SELECT to reference columns uniformly as t0.t0_col1.
   */
  private Fragment renderTableRef(TableState table, Dialect dialect, AtomicInteger counter) {
    return switch (table) {
      case CompositeTableState composite -> {
        Query<?, ?> innerQuery = composite.innerQuery();
        List<TableState> allTables = innerQuery.allTables();

        RenderCtx innerCtx =
            composite
                .renderCtx()
                .withJoinContext(true)
                .withAliasToCteMap(buildSimpleAliasMap(allTables));

        // Build SELECT list: each column as alias."column" AS alias_column
        // We need unique aliases because different tables may have columns with the same name
        List<Fragment> colFragments = new ArrayList<>();
        for (TableState table1 : allTables) {
          for (ColumnTuple col : table1.columns()) {
            String uniqueAlias = table1.alias() + "_" + col.column().name();
            Fragment colRef =
                Fragment.lit(
                    table1.alias()
                        + "."
                        + dialect.quoteIdent(col.column().name())
                        + " AS "
                        + uniqueAlias);
            colFragments.add(colRef);
          }
        }
        Fragment select = Fragment.lit("select ").append(Fragment.comma(colFragments));

        // FROM first table
        Fragment from =
            Fragment.lit(" from ")
                .append(renderTableRef(innerQuery.firstTable(), dialect, counter));

        // JOINs
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

        // The composite keeps the first table's alias as its outer alias
        String compositeAlias = allTables.getFirst().alias();

        yield Fragment.lit("(")
            .append(select)
            .append(from)
            .append(joins)
            .append(Fragment.lit(") "))
            .append(Fragment.lit(compositeAlias));
      }
      case SimpleTableState simple -> {
        Fragment result;
        boolean needsSubquery =
            simple.whereFragment().isPresent()
                || simple.orderByFragment().isPresent()
                || simple.limit().isPresent()
                || simple.offset().isPresent();

        if (!needsSubquery) {
          // Simple case: direct table reference
          result = Fragment.lit(dialect.quoteTableName(simple.tableName()) + " " + simple.alias());
        } else { // Need a subquery for WHERE/ORDER BY/LIMIT/OFFSET
          Fragment subquery =
              Fragment.lit("(select * from ")
                  .append(Fragment.lit(dialect.quoteTableName(simple.tableName())))
                  .append(Fragment.lit(" "))
                  .append(Fragment.lit(simple.alias())); // Add WHERE if present
          if (simple.whereFragment().isPresent()) {
            subquery =
                subquery.append(Fragment.lit(" where ")).append(simple.whereFragment().get());
          } // Add ORDER BY if present
          if (simple.orderByFragment().isPresent()) {
            subquery =
                subquery.append(Fragment.lit(" order by ")).append(simple.orderByFragment().get());
          } // Add OFFSET if present
          if (simple.offset().isPresent()) {
            subquery =
                subquery.append(Fragment.lit(" " + dialect.offsetClause(simple.offset().get())));
          } // Add LIMIT if present
          if (simple.limit().isPresent()) {
            subquery =
                subquery.append(Fragment.lit(" " + dialect.limitClause(simple.limit().get())));
          }
          subquery = subquery.append(Fragment.lit(") ")).append(Fragment.lit(simple.alias()));
          result = subquery;
        }

        yield result;
      }
      case GroupedTableState grouped ->
          renderGroupedTableRef(grouped, dialect, counter, this::renderTableRef);
      case ProjectedTableState projected ->
          throw new IllegalStateException(
              "ProjectedTableState should not appear in standard query rendering");
    };
  }

  /**
   * Render a grouped query as a subquery. Renders: (SELECT projected_cols FROM source GROUP BY ...
   * HAVING ...) alias
   *
   * @param tableRefRenderer function to render table references (needed since this is called from
   *     static context)
   */
  static Fragment renderGroupedTableRef(
      GroupedTableState grouped,
      Dialect dialect,
      AtomicInteger counter,
      TableRefRenderer tableRefRenderer) {
    Query<?, ?> sourceQuery = grouped.sourceQuery();
    List<TableState> allTables = sourceQuery.allTables();
    RenderCtx ctx = grouped.renderCtx();

    RenderCtx joinCtx = ctx.withJoinContext(true).withAliasToCteMap(buildFullAliasMap(allTables));

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
        Fragment.lit(" FROM ")
            .append(tableRefRenderer.render(sourceQuery.firstTable(), dialect, counter));

    // JOINs from source query
    Fragment joins = Fragment.empty();
    for (JoinInfo join : sourceQuery.joins()) {
      String joinType = join.isLeftJoin() ? " LEFT JOIN " : " JOIN ";
      joins =
          joins
              .append(Fragment.lit(joinType))
              .append(tableRefRenderer.render(join.table(), dialect, counter))
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

  /**
   * Functional interface for rendering table references. Used to pass the table rendering logic to
   * static methods.
   */
  @FunctionalInterface
  interface TableRefRenderer {
    Fragment render(TableState table, Dialect dialect, AtomicInteger counter);
  }

  @Override
  public RenderCtx renderCtx() {
    return RenderCtx.from(this, dialect());
  }

  @Override
  public List<Row> toList(Connection connection) {
    Tuple2<Fragment, RowParser<Row>> sqlAndParser = getSqlAndRowParser();
    Fragment frag = sqlAndParser.first();
    RowParser<Row> rowParser = sqlAndParser.second();

    try (PreparedStatement ps = connection.prepareStatement(frag.render())) {
      frag.set(ps);
      try (ResultSet rs = ps.executeQuery()) {
        List<Row> results = new ArrayList<>();
        while (rs.next()) {
          results.add(rowParser.parse(rs));
        }
        return results;
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to execute query: " + frag.render(), e);
    }
  }

  @Override
  public int count(Connection connection) {
    Tuple2<Fragment, RowParser<Row>> sqlAndParser = getSqlAndRowParser();
    Fragment frag = sqlAndParser.first();
    Fragment countQuery =
        Fragment.lit("select count(*) from (").append(frag).append(Fragment.lit(") subq"));

    try (PreparedStatement ps = connection.prepareStatement(countQuery.render())) {
      countQuery.set(ps);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
          return rs.getInt(1);
        }
        return 0;
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to execute count query: " + countQuery.render(), e);
    }
  }

  @Override
  public Optional<Fragment> sql() {
    return Optional.of(getSqlAndRowParser().first());
  }

  @Override
  public <Fields2, Row2>
      SelectBuilder<typr.dsl.Tuple2<Fields, Fields2>, typr.dsl.Tuple2<Row, Row2>> joinOn(
          SelectBuilder<Fields2, Row2> other,
          Function<typr.dsl.Tuple2<Fields, Fields2>, SqlExpr<Boolean>> pred) {

    if (!(other instanceof SelectBuilderSql<Fields2, Row2> otherSql)) {
      throw new IllegalArgumentException("Can only join with SQL-based SelectBuilder");
    }

    return new TableJoin<>(
        this.withPath(Path.LEFT_IN_JOIN),
        otherSql.withPath(Path.RIGHT_IN_JOIN),
        pred,
        SelectParams.empty(),
        false);
  }

  @Override
  public <Fields2, Row2>
      SelectBuilder<typr.dsl.Tuple2<Fields, Fields2>, typr.dsl.Tuple2<Row, Optional<Row2>>>
          leftJoinOn(
              SelectBuilder<Fields2, Row2> other,
              Function<typr.dsl.Tuple2<Fields, Fields2>, SqlExpr<Boolean>> pred) {

    if (!(other instanceof SelectBuilderSql<Fields2, Row2> otherSql)) {
      throw new IllegalArgumentException("Can only join with SQL-based SelectBuilder");
    }

    return new TableLeftJoin<>(
        this.withPath(Path.LEFT_IN_JOIN),
        otherSql.withPath(Path.RIGHT_IN_JOIN),
        pred,
        SelectParams.empty());
  }

  @Override
  @SuppressWarnings("unchecked")
  public <NewFields extends Tuples.TupleExpr<NewRow>, NewRow extends Tuples.Tuple>
      SelectBuilder<NewFields, NewRow> mapExpr(Function<Fields, NewFields> projection) {
    // Apply the projection to get the TupleExpr
    NewFields tupleExpr = projection.apply(structure().fields());

    return new ProjectedSelectBuilder<>(this, tupleExpr, tupleExpr.exprs());
  }

  @Override
  @SuppressWarnings("unchecked")
  public <Fields2, Row2>
      SelectBuilder<typr.dsl.Tuple2<Fields, Fields2>, typr.dsl.Tuple2<Row, List<Row2>>> multisetOn(
          SelectBuilder<Fields2, Row2> other,
          Function<typr.dsl.Tuple2<Fields, Fields2>, SqlExpr<Boolean>> pred) {
    if (!(other instanceof SelectBuilderSql<Fields2, Row2> otherSql)) {
      throw new IllegalArgumentException("Can only use multiset with SQL-based SelectBuilder");
    }

    return new MultisetSelectBuilder<>(this, otherSql, pred, SelectParams.empty());
  }

  @Override
  public GroupedBuilder<Fields, Row> groupByExpr(Function<Fields, List<SqlExpr<?>>> groupKeys) {
    return new GroupedBuilderSql<>(this, groupKeys);
  }

  /** Build a simple alias map from a list of tables. Maps each table alias to itself. */
  static Map<String, String> buildSimpleAliasMap(List<TableState> tables) {
    Map<String, String> aliasMap = new HashMap<>();
    for (TableState table : tables) {
      aliasMap.put(table.alias(), table.alias());
    }
    return aliasMap;
  }

  /**
   * Build a full alias map from a list of tables. For composite/grouped tables, also maps inner
   * column aliases to the table alias.
   */
  static Map<String, String> buildFullAliasMap(List<TableState> tables) {
    Map<String, String> aliasMap = new HashMap<>();
    for (TableState table : tables) {
      switch (table) {
        case CompositeTableState composite -> {
          for (ColumnTuple col : composite.columns()) {
            aliasMap.put(col.alias(), table.alias());
          }
        }
        case GroupedTableState grouped -> {
          for (ColumnTuple col : grouped.columns()) {
            aliasMap.put(col.alias(), table.alias());
          }
        }
        default -> {}
      }
      aliasMap.put(table.alias(), table.alias());
    }
    return aliasMap;
  }

  /** Tuple helper class. */
  public record Tuple2<A, B>(A first, B second) {}

  /** Column tuple for queries. */
  public record ColumnTuple(String alias, SqlExpr.FieldLike<?, ?> column) {}

  /** State for a single table in the query. */
  sealed interface TableState
      permits SimpleTableState, CompositeTableState, ProjectedTableState, GroupedTableState {
    String alias();

    List<ColumnTuple> columns();

    boolean isComposite();
  }

  /** A simple table state represents a single database table. */
  record SimpleTableState(
      String tableName,
      String alias,
      List<ColumnTuple> columns,
      Optional<Fragment> whereFragment,
      Optional<Fragment> orderByFragment,
      Optional<Integer> limit,
      Optional<Integer> offset)
      implements TableState {
    @Override
    public boolean isComposite() {
      return false;
    }
  }

  /**
   * A composite table state represents an entire query (with internal joins) as a single unit. This
   * is used when the right side of a join has its own joins, requiring the entire right side to be
   * rendered as a subquery.
   */
  record CompositeTableState(
      Query<?, ?> innerQuery, String alias, List<ColumnTuple> columns, RenderCtx renderCtx)
      implements TableState {
    @Override
    public boolean isComposite() {
      return true;
    }
  }

  /**
   * A grouped table state represents a grouped query (with GROUP BY) as a subquery. This allows
   * joining with the results of a grouped query. Contains all data needed to render the grouped
   * query at render time.
   */
  record GroupedTableState(
      String alias,
      List<ColumnTuple> columns,
      Query<?, ?> sourceQuery,
      List<SqlExpr<?>> groupByExprs,
      List<SqlExpr<Boolean>> havingPredicates,
      List<SqlExpr<Boolean>> wherePredicates,
      List<SqlExpr<?>> projectedExprs,
      RenderCtx renderCtx)
      implements TableState {
    @Override
    public boolean isComposite() {
      return true; // Needs to be rendered as subquery
    }
  }

  /** Information about a join. */
  record JoinInfo(TableState table, boolean isLeftJoin, OnConditionRenderer onCondition) {}

  /** Functional interface for rendering ON conditions. */
  @FunctionalInterface
  interface OnConditionRenderer {
    Fragment apply(RenderCtx ctx, AtomicInteger counter);
  }

  /** Complete query structure with all tables and joins. */
  public record Query<Fields, Row>(
      TableState firstTable,
      List<JoinInfo> joins,
      SelectParams<Fields, Row> topLevelParams,
      Fields fields,
      Function<Integer, RowParser<Row>> rowParser) {
    List<TableState> allTables() {
      List<TableState> result = new ArrayList<>();
      result.add(firstTable);
      for (JoinInfo join : joins) {
        result.add(join.table());
      }
      return result;
    }
  }

  /**
   * Computes the total number of columns across all tables, accounting for ProjectedTableState
   * which has projected expressions instead of regular columns.
   */
  private static int computeColumnCount(List<TableState> tables) {
    int count = 0;
    for (TableState table : tables) {
      if (table instanceof ProjectedTableState projected) {
        // Use columnCount() which recursively handles nested multi-column expressions
        for (SqlExpr<?> expr : projected.projectedExprs()) {
          count += expr.columnCount();
        }
      } else {
        count += table.columns().size();
      }
    }
    return count;
  }

  /**
   * Creates a composite TableState that represents an entire query (with joins) as a single unit.
   */
  static <F, R> CompositeTableState createCompositeTableState(Query<F, R> query, RenderCtx ctx) {
    // Use the first table's alias as the composite alias
    // This matches what renderCompositeTableRef uses for the outer alias
    String alias = query.firstTable().alias();

    // Collect all columns from all tables in the query
    List<ColumnTuple> allColumns = new ArrayList<>();
    for (TableState table : query.allTables()) {
      for (ColumnTuple col : table.columns()) {
        allColumns.add(new ColumnTuple(col.alias(), col.column()));
      }
    }

    return new CompositeTableState(query, alias, allColumns, ctx);
  }

  /** Relation implementation - a single table. */
  static class Relation<Fields, Row> extends SelectBuilderSql<Fields, Row> {
    private final String tableName;
    private final Structure<Fields, Row> structure;
    private final Function<Integer, RowParser<Row>> rowParser;
    private final SelectParams<Fields, Row> params;
    private final Dialect dialect;

    public String name() {
      return tableName;
    }

    public Dialect dialect() {
      return dialect;
    }

    public Relation(
        String name,
        Structure<Fields, Row> structure,
        RowParser<Row> rowParser,
        SelectParams<Fields, Row> params,
        Dialect dialect) {
      this.tableName = name;
      this.structure = structure;
      this.rowParser = i -> rowParser;
      this.params = params;
      this.dialect = dialect;
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
      return new Relation<>(tableName, structure, rowParser.apply(1), newParams, dialect);
    }

    @Override
    public SelectBuilderSql<Fields, Row> withPath(Path path) {
      return new Relation<>(
          tableName, structure.withPath(path), rowParser.apply(1), params, dialect);
    }

    @Override
    public Query<Fields, Row> collectQuery(RenderCtx ctx, AtomicInteger counter) {
      String alias = ctx.alias(structure._path()).orElse("t0");

      List<ColumnTuple> columns =
          structure.allFields().stream()
              .map(c -> new ColumnTuple(alias, c))
              .collect(Collectors.toList());

      // Render WHERE, ORDER BY for this table
      var expanded = OrderByOrSeek.expand(structure.fields(), params);

      Optional<Fragment> whereFragment =
          expanded.combinedFilter().map(combined -> combined.render(ctx, counter));

      Optional<Fragment> orderByFragment = Optional.empty();
      if (!expanded.orderBys().isEmpty()) {
        List<Fragment> orderFragments = new ArrayList<>();
        for (SortOrder<?> order : expanded.orderBys()) {
          orderFragments.add(order.render(ctx, counter));
        }
        orderByFragment = Optional.of(Fragment.comma(orderFragments));
      }

      SimpleTableState tableState =
          new SimpleTableState(
              tableName,
              alias,
              columns,
              whereFragment,
              orderByFragment,
              params.limit(),
              params.offset());

      return new Query<>(
          tableState, List.of(), SelectParams.empty(), structure.fields(), rowParser);
    }
  }

  /** SelectBuilder for inner joins. */
  static class TableJoin<Fields1, Row1, Fields2, Row2>
      extends SelectBuilderSql<typr.dsl.Tuple2<Fields1, Fields2>, typr.dsl.Tuple2<Row1, Row2>> {

    private final SelectBuilderSql<Fields1, Row1> leftBuilder;
    private final SelectBuilderSql<Fields2, Row2> rightBuilder;
    private final Function<typr.dsl.Tuple2<Fields1, Fields2>, SqlExpr<Boolean>> pred;
    private final SelectParams<typr.dsl.Tuple2<Fields1, Fields2>, typr.dsl.Tuple2<Row1, Row2>>
        params;
    private final boolean isLeftJoin;

    public SelectBuilderSql<Fields1, Row1> left() {
      return leftBuilder;
    }

    public SelectBuilderSql<Fields2, Row2> right() {
      return rightBuilder;
    }

    public TableJoin(
        SelectBuilderSql<Fields1, Row1> left,
        SelectBuilderSql<Fields2, Row2> right,
        Function<typr.dsl.Tuple2<Fields1, Fields2>, SqlExpr<Boolean>> pred,
        SelectParams<typr.dsl.Tuple2<Fields1, Fields2>, typr.dsl.Tuple2<Row1, Row2>> params,
        boolean isLeftJoin) {
      this.leftBuilder = left;
      this.rightBuilder = right;
      this.pred = pred;
      this.params = params;
      this.isLeftJoin = isLeftJoin;
    }

    @Override
    public Dialect dialect() {
      return leftBuilder.dialect();
    }

    @Override
    public Structure<typr.dsl.Tuple2<Fields1, Fields2>, typr.dsl.Tuple2<Row1, Row2>> structure() {
      return leftBuilder.structure().join(rightBuilder.structure());
    }

    @Override
    public SelectParams<typr.dsl.Tuple2<Fields1, Fields2>, typr.dsl.Tuple2<Row1, Row2>> params() {
      return params;
    }

    @Override
    public SelectBuilder<typr.dsl.Tuple2<Fields1, Fields2>, typr.dsl.Tuple2<Row1, Row2>> withParams(
        SelectParams<typr.dsl.Tuple2<Fields1, Fields2>, typr.dsl.Tuple2<Row1, Row2>> newParams) {
      return new TableJoin<>(leftBuilder, rightBuilder, pred, newParams, isLeftJoin);
    }

    @Override
    public SelectBuilderSql<typr.dsl.Tuple2<Fields1, Fields2>, typr.dsl.Tuple2<Row1, Row2>>
        withPath(Path path) {
      return new TableJoin<>(
          leftBuilder.withPath(path).withPath(Path.LEFT_IN_JOIN),
          rightBuilder.withPath(path).withPath(Path.RIGHT_IN_JOIN),
          pred,
          params,
          isLeftJoin);
    }

    @Override
    public Query<typr.dsl.Tuple2<Fields1, Fields2>, typr.dsl.Tuple2<Row1, Row2>> collectQuery(
        RenderCtx ctx, AtomicInteger counter) {

      Query<Fields1, Row1> leftQuery = leftBuilder.collectQuery(ctx, counter);
      Query<Fields2, Row2> rightQuery = rightBuilder.collectQuery(ctx, counter);

      Structure<typr.dsl.Tuple2<Fields1, Fields2>, typr.dsl.Tuple2<Row1, Row2>> newStructure =
          leftBuilder.structure().join(rightBuilder.structure());

      // Collect all tables from left side
      List<JoinInfo> allJoins = new ArrayList<>(leftQuery.joins());

      // ON condition renderer
      OnConditionRenderer onRenderer =
          (joinCtx, cnt) -> pred.apply(newStructure.fields()).render(joinCtx, cnt);

      // If the right side has its own joins, we cannot flatten them because the ON condition
      // might reference columns from any table in the right side. Instead, mark it as a
      // "composite" table that needs special rendering.
      if (rightQuery.joins().isEmpty()) {
        // Simple case: right side is a single table
        allJoins.add(new JoinInfo(rightQuery.firstTable(), isLeftJoin, onRenderer));
      } else {
        // Complex case: right side has joins - create a composite table state
        // that represents the entire right query as a single joined unit
        // The composite alias will be the first table's alias from the right query
        String compositeAlias = rightQuery.firstTable().alias();

        // Create an ON renderer that remaps all right-side table aliases to the composite alias
        OnConditionRenderer remappedOnRenderer =
            (joinCtx, cnt) -> {
              // Build a new alias map that remaps all right-side aliases to the composite
              Map<String, String> remappedAliasMap = new HashMap<>(joinCtx.aliasToCteMap());
              for (TableState table : rightQuery.allTables()) {
                remappedAliasMap.put(table.alias(), compositeAlias);
              }
              RenderCtx remappedCtx = joinCtx.withAliasToCteMap(remappedAliasMap);
              return pred.apply(newStructure.fields()).render(remappedCtx, cnt);
            };

        TableState compositeTable = createCompositeTableState(rightQuery, ctx);
        allJoins.add(new JoinInfo(compositeTable, isLeftJoin, remappedOnRenderer));
      }

      // Create combined row parser
      Function<Integer, RowParser<Row1>> leftParser = leftQuery.rowParser();
      Function<Integer, RowParser<Row2>> rightParser = rightQuery.rowParser();
      int leftColCount = leftQuery.allTables().stream().mapToInt(t -> t.columns().size()).sum();

      Function<Integer, RowParser<typr.dsl.Tuple2<Row1, Row2>>> combinedParser =
          i -> {
            RowParser<Row1> r1Parser = leftParser.apply(i);
            RowParser<Row2> r2Parser = rightParser.apply(i + leftColCount);
            RowParser<And<Row1, Row2>> andParser = r1Parser.joined(r2Parser);

            var allColumns = new ArrayList<>(andParser.columns());
            Function<Object[], typr.dsl.Tuple2<Row1, Row2>> decode =
                values -> {
                  And<Row1, Row2> and = andParser.decode().apply(values);
                  return typr.dsl.Tuple2.of(and.left(), and.right());
                };
            Function<typr.dsl.Tuple2<Row1, Row2>, Object[]> encode =
                tuple2 -> {
                  And<Row1, Row2> and = new And<>(tuple2._1(), tuple2._2());
                  return andParser.encode().apply(and);
                };

            return new RowParser<>(allColumns, decode, encode);
          };

      return new Query<>(
          leftQuery.firstTable(), allJoins, params, newStructure.fields(), combinedParser);
    }
  }

  /** SelectBuilder for left joins. */
  static class TableLeftJoin<Fields1, Row1, Fields2, Row2>
      extends SelectBuilderSql<
          typr.dsl.Tuple2<Fields1, Fields2>, typr.dsl.Tuple2<Row1, Optional<Row2>>> {

    private final SelectBuilderSql<Fields1, Row1> leftBuilder;
    private final SelectBuilderSql<Fields2, Row2> rightBuilder;
    private final Function<typr.dsl.Tuple2<Fields1, Fields2>, SqlExpr<Boolean>> pred;
    private final SelectParams<
            typr.dsl.Tuple2<Fields1, Fields2>, typr.dsl.Tuple2<Row1, Optional<Row2>>>
        params;

    public SelectBuilderSql<Fields1, Row1> left() {
      return leftBuilder;
    }

    public SelectBuilderSql<Fields2, Row2> right() {
      return rightBuilder;
    }

    public TableLeftJoin(
        SelectBuilderSql<Fields1, Row1> left,
        SelectBuilderSql<Fields2, Row2> right,
        Function<typr.dsl.Tuple2<Fields1, Fields2>, SqlExpr<Boolean>> pred,
        SelectParams<typr.dsl.Tuple2<Fields1, Fields2>, typr.dsl.Tuple2<Row1, Optional<Row2>>>
            params) {
      this.leftBuilder = left;
      this.rightBuilder = right;
      this.pred = pred;
      this.params = params;
    }

    @Override
    public Dialect dialect() {
      return leftBuilder.dialect();
    }

    @Override
    public Structure<typr.dsl.Tuple2<Fields1, Fields2>, typr.dsl.Tuple2<Row1, Optional<Row2>>>
        structure() {
      return leftBuilder.structure().leftJoin(rightBuilder.structure());
    }

    @Override
    public SelectParams<typr.dsl.Tuple2<Fields1, Fields2>, typr.dsl.Tuple2<Row1, Optional<Row2>>>
        params() {
      return params;
    }

    @Override
    public SelectBuilder<typr.dsl.Tuple2<Fields1, Fields2>, typr.dsl.Tuple2<Row1, Optional<Row2>>>
        withParams(
            SelectParams<typr.dsl.Tuple2<Fields1, Fields2>, typr.dsl.Tuple2<Row1, Optional<Row2>>>
                newParams) {
      return new TableLeftJoin<>(leftBuilder, rightBuilder, pred, newParams);
    }

    @Override
    public SelectBuilderSql<
            typr.dsl.Tuple2<Fields1, Fields2>, typr.dsl.Tuple2<Row1, Optional<Row2>>>
        withPath(Path path) {
      // When a join gets a path, we need to distinguish its left and right sides
      // by prepending path and then LEFT/RIGHT to maintain uniqueness throughout nesting
      return new TableLeftJoin<>(
          leftBuilder.withPath(path).withPath(Path.LEFT_IN_JOIN),
          rightBuilder.withPath(path).withPath(Path.RIGHT_IN_JOIN),
          pred,
          params);
    }

    @Override
    public Query<typr.dsl.Tuple2<Fields1, Fields2>, typr.dsl.Tuple2<Row1, Optional<Row2>>>
        collectQuery(RenderCtx ctx, AtomicInteger counter) {

      Query<Fields1, Row1> leftQuery = leftBuilder.collectQuery(ctx, counter);
      Query<Fields2, Row2> rightQuery = rightBuilder.collectQuery(ctx, counter);

      Structure<typr.dsl.Tuple2<Fields1, Fields2>, typr.dsl.Tuple2<Row1, Row2>> joinedStructure =
          leftBuilder.structure().join(rightBuilder.structure());
      Structure<typr.dsl.Tuple2<Fields1, Fields2>, typr.dsl.Tuple2<Row1, Optional<Row2>>>
          newStructure = leftBuilder.structure().leftJoin(rightBuilder.structure());

      // Collect all tables from left side
      List<JoinInfo> allJoins = new ArrayList<>(leftQuery.joins());

      // Add right table as left join
      OnConditionRenderer onRenderer =
          (joinCtx, cnt) -> pred.apply(joinedStructure.fields()).render(joinCtx, cnt);

      // If the right side has its own joins, we cannot flatten them
      if (rightQuery.joins().isEmpty()) {
        // Simple case: right side is a single table
        allJoins.add(new JoinInfo(rightQuery.firstTable(), true, onRenderer));
      } else {
        // Complex case: right side has joins
        String compositeAlias = rightQuery.firstTable().alias();

        // Create an ON renderer that remaps all right-side table aliases to the composite alias
        OnConditionRenderer remappedOnRenderer =
            (joinCtx, cnt) -> {
              Map<String, String> remappedAliasMap = new HashMap<>(joinCtx.aliasToCteMap());
              for (TableState table : rightQuery.allTables()) {
                remappedAliasMap.put(table.alias(), compositeAlias);
              }
              RenderCtx remappedCtx = joinCtx.withAliasToCteMap(remappedAliasMap);
              return pred.apply(joinedStructure.fields()).render(remappedCtx, cnt);
            };

        TableState compositeTable = createCompositeTableState(rightQuery, ctx);
        allJoins.add(new JoinInfo(compositeTable, true, remappedOnRenderer));
      }

      // Create combined row parser for left join
      Function<Integer, RowParser<Row1>> leftParser = leftQuery.rowParser();
      Function<Integer, RowParser<Row2>> rightParser = rightQuery.rowParser();
      int leftColCount = leftQuery.allTables().stream().mapToInt(t -> t.columns().size()).sum();

      Function<Integer, RowParser<typr.dsl.Tuple2<Row1, Optional<Row2>>>> combinedParser =
          i -> {
            RowParser<Row1> r1Parser = leftParser.apply(i);
            RowParser<Row2> r2Parser = rightParser.apply(i + leftColCount);
            RowParser<And<Row1, Optional<Row2>>> andParser = r1Parser.leftJoined(r2Parser);

            var allColumns = new ArrayList<>(andParser.columns());
            Function<Object[], typr.dsl.Tuple2<Row1, Optional<Row2>>> decode =
                values -> {
                  And<Row1, Optional<Row2>> and = andParser.decode().apply(values);
                  return typr.dsl.Tuple2.of(and.left(), and.right());
                };
            Function<typr.dsl.Tuple2<Row1, Optional<Row2>>, Object[]> encode =
                tuple2 -> {
                  And<Row1, Optional<Row2>> and = new And<>(tuple2._1(), tuple2._2());
                  return andParser.encode().apply(and);
                };

            return new RowParser<>(allColumns, decode, encode);
          };

      return new Query<>(
          leftQuery.firstTable(), allJoins, params, newStructure.fields(), combinedParser);
    }
  }

  /**
   * SelectBuilder that wraps another builder and projects to specific expressions.
   *
   * <p>The NewFields is a TupleExpr (e.g., TupleExpr2&lt;String, Integer&gt;) and NewRow is the
   * corresponding Tuple (e.g., Tuple2&lt;String, Integer&gt;).
   *
   * <p>Supports both FieldLike (column references) and computed expressions.
   */
  static class ProjectedSelectBuilder<
          Fields, Row, NewFields extends Tuples.TupleExpr<NewRow>, NewRow extends Tuples.Tuple>
      extends SelectBuilderSql<NewFields, NewRow> {

    private final SelectBuilderSql<Fields, Row> underlying;
    private final NewFields tupleExpr;
    private final List<SqlExpr<?>> projectedExprs;
    private final SelectParams<NewFields, NewRow> params;

    ProjectedSelectBuilder(
        SelectBuilderSql<Fields, Row> underlying,
        NewFields tupleExpr,
        List<SqlExpr<?>> projectedExprs) {
      this.underlying = underlying;
      this.tupleExpr = tupleExpr;
      this.projectedExprs = projectedExprs;
      this.params = SelectParams.empty();
    }

    private ProjectedSelectBuilder(
        SelectBuilderSql<Fields, Row> underlying,
        NewFields tupleExpr,
        List<SqlExpr<?>> projectedExprs,
        SelectParams<NewFields, NewRow> params) {
      this.underlying = underlying;
      this.tupleExpr = tupleExpr;
      this.projectedExprs = projectedExprs;
      this.params = params;
    }

    @Override
    public Dialect dialect() {
      return underlying.dialect();
    }

    @Override
    public Structure<NewFields, NewRow> structure() {
      // Return a minimal structure for the projected expressions
      return new ProjectedStructure<>(tupleExpr, projectedExprs);
    }

    @Override
    public SelectParams<NewFields, NewRow> params() {
      return params;
    }

    @Override
    public SelectBuilder<NewFields, NewRow> withParams(SelectParams<NewFields, NewRow> newParams) {
      return new ProjectedSelectBuilder<>(underlying, tupleExpr, projectedExprs, newParams);
    }

    @Override
    public SelectBuilderSql<NewFields, NewRow> withPath(Path path) {
      return new ProjectedSelectBuilder<>(
          underlying.withPath(path), tupleExpr, projectedExprs, params);
    }

    @Override
    public Query<NewFields, NewRow> collectQuery(RenderCtx ctx, AtomicInteger counter) {
      // Collect the underlying query
      Query<Fields, Row> underlyingQuery = underlying.collectQuery(ctx, counter);

      List<DbType<?>> dbTypes = new ArrayList<>();
      List<Integer> exprColumnCounts = new ArrayList<>();

      for (SqlExpr<?> expr : projectedExprs) {
        exprColumnCounts.add(expr.columnCount());
        dbTypes.addAll(expr.flattenedDbTypes());
      }

      @SuppressWarnings("unchecked")
      Function<Integer, RowParser<NewRow>> projectedParser =
          startCol ->
              // Use the generated asArray() method - no reflection needed
              new RowParser<>(
                  dbTypes,
                  values -> createNestedTuple(values, projectedExprs, exprColumnCounts),
                  Tuples.Tuple::asArray);

      // We use a ProjectedTableState that renders the projection
      ProjectedTableState projectedTable =
          new ProjectedTableState(underlyingQuery, projectedExprs, ctx, counter);

      return new Query<>(
          projectedTable,
          List.of(), // No additional joins - they're encapsulated in the projected table
          SelectParams.empty(), // Params handled separately
          tupleExpr,
          projectedParser);
    }

    @Override
    protected Tuple2<Fragment, RowParser<NewRow>> getSqlAndRowParser() {
      // Optimized SQL generation: push down projections to avoid selecting unnecessary columns
      RenderCtx ctx = RenderCtx.from(underlying, dialect());
      AtomicInteger counter = new AtomicInteger(0);
      Query<Fields, Row> underlyingQuery = underlying.collectQuery(ctx, counter);

      Dialect dialect = ctx.dialect();
      List<TableState> allTables = underlyingQuery.allTables();

      RenderCtx joinCtx =
          ctx.withJoinContext(true).withAliasToCteMap(buildSimpleAliasMap(allTables));

      // SELECT the projected expressions (both column references and computed expressions)
      List<Fragment> selectFragments = new ArrayList<>();
      for (SqlExpr<?> expr : projectedExprs) {
        // Render the expression - works for both FieldLike and computed expressions
        Fragment exprFrag = expr.render(joinCtx, counter);
        selectFragments.add(exprFrag);
      }
      Fragment select = Fragment.lit("select ").append(Fragment.comma(selectFragments));

      // FROM: render the first table
      Fragment from =
          Fragment.lit("\nfrom ")
              .append(renderTableRefOptimized(underlyingQuery.firstTable(), dialect, counter));

      // JOINs
      Fragment joins = Fragment.empty();
      for (JoinInfo join : underlyingQuery.joins()) {
        String joinType = join.isLeftJoin() ? " left join " : " join ";
        joins =
            joins
                .append(Fragment.lit("\n" + joinType))
                .append(renderTableRefOptimized(join.table(), dialect, counter))
                .append(Fragment.lit(" on "))
                .append(join.onCondition().apply(joinCtx, counter));
      }

      // WHERE, ORDER BY, LIMIT, OFFSET from underlying params
      Fragment whereFrag = Fragment.empty();
      Fragment orderByFrag = Fragment.empty();
      Fragment limitFrag = Fragment.empty();
      Fragment offsetFrag = Fragment.empty();

      SelectParams<Fields, Row> underlyingParams = underlyingQuery.topLevelParams();
      var expanded = OrderByOrSeek.expand(underlyingQuery.fields(), underlyingParams);

      whereFrag =
          expanded
              .combinedFilter()
              .map(combined -> Fragment.lit("\nwhere ").append(combined.render(joinCtx, counter)))
              .orElse(Fragment.empty());

      if (!expanded.orderBys().isEmpty()) {
        List<Fragment> orderFragments = new ArrayList<>();
        for (SortOrder<?> order : expanded.orderBys()) {
          orderFragments.add(order.render(joinCtx, counter));
        }
        orderByFrag = Fragment.lit("\norder by ").append(Fragment.comma(orderFragments));
      }

      if (underlyingParams.offset().isPresent()) {
        offsetFrag = Fragment.lit("\noffset " + underlyingParams.offset().get());
      }

      if (underlyingParams.limit().isPresent()) {
        limitFrag = Fragment.lit("\nlimit " + underlyingParams.limit().get());
      }

      // Handle params on the projected query itself
      if (!params.where().isEmpty()
          || !params.orderBy().isEmpty()
          || params.limit().isPresent()
          || params.offset().isPresent()) {
        // TODO: Apply params from the projected query
      }

      return new Tuple2<>(
          select
              .append(from)
              .append(joins)
              .append(whereFrag)
              .append(orderByFrag)
              .append(offsetFrag)
              .append(limitFrag),
          buildRowParser());
    }

    /**
     * Render a table reference for optimized projection queries. For simple tables with WHERE
     * clauses, use a subquery to preserve filtering.
     */
    private Fragment renderTableRefOptimized(
        TableState table, Dialect dialect, AtomicInteger counter) {
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

          // Build subquery to preserve filtering
          Fragment subquery =
              Fragment.lit("(SELECT * FROM ")
                  .append(Fragment.lit(dialect.quoteTableName(simple.tableName())))
                  .append(Fragment.lit(" "))
                  .append(Fragment.lit(simple.alias()));

          if (simple.whereFragment().isPresent()) {
            subquery =
                subquery.append(Fragment.lit(" WHERE ")).append(simple.whereFragment().get());
          }
          if (simple.orderByFragment().isPresent()) {
            subquery =
                subquery.append(Fragment.lit(" ORDER BY ")).append(simple.orderByFragment().get());
          }
          if (simple.offset().isPresent()) {
            subquery = subquery.append(Fragment.lit(" OFFSET " + simple.offset().get()));
          }
          if (simple.limit().isPresent()) {
            subquery = subquery.append(Fragment.lit(" LIMIT " + simple.limit().get()));
          }

          yield subquery.append(Fragment.lit(") ")).append(Fragment.lit(simple.alias()));
        }
        case CompositeTableState composite -> {
          // For composite (joined) tables, we need to render as a subquery
          Query<?, ?> innerQuery = composite.innerQuery();
          List<TableState> innerTables = innerQuery.allTables();

          Map<String, String> innerAliasMap = new HashMap<>();
          for (TableState table1 : innerTables) {
            innerAliasMap.put(table1.alias(), table1.alias());
          }
          RenderCtx innerCtx =
              composite.renderCtx().withJoinContext(true).withAliasToCteMap(innerAliasMap);

          // SELECT only columns from the composite that are needed
          List<Fragment> colFragments = new ArrayList<>();
          for (TableState table1 : innerTables) {
            for (ColumnTuple col : table1.columns()) {
              String uniqueAlias = table1.alias() + "_" + col.column().name();
              Fragment colRef =
                  Fragment.lit(
                      table1.alias()
                          + "."
                          + dialect.quoteIdent(col.column().name())
                          + " AS "
                          + uniqueAlias);
              colFragments.add(colRef);
            }
          }
          Fragment select = Fragment.lit("SELECT ").append(Fragment.comma(colFragments));
          Fragment from =
              Fragment.lit(" FROM ")
                  .append(renderTableRefOptimized(innerQuery.firstTable(), dialect, counter));

          Fragment joins = Fragment.empty();
          for (JoinInfo join : innerQuery.joins()) {
            String joinType = join.isLeftJoin() ? " LEFT JOIN " : " JOIN ";
            joins =
                joins
                    .append(Fragment.lit(joinType))
                    .append(renderTableRefOptimized(join.table(), dialect, counter))
                    .append(Fragment.lit(" ON "))
                    .append(join.onCondition().apply(innerCtx, counter));
          }

          yield Fragment.interpolate(
              Fragment.lit("("),
              select,
              from,
              joins,
              Fragment.lit(") "),
              Fragment.lit(innerTables.getFirst().alias()));
        }
        case GroupedTableState grouped -> // Delegate to the standard grouped table rendering
            SelectBuilderSql.renderGroupedTableRef(
                grouped, dialect, counter, this::renderTableRefOptimized);
        case ProjectedTableState projected ->
            throw new IllegalStateException(
                "ProjectedTableState should not appear in underlying query");
      };
    }

    private RowParser<NewRow> buildRowParser() {
      List<DbType<?>> dbTypes = new ArrayList<>();
      List<Integer> exprColumnCounts = new ArrayList<>();

      for (SqlExpr<?> expr : projectedExprs) {
        exprColumnCounts.add(expr.columnCount());
        dbTypes.addAll(expr.flattenedDbTypes());
      }

      return new RowParser<>(
          dbTypes,
          values -> createNestedTuple(values, projectedExprs, exprColumnCounts),
          tuple -> {
            // Use the generated asArray() method - no reflection needed
            return tuple.asArray();
          });
    }

    @SuppressWarnings("unchecked")
    private NewRow createNestedTuple(
        Object[] values, List<SqlExpr<?>> exprs, List<Integer> columnCounts) {
      // Reconstruct nested tuple structure from flat column values
      Object[] tupleElements = new Object[exprs.size()];
      int valueIndex = 0;

      for (int i = 0; i < exprs.size(); i++) {
        int count = columnCounts.get(i);
        if (count == 1) {
          tupleElements[i] = values[valueIndex++];
        } else {
          // This is a TupleExpr - reconstruct the nested tuple
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

  /**
   * A table state that wraps an entire query and projects specific expressions. Supports both
   * column references (FieldLike) and computed expressions.
   */
  record ProjectedTableState(
      Query<?, ?> wrappedQuery,
      List<SqlExpr<?>> projectedExprs,
      RenderCtx renderCtx,
      AtomicInteger counter)
      implements TableState {
    @Override
    public String alias() {
      return "projected";
    }

    @Override
    public List<ColumnTuple> columns() {
      // For projected expressions, we can't return meaningful columns
      // This is mainly used for composite table rendering
      return List.of();
    }

    @Override
    public boolean isComposite() {
      return true;
    }
  }

  /** A minimal Structure implementation for projected queries. */
  record ProjectedStructure<
          NewFields extends Tuples.TupleExpr<NewRow>, NewRow extends Tuples.Tuple>(
      NewFields fields, List<SqlExpr<?>> projectedExprs) implements Structure<NewFields, NewRow> {

    @Override
    public List<SqlExpr.FieldLike<?, ?>> allFields() {
      // Return only the FieldLike expressions (column references)
      // Computed expressions don't have column names
      return projectedExprs.stream()
          .filter(e -> e instanceof SqlExpr.FieldLike<?, ?>)
          .map(f -> (SqlExpr.FieldLike<?, ?>) f)
          .collect(Collectors.toList());
    }

    @Override
    public List<Path> _path() {
      return List.of();
    }

    @Override
    public Structure<NewFields, NewRow> withPath(Path path) {
      return this; // Path changes don't affect projected structure
    }

    @Override
    public <T> Optional<T> untypedGetFieldValue(SqlExpr.FieldLike<T, ?> field, NewRow row) {
      // Find the index of the field in projectedExprs
      for (int i = 0; i < projectedExprs.size(); i++) {
        var expr = projectedExprs.get(i);
        if (expr instanceof SqlExpr.FieldLike<?, ?> pf) {
          if (pf._path().equals(field._path()) && pf.column().equals(field.column())) {
            // Extract value from tuple at index i
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
   * SelectBuilder for multiset (one-to-many) joins that aggregate the right side into a typed List.
   *
   * <p>Generates SQL like:
   *
   * <pre>
   * SELECT p.*,
   *        (SELECT json_agg(jsonb_build_object('id', e.id, 'email', e.email))
   *         FROM emails e WHERE e.person_id = p.id) as child_data
   * FROM persons p
   * </pre>
   *
   * <p>The JSON is then parsed at runtime using the child RowParser to produce typed Row2 objects.
   */
  static class MultisetSelectBuilder<Fields1, Row1, Fields2, Row2>
      extends SelectBuilderSql<
          typr.dsl.Tuple2<Fields1, Fields2>, typr.dsl.Tuple2<Row1, List<Row2>>> {

    private final SelectBuilderSql<Fields1, Row1> parentBuilder;
    private final SelectBuilderSql<Fields2, Row2> childBuilder;
    private final Function<typr.dsl.Tuple2<Fields1, Fields2>, SqlExpr<Boolean>> correlationPred;
    private final SelectParams<typr.dsl.Tuple2<Fields1, Fields2>, typr.dsl.Tuple2<Row1, List<Row2>>>
        params;

    MultisetSelectBuilder(
        SelectBuilderSql<Fields1, Row1> parentBuilder,
        SelectBuilderSql<Fields2, Row2> childBuilder,
        Function<typr.dsl.Tuple2<Fields1, Fields2>, SqlExpr<Boolean>> correlationPred,
        SelectParams<typr.dsl.Tuple2<Fields1, Fields2>, typr.dsl.Tuple2<Row1, List<Row2>>> params) {
      this.parentBuilder = parentBuilder;
      this.childBuilder = childBuilder;
      this.correlationPred = correlationPred;
      this.params = params;
    }

    @Override
    public Dialect dialect() {
      return parentBuilder.dialect();
    }

    @Override
    public Structure<typr.dsl.Tuple2<Fields1, Fields2>, typr.dsl.Tuple2<Row1, List<Row2>>>
        structure() {
      return new MultisetStructure<>(parentBuilder.structure(), childBuilder.structure());
    }

    @Override
    public SelectParams<typr.dsl.Tuple2<Fields1, Fields2>, typr.dsl.Tuple2<Row1, List<Row2>>>
        params() {
      return params;
    }

    @Override
    public SelectBuilder<typr.dsl.Tuple2<Fields1, Fields2>, typr.dsl.Tuple2<Row1, List<Row2>>>
        withParams(
            SelectParams<typr.dsl.Tuple2<Fields1, Fields2>, typr.dsl.Tuple2<Row1, List<Row2>>>
                newParams) {
      return new MultisetSelectBuilder<>(parentBuilder, childBuilder, correlationPred, newParams);
    }

    @Override
    public SelectBuilderSql<typr.dsl.Tuple2<Fields1, Fields2>, typr.dsl.Tuple2<Row1, List<Row2>>>
        withPath(Path path) {
      return new MultisetSelectBuilder<>(
          parentBuilder.withPath(path), childBuilder.withPath(path), correlationPred, params);
    }

    /** Get the child column names in order for JSON parsing. */
    private List<String> getChildColumnNames() {
      RenderCtx childCtx = RenderCtx.from(childBuilder, dialect());
      Query<Fields2, Row2> childQuery = childBuilder.collectQuery(childCtx, new AtomicInteger(0));
      List<String> columnNames = new ArrayList<>();
      for (TableState table : childQuery.allTables()) {
        for (ColumnTuple col : table.columns()) {
          columnNames.add(col.column().column());
        }
      }
      return columnNames;
    }

    /** Get the child row parser for JSON parsing. */
    private RowParser<Row2> getChildRowParser() {
      RenderCtx childCtx = RenderCtx.from(childBuilder, dialect());
      Query<Fields2, Row2> childQuery = childBuilder.collectQuery(childCtx, new AtomicInteger(0));
      return childQuery.rowParser().apply(1);
    }

    @Override
    public Query<typr.dsl.Tuple2<Fields1, Fields2>, typr.dsl.Tuple2<Row1, List<Row2>>> collectQuery(
        RenderCtx ctx, AtomicInteger counter) {
      // For multiset, we collect the parent query normally,
      // but the SQL rendering is custom (done in getSqlAndRowParser)
      Query<Fields1, Row1> parentQuery = parentBuilder.collectQuery(ctx, counter);

      // Get child parser info for JSON decoding
      RowParser<Row2> childRowParser = getChildRowParser();
      List<String> childColumnNames = getChildColumnNames();

      // Create a row parser that parses parent rows plus JSON decoded to List<Row2>
      Function<Integer, RowParser<Row1>> parentParser = parentQuery.rowParser();
      int parentColCount = computeColumnCount(parentQuery.allTables());

      Function<Integer, RowParser<typr.dsl.Tuple2<Row1, List<Row2>>>> combinedParser =
          startCol -> {
            RowParser<Row1> r1Parser = parentParser.apply(startCol);

            // Add the JSON column at the end (we read as text then parse)
            List<DbType<?>> allDbTypes = new ArrayList<>(r1Parser.columns());
            allDbTypes.add(typr.runtime.PgTypes.text);

            Function<Object[], typr.dsl.Tuple2<Row1, List<Row2>>> decode =
                values -> {
                  // Parent values are 0 to parentColCount-1, JSON text is at parentColCount
                  Object[] parentValues = new Object[parentColCount];
                  System.arraycopy(values, 0, parentValues, 0, parentColCount);
                  Row1 parentRow = r1Parser.decode().apply(parentValues);
                  String jsonStr = (String) values[parentColCount];
                  List<Row2> childRows = childRowParser.parseJsonArray(jsonStr, childColumnNames);
                  return typr.dsl.Tuple2.of(parentRow, childRows);
                };

            Function<typr.dsl.Tuple2<Row1, List<Row2>>, Object[]> encode =
                tuple -> {
                  Object[] parentValues = r1Parser.encode().apply(tuple._1());
                  Object[] allValues = new Object[parentValues.length + 1];
                  System.arraycopy(parentValues, 0, allValues, 0, parentValues.length);
                  // Can't easily encode List<Row2> back to JSON, so we just store null
                  allValues[parentValues.length] = null;
                  return allValues;
                };

            return new RowParser<>(allDbTypes, decode, encode);
          };

      return new Query<>(
          parentQuery.firstTable(),
          parentQuery.joins(),
          SelectParams.empty(),
          structure().fields(),
          combinedParser);
    }

    @Override
    protected Tuple2<Fragment, RowParser<typr.dsl.Tuple2<Row1, List<Row2>>>> getSqlAndRowParser() {
      RenderCtx parentCtx = RenderCtx.from(parentBuilder, dialect());
      AtomicInteger counter = new AtomicInteger(0);

      Query<Fields1, Row1> parentQuery = parentBuilder.collectQuery(parentCtx, counter);

      // Create a separate context for the child query to avoid alias conflicts
      RenderCtx childCtx = RenderCtx.from(childBuilder, dialect());
      Query<Fields2, Row2> childQuery = childBuilder.collectQuery(childCtx, counter);

      Dialect dialect = parentCtx.dialect();
      List<TableState> parentTables = parentQuery.allTables();

      // Build alias maps for the outer query
      Map<String, String> parentAliasMap = buildSimpleAliasMap(parentTables);
      RenderCtx outerCtx = parentCtx.withJoinContext(true).withAliasToCteMap(parentAliasMap);

      // SELECT: all parent columns plus the correlated subquery
      List<Fragment> colFragments = new ArrayList<>();

      // Parent columns
      for (TableState table : parentTables) {
        if (table instanceof ProjectedTableState projected) {
          // For projected tables, reference the projected column aliases (proj_0, proj_1, ...)
          // The flattened column count accounts for nested TupleExpr
          int idx = 0;
          for (SqlExpr<?> expr : projected.projectedExprs()) {
            if (expr instanceof Tuples.TupleExpr<?> tupleExpr) {
              // Flatten nested TupleExpr
              for (SqlExpr<?> subExpr : tupleExpr.exprs()) {
                String colName = "proj_" + idx;
                Fragment colRef = Fragment.lit(projected.alias() + "." + colName);
                colFragments.add(colRef);
                idx++;
              }
            } else {
              String colName = "proj_" + idx;
              Fragment colRef = Fragment.lit(projected.alias() + "." + colName);
              colFragments.add(colRef);
              idx++;
            }
          }
        } else if (table.isComposite()) {
          for (ColumnTuple col : table.columns()) {
            String uniqueColName = col.alias() + "_" + col.column().name();
            Fragment baseRef = Fragment.lit(table.alias() + "." + uniqueColName);
            Fragment colRef =
                col.column()
                    .sqlReadCast()
                    .map(cast -> dialect.typeCast(baseRef, cast))
                    .orElse(baseRef);
            colFragments.add(colRef);
          }
        } else {
          for (ColumnTuple col : table.columns()) {
            Fragment baseRef =
                Fragment.lit(table.alias() + "." + dialect.quoteIdent(col.column().name()));
            Fragment colRef =
                col.column()
                    .sqlReadCast()
                    .map(cast -> dialect.typeCast(baseRef, cast))
                    .orElse(baseRef);
            colFragments.add(colRef);
          }
        }
      }

      // Build the correlated subquery for the multiset
      Fragment multisetSubquery =
          buildMultisetSubquery(parentQuery, childQuery, outerCtx, childCtx, counter, dialect);
      colFragments.add(multisetSubquery);

      Fragment select = Fragment.lit("select ").append(Fragment.comma(colFragments));

      // FROM: first table
      Fragment from =
          Fragment.lit("\nfrom ")
              .append(renderTableRefForMultiset(parentQuery.firstTable(), dialect, counter));

      // JOINs for parent
      Fragment joins = Fragment.empty();
      for (JoinInfo join : parentQuery.joins()) {
        String joinType = join.isLeftJoin() ? "left join" : "join";
        joins =
            joins
                .append(Fragment.lit("\n" + joinType + " "))
                .append(renderTableRefForMultiset(join.table(), dialect, counter))
                .append(Fragment.lit("\n  on "))
                .append(join.onCondition().apply(outerCtx, counter));
      }

      // WHERE, ORDER BY, LIMIT, OFFSET for outer query
      Fragment whereFrag = Fragment.empty();
      Fragment orderByFrag = Fragment.empty();
      Fragment limitFrag = Fragment.empty();
      Fragment offsetFrag = Fragment.empty();

      if (!params.where().isEmpty()
          || !params.orderBy().isEmpty()
          || params.limit().isPresent()
          || params.offset().isPresent()) {

        var expanded = OrderByOrSeek.expand(structure().fields(), params);

        whereFrag =
            expanded
                .combinedFilter()
                .map(
                    combined -> Fragment.lit("\nwhere ").append(combined.render(outerCtx, counter)))
                .orElse(Fragment.empty());

        if (!expanded.orderBys().isEmpty()) {
          List<Fragment> orderFragments = new ArrayList<>();
          for (SortOrder<?> order : expanded.orderBys()) {
            orderFragments.add(order.render(outerCtx, counter));
          }
          orderByFrag = Fragment.lit("\norder by ").append(Fragment.comma(orderFragments));
        }

        if (params.offset().isPresent()) {
          offsetFrag = Fragment.lit("\noffset " + params.offset().get());
        }

        if (params.limit().isPresent()) {
          limitFrag = Fragment.lit("\nlimit " + params.limit().get());
        }
      }

      Fragment sql =
          select
              .append(from)
              .append(joins)
              .append(whereFrag)
              .append(orderByFrag)
              .append(offsetFrag)
              .append(limitFrag);

      // Build row parser
      Query<typr.dsl.Tuple2<Fields1, Fields2>, typr.dsl.Tuple2<Row1, List<Row2>>> fullQuery =
          collectQuery(parentCtx, new AtomicInteger(0));
      RowParser<typr.dsl.Tuple2<Row1, List<Row2>>> rowParser = fullQuery.rowParser().apply(1);

      return new Tuple2<>(sql, rowParser);
    }

    /**
     * Build the correlated subquery that produces JSON for the child rows.
     *
     * <p>PostgreSQL: (SELECT COALESCE(json_agg(jsonb_build_object(...)), '[]'::json) FROM ...)
     *
     * <p>MariaDB: (SELECT COALESCE(JSON_ARRAYAGG(JSON_OBJECT(...)), JSON_ARRAY()) FROM ...)
     */
    private Fragment buildMultisetSubquery(
        Query<Fields1, Row1> parentQuery,
        Query<Fields2, Row2> childQuery,
        RenderCtx parentCtx,
        RenderCtx childBaseCtx,
        AtomicInteger counter,
        Dialect dialect) {

      List<TableState> parentTables = parentQuery.allTables();
      List<TableState> childTables = childQuery.allTables();

      // Build the JSON object construction for all child columns
      List<Fragment> jsonFields = new ArrayList<>();
      for (TableState table : childTables) {
        for (ColumnTuple col : table.columns()) {
          String colName = col.column().column();
          Fragment colRef = Fragment.lit(table.alias() + "." + dialect.quoteIdent(colName));
          // Add key-value pair: 'column_name', column_value
          jsonFields.add(Fragment.lit("'" + colName + "'"));
          jsonFields.add(colRef);
        }
      }

      // Build JSON aggregation based on dialect
      Fragment jsonAgg;
      if (dialect == Dialect.MARIADB) {
        // MariaDB: JSON_ARRAYAGG(JSON_OBJECT('key1', val1, 'key2', val2, ...))
        Fragment jsonObject =
            Fragment.lit("JSON_OBJECT(")
                .append(Fragment.comma(jsonFields))
                .append(Fragment.lit(")"));
        jsonAgg =
            Fragment.lit("COALESCE(JSON_ARRAYAGG(")
                .append(jsonObject)
                .append(Fragment.lit("), JSON_ARRAY())"));
      } else {
        // PostgreSQL: json_agg(jsonb_build_object('key1', val1, 'key2', val2, ...))
        Fragment jsonObject =
            Fragment.lit("jsonb_build_object(")
                .append(Fragment.comma(jsonFields))
                .append(Fragment.lit(")"));
        jsonAgg =
            Fragment.lit("COALESCE(json_agg(")
                .append(jsonObject)
                .append(Fragment.lit("), '[]'::json)"));
      }

      // Build FROM clause for child
      Fragment childFrom =
          Fragment.lit(" FROM ")
              .append(renderTableRefForMultiset(childQuery.firstTable(), dialect, counter));

      // Child JOINs - use child context for join conditions within the child query
      Map<String, String> childOnlyAliasMap = new HashMap<>();
      for (TableState table : childTables) {
        childOnlyAliasMap.put(table.alias(), table.alias());
      }
      RenderCtx childOnlyCtx = childBaseCtx.withAliasToCteMap(childOnlyAliasMap);

      Fragment childJoins = Fragment.empty();
      for (JoinInfo join : childQuery.joins()) {
        String joinType = join.isLeftJoin() ? " LEFT JOIN " : " JOIN ";
        childJoins =
            childJoins
                .append(Fragment.lit(joinType))
                .append(renderTableRefForMultiset(join.table(), dialect, counter))
                .append(Fragment.lit(" ON "))
                .append(join.onCondition().apply(childOnlyCtx, counter));
      }

      // Build the correlation predicate (WHERE clause)
      // The correlation predicate compares fields from parent and child.
      // We need to render parent fields with the parent alias and child fields with the child
      // alias.
      //
      // The key insight is that the correlation expression uses fields from both structures.
      // When rendered, each field looks up its path to find the alias.
      // Parent fields have paths from parentBuilder, child fields have paths from childBuilder.
      // We need a combined context that can resolve both.

      String parentAlias = parentTables.getFirst().alias();
      String childAlias = childTables.getFirst().alias();

      // Build the WHERE clause by rendering the correlation expression
      // We need to use structures with distinct paths so the context can tell them apart
      Structure<Fields1, Row1> parentStructureWithPath =
          parentBuilder.structure().withPath(Path.of("parent"));
      Structure<Fields2, Row2> childStructureWithPath =
          childBuilder.structure().withPath(Path.of("child"));

      Structure<typr.dsl.Tuple2<Fields1, Fields2>, typr.dsl.Tuple2<Row1, Row2>>
          correlationStructure = parentStructureWithPath.join(childStructureWithPath);

      SqlExpr<Boolean> correlationExpr = correlationPred.apply(correlationStructure.fields());

      // Build a custom context for rendering the correlation expression
      RenderCtx correlationCtx;

      // Check if parent is a projected table - if so, build projected expression map
      TableState parentFirstTable = parentTables.getFirst();
      if (parentFirstTable instanceof ProjectedTableState projected) {
        // Build a map from projected expressions (by identity) to their column references
        IdentityHashMap<SqlExpr<?>, String> projectedExprMap = new IdentityHashMap<>();
        int idx = 0;
        for (SqlExpr<?> expr : projected.projectedExprs()) {
          if (expr instanceof Tuples.TupleExpr<?> tupleExpr) {
            // Flatten nested TupleExpr
            for (SqlExpr<?> subExpr : tupleExpr.exprs()) {
              String colRef = projected.alias() + ".proj_" + idx;
              projectedExprMap.put(subExpr, colRef);
              idx++;
            }
          } else {
            String colRef = projected.alias() + ".proj_" + idx;
            projectedExprMap.put(expr, colRef);
            idx++;
          }
        }

        correlationCtx =
            RenderCtx.forProjectedCorrelation(
                projectedExprMap, List.of(Path.of("child")), childAlias, dialect);
      } else {
        correlationCtx =
            RenderCtx.forCorrelation(
                List.of(Path.of("parent")),
                parentAlias,
                List.of(Path.of("child")),
                childAlias,
                dialect);
      }

      Fragment whereClause =
          Fragment.lit(" WHERE ").append(correlationExpr.render(correlationCtx, counter));

      // Combine into subquery
      return Fragment.lit("(SELECT ")
          .append(jsonAgg)
          .append(childFrom)
          .append(childJoins)
          .append(whereClause)
          .append(Fragment.lit(")"));
    }

    private Fragment renderTableRefForMultiset(
        TableState table, Dialect dialect, AtomicInteger counter) {
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
              Fragment.lit("(SELECT * FROM ")
                  .append(Fragment.lit(dialect.quoteTableName(simple.tableName())))
                  .append(Fragment.lit(" "))
                  .append(Fragment.lit(simple.alias()));

          if (simple.whereFragment().isPresent()) {
            subquery =
                subquery.append(Fragment.lit(" WHERE ")).append(simple.whereFragment().get());
          }
          if (simple.orderByFragment().isPresent()) {
            subquery =
                subquery.append(Fragment.lit(" ORDER BY ")).append(simple.orderByFragment().get());
          }
          if (simple.offset().isPresent()) {
            subquery = subquery.append(Fragment.lit(" OFFSET " + simple.offset().get()));
          }
          if (simple.limit().isPresent()) {
            subquery = subquery.append(Fragment.lit(" LIMIT " + simple.limit().get()));
          }

          yield subquery.append(Fragment.lit(") ")).append(Fragment.lit(simple.alias()));
        }
        case CompositeTableState composite -> {
          Query<?, ?> innerQuery = composite.innerQuery();
          List<TableState> allTables = innerQuery.allTables();

          RenderCtx innerCtx =
              composite
                  .renderCtx()
                  .withJoinContext(true)
                  .withAliasToCteMap(buildSimpleAliasMap(allTables));

          // Build SELECT list: all columns from all tables
          List<Fragment> colFragments = new ArrayList<>();
          for (TableState table1 : allTables) {
            for (ColumnTuple col : table1.columns()) {
              String uniqueAlias = table1.alias() + "_" + col.column().name();
              Fragment colRef =
                  Fragment.lit(
                      table1.alias()
                          + "."
                          + dialect.quoteIdent(col.column().name())
                          + " AS "
                          + uniqueAlias);
              colFragments.add(colRef);
            }
          }
          Fragment select = Fragment.lit("SELECT ").append(Fragment.comma(colFragments));

          // FROM first table
          Fragment from =
              Fragment.lit(" FROM ")
                  .append(renderTableRefForMultiset(innerQuery.firstTable(), dialect, counter));

          // JOINs
          Fragment joins = Fragment.empty();
          for (JoinInfo join : innerQuery.joins()) {
            String joinType = join.isLeftJoin() ? " LEFT JOIN " : " JOIN ";
            joins =
                joins
                    .append(Fragment.lit(joinType))
                    .append(renderTableRefForMultiset(join.table(), dialect, counter))
                    .append(Fragment.lit(" ON "))
                    .append(join.onCondition().apply(innerCtx, counter));
          }

          // The composite keeps the first table's alias as its outer alias
          String compositeAlias = allTables.getFirst().alias();

          yield Fragment.lit("(")
              .append(select)
              .append(from)
              .append(joins)
              .append(Fragment.lit(") "))
              .append(Fragment.lit(compositeAlias));
        }
        case GroupedTableState grouped -> // Delegate to the standard grouped table rendering
            SelectBuilderSql.renderGroupedTableRef(
                grouped, dialect, counter, this::renderTableRefForMultiset);
        case ProjectedTableState projected -> {
          Query<?, ?> wrappedQuery = projected.wrappedQuery();
          List<SqlExpr<?>> projectedExprs = projected.projectedExprs();
          RenderCtx projCtx = projected.renderCtx();

          // Build SELECT list from projected expressions
          // Handle nested TupleExpr by flattening - each leaf expression gets its own alias
          List<Fragment> selectFragments = new ArrayList<>();
          int idx = 0;
          for (SqlExpr<?> expr : projectedExprs) {
            if (expr instanceof Tuples.TupleExpr<?> tupleExpr) {
              // Flatten TupleExpr: each sub-expression gets its own alias
              for (SqlExpr<?> subExpr : tupleExpr.exprs()) {
                Fragment subFrag = subExpr.render(projCtx, counter);
                String alias = "proj_" + idx;
                selectFragments.add(subFrag.append(Fragment.lit(" AS " + alias)));
                idx++;
              }
            } else {
              Fragment exprFrag = expr.render(projCtx, counter);
              String alias = "proj_" + idx;
              selectFragments.add(exprFrag.append(Fragment.lit(" AS " + alias)));
              idx++;
            }
          }
          Fragment select = Fragment.lit("SELECT ").append(Fragment.comma(selectFragments));

          // FROM the wrapped query (rendered as a subquery)
          List<TableState> wrappedTables = wrappedQuery.allTables();
          Fragment from =
              Fragment.lit(" FROM ")
                  .append(renderTableRefForMultiset(wrappedQuery.firstTable(), dialect, counter));

          RenderCtx wrappedCtx =
              projCtx.withJoinContext(true).withAliasToCteMap(buildSimpleAliasMap(wrappedTables));

          Fragment joins = Fragment.empty();
          for (JoinInfo join : wrappedQuery.joins()) {
            String joinType = join.isLeftJoin() ? " LEFT JOIN " : " JOIN ";
            joins =
                joins
                    .append(Fragment.lit(joinType))
                    .append(renderTableRefForMultiset(join.table(), dialect, counter))
                    .append(Fragment.lit(" ON "))
                    .append(join.onCondition().apply(wrappedCtx, counter));
          }

          String projectedAlias = projected.alias();

          yield Fragment.lit("(")
              .append(select)
              .append(from)
              .append(joins)
              .append(Fragment.lit(") "))
              .append(Fragment.lit(projectedAlias));
        }
      };
    }
  }

  /** Structure for multiset queries where the right side becomes a typed List column. */
  record MultisetStructure<Fields1, Row1, Fields2, Row2>(
      Structure<Fields1, Row1> parentStructure, Structure<Fields2, Row2> childStructure)
      implements Structure<typr.dsl.Tuple2<Fields1, Fields2>, typr.dsl.Tuple2<Row1, List<Row2>>> {

    MultisetStructure(
        Structure<Fields1, Row1> parentStructure, Structure<Fields2, Row2> childStructure) {
      this.parentStructure = parentStructure;
      this.childStructure = childStructure;
    }

    @Override
    public typr.dsl.Tuple2<Fields1, Fields2> fields() {
      return typr.dsl.Tuple2.of(parentStructure.fields(), childStructure.fields());
    }

    @Override
    public List<SqlExpr.FieldLike<?, ?>> allFields() {
      // Return parent fields (the child list column is synthetic and not backed by a field)
      return new ArrayList<>(parentStructure.allFields());
    }

    @Override
    public List<Path> _path() {
      return parentStructure._path();
    }

    @Override
    public Structure<typr.dsl.Tuple2<Fields1, Fields2>, typr.dsl.Tuple2<Row1, List<Row2>>> withPath(
        Path _path) {
      return new MultisetStructure<>(
          parentStructure.withPath(_path), childStructure.withPath(_path));
    }

    @Override
    public <T> Optional<T> untypedGetFieldValue(
        SqlExpr.FieldLike<T, ?> field, typr.dsl.Tuple2<Row1, List<Row2>> row) {
      // Try to get from parent structure
      return parentStructure.untypedGetFieldValue(field, row._1());
    }
  }

  /**
   * A SelectBuilder that wraps another builder and applies a bijection to transform the row type.
   * This is useful for language wrappers (Scala, Kotlin) that need to convert between Java types
   * (like Optional) and language-native types (like scala.Option).
   */
  static class Rewritten<Fields, OuterRow, Row> extends SelectBuilderSql<Fields, Row> {
    private final SelectBuilderSql<Fields, OuterRow> outer;
    private final Bijection<OuterRow, Row> bijection;

    public Rewritten(SelectBuilderSql<Fields, OuterRow> outer, Bijection<OuterRow, Row> bijection) {
      this.outer = outer;
      this.bijection = bijection;
    }

    @Override
    public Dialect dialect() {
      return outer.dialect();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Structure<Fields, Row> structure() {
      // Structure<Fields, Row> vs Structure<Fields, OuterRow> - the Row type parameter is only used
      // for RowParser creation, which we override via collectQuery anyway
      return (Structure<Fields, Row>) (Structure<?, ?>) outer.structure();
    }

    @Override
    @SuppressWarnings("unchecked")
    public SelectParams<Fields, Row> params() {
      return (SelectParams<Fields, Row>) (SelectParams<?, ?>) outer.params();
    }

    @Override
    @SuppressWarnings("unchecked")
    public SelectBuilder<Fields, Row> withParams(SelectParams<Fields, Row> newParams) {
      SelectParams<Fields, OuterRow> outerParams =
          (SelectParams<Fields, OuterRow>) (SelectParams<?, ?>) newParams;
      SelectBuilderSql<Fields, OuterRow> newOuter =
          (SelectBuilderSql<Fields, OuterRow>) outer.withParams(outerParams);
      return new Rewritten<>(newOuter, bijection);
    }

    @Override
    public SelectBuilderSql<Fields, Row> withPath(Path path) {
      return new Rewritten<>(outer.withPath(path), bijection);
    }

    @Override
    public Query<Fields, Row> collectQuery(RenderCtx ctx, AtomicInteger counter) {
      Query<Fields, OuterRow> outerQuery = outer.collectQuery(ctx, counter);
      Function<Integer, RowParser<Row>> transformedParser =
          colOffset -> {
            RowParser<OuterRow> outerParser = outerQuery.rowParser().apply(colOffset);
            return outerParser.to(bijection);
          };
      @SuppressWarnings("unchecked")
      SelectParams<Fields, Row> params =
          (SelectParams<Fields, Row>) (SelectParams<?, ?>) outerQuery.topLevelParams();
      return new Query<>(
          outerQuery.firstTable(),
          outerQuery.joins(),
          params,
          outerQuery.fields(),
          transformedParser);
    }

    @Override
    public RenderCtx renderCtx() {
      return outer.renderCtx();
    }

    @Override
    public List<Row> toList(Connection connection) {
      List<OuterRow> outerResults = outer.toList(connection);
      List<Row> results = new ArrayList<>(outerResults.size());
      for (OuterRow outerRow : outerResults) {
        results.add(bijection.underlying(outerRow));
      }
      return results;
    }

    @Override
    public int count(Connection connection) {
      return outer.count(connection);
    }

    @Override
    public Optional<Fragment> sql() {
      return outer.sql();
    }

    // Join operations: run on outer, then wrap result with a new Rewritten using composed bijection
    @Override
    public <Fields2, Row2>
        SelectBuilder<typr.dsl.Tuple2<Fields, Fields2>, typr.dsl.Tuple2<Row, Row2>> joinOn(
            SelectBuilder<Fields2, Row2> other,
            Function<typr.dsl.Tuple2<Fields, Fields2>, SqlExpr<Boolean>> pred) {
      SelectBuilder<typr.dsl.Tuple2<Fields, Fields2>, typr.dsl.Tuple2<OuterRow, Row2>> outerResult =
          outer.joinOn(other, pred);
      Bijection<typr.dsl.Tuple2<OuterRow, Row2>, typr.dsl.Tuple2<Row, Row2>> tupleBijection =
          Bijection.of(
              tuple -> new typr.dsl.Tuple2<>(bijection.underlying(tuple._1()), tuple._2()),
              tuple -> new typr.dsl.Tuple2<>(bijection.from(tuple._1()), tuple._2()));
      @SuppressWarnings("unchecked")
      SelectBuilderSql<typr.dsl.Tuple2<Fields, Fields2>, typr.dsl.Tuple2<OuterRow, Row2>> outerSql =
          (SelectBuilderSql<typr.dsl.Tuple2<Fields, Fields2>, typr.dsl.Tuple2<OuterRow, Row2>>)
              outerResult;
      return new Rewritten<>(outerSql, tupleBijection);
    }

    @Override
    public <Fields2, Row2>
        SelectBuilder<typr.dsl.Tuple2<Fields, Fields2>, typr.dsl.Tuple2<Row, Optional<Row2>>>
            leftJoinOn(
                SelectBuilder<Fields2, Row2> other,
                Function<typr.dsl.Tuple2<Fields, Fields2>, SqlExpr<Boolean>> pred) {
      SelectBuilder<typr.dsl.Tuple2<Fields, Fields2>, typr.dsl.Tuple2<OuterRow, Optional<Row2>>>
          outerResult = outer.leftJoinOn(other, pred);
      Bijection<typr.dsl.Tuple2<OuterRow, Optional<Row2>>, typr.dsl.Tuple2<Row, Optional<Row2>>>
          tupleBijection =
              Bijection.of(
                  tuple -> new typr.dsl.Tuple2<>(bijection.underlying(tuple._1()), tuple._2()),
                  tuple -> new typr.dsl.Tuple2<>(bijection.from(tuple._1()), tuple._2()));
      @SuppressWarnings("unchecked")
      SelectBuilderSql<typr.dsl.Tuple2<Fields, Fields2>, typr.dsl.Tuple2<OuterRow, Optional<Row2>>>
          outerSql =
              (SelectBuilderSql<
                      typr.dsl.Tuple2<Fields, Fields2>, typr.dsl.Tuple2<OuterRow, Optional<Row2>>>)
                  outerResult;
      return new Rewritten<>(outerSql, tupleBijection);
    }

    @Override
    public <Fields2, Row2>
        SelectBuilder<typr.dsl.Tuple2<Fields, Fields2>, typr.dsl.Tuple2<Row, List<Row2>>>
            multisetOn(
                SelectBuilder<Fields2, Row2> other,
                Function<typr.dsl.Tuple2<Fields, Fields2>, SqlExpr<Boolean>> pred) {
      SelectBuilder<typr.dsl.Tuple2<Fields, Fields2>, typr.dsl.Tuple2<OuterRow, List<Row2>>>
          outerResult = outer.multisetOn(other, pred);
      Bijection<typr.dsl.Tuple2<OuterRow, List<Row2>>, typr.dsl.Tuple2<Row, List<Row2>>>
          tupleBijection =
              Bijection.of(
                  tuple -> new typr.dsl.Tuple2<>(bijection.underlying(tuple._1()), tuple._2()),
                  tuple -> new typr.dsl.Tuple2<>(bijection.from(tuple._1()), tuple._2()));
      @SuppressWarnings("unchecked")
      SelectBuilderSql<typr.dsl.Tuple2<Fields, Fields2>, typr.dsl.Tuple2<OuterRow, List<Row2>>>
          outerSql =
              (SelectBuilderSql<
                      typr.dsl.Tuple2<Fields, Fields2>, typr.dsl.Tuple2<OuterRow, List<Row2>>>)
                  outerResult;
      return new Rewritten<>(outerSql, tupleBijection);
    }

    @Override
    @SuppressWarnings("unchecked")
    public GroupedBuilder<Fields, Row> groupByExpr(Function<Fields, List<SqlExpr<?>>> groupKeys) {
      // GroupedBuilder doesn't expose Row in its interface (Row is lost after select())
      // so we can safely cast
      GroupedBuilder<Fields, OuterRow> outerGrouped = outer.groupByExpr(groupKeys);
      return (GroupedBuilder<Fields, Row>) outerGrouped;
    }
  }
}
