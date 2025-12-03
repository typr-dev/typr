package typo.dsl;

import typo.runtime.And;
import typo.runtime.Fragment;
import typo.runtime.RowParser;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * SQL implementation of SelectBuilder that generates and executes SQL queries.
 */
public abstract class SelectBuilderSql<Fields, Row> implements SelectBuilder<Fields, Row> {

    /**
     * Returns a copy with the specified path.
     */
    public abstract SelectBuilderSql<Fields, Row> withPath(Path path);

    /**
     * Get the dialect for this builder.
     */
    public abstract Dialect dialect();

    /**
     * Instantiate this builder with the given context and counter.
     */
    public abstract Instantiated<Fields, Row> instantiate(RenderCtx renderCtx, AtomicInteger counter);

    /**
     * Get the lazy SQL and row parser.
     */
    protected Tuple2<Fragment, RowParser<Row>> getSqlAndRowParser() {
        RenderCtx ctx = RenderCtx.from(this, dialect());
        Instantiated<Fields, Row> instance = instantiate(ctx, new AtomicInteger(0));
        Dialect dialect = ctx.dialect();

        List<CTE> ctes = instance.asCTEs();
        String lastCteName = ctes.get(ctes.size() - 1).name();

        // In the final SELECT, reference columns by their unique aliases from the last CTE
        List<Fragment> cols = instance.columns.stream()
            .map(columnTuple -> {
                SqlExpr.FieldLike<?, ?> col = columnTuple.column();
                // Reference via lastCteName.uniqueAlias
                Fragment baseCol = Fragment.lit(lastCteName + "." + columnTuple.uniqueAlias());
                // Apply SQL read casts if any
                return col.sqlReadCast()
                    .map(cast -> dialect.typeCast(baseCol, cast))
                    .orElse(baseCol);
            })
            .collect(Collectors.toList());

        List<Fragment> formattedCTEs = ctes.stream()
            .map(cte -> Fragment.lit(cte.name() + " as (\n  ")
                .append(cte.sql())
                .append(Fragment.lit("\n)")))
            .collect(Collectors.toList());

        Fragment frag = Fragment.lit("with \n")
            .append(Fragment.comma(formattedCTEs))
            .append(Fragment.lit("\nselect "))
            .append(Fragment.comma(cols))
            .append(Fragment.lit(" from "))
            .append(Fragment.lit(lastCteName));

        return new Tuple2<>(frag, instance.rowParser.apply(1));
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
        Fragment countQuery = Fragment.lit("select count(*) from (")
            .append(frag)
            .append(Fragment.lit(") rows"));
        
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
    public <Fields2, Row2> SelectBuilder<Structure.Tuple2<Fields, Fields2>, Structure.Tuple2<Row, Row2>> 
            joinOn(SelectBuilder<Fields2, Row2> other, Function<Structure.Tuple2<Fields, Fields2>, SqlExpr<Boolean>> pred) {
        
        if (!(other instanceof SelectBuilderSql<Fields2, Row2> otherSql)) {
            throw new IllegalArgumentException("Can only join with SQL-based SelectBuilder");
        }
        
        return new TableJoin<>(
            this.withPath(Path.LEFT_IN_JOIN),
            otherSql.withPath(Path.RIGHT_IN_JOIN),
            pred,
            SelectParams.empty()
        );
    }
    
    @Override
    public <Fields2, Row2> SelectBuilder<Structure.Tuple2<Fields, Fields2>, Structure.Tuple2<Row, Optional<Row2>>> 
            leftJoinOn(SelectBuilder<Fields2, Row2> other, Function<Structure.Tuple2<Fields, Fields2>, SqlExpr<Boolean>> pred) {
        
        if (!(other instanceof SelectBuilderSql<Fields2, Row2> otherSql)) {
            throw new IllegalArgumentException("Can only join with SQL-based SelectBuilder");
        }
        
        return new TableLeftJoin<>(
            this.withPath(Path.LEFT_IN_JOIN),
            otherSql.withPath(Path.RIGHT_IN_JOIN),
            pred,
            SelectParams.empty()
        );
    }
    
    /**
     * Quote a table name for SQL, handling schema.table format and special characters.
     * Splits by "." and quotes each part that contains non-alphanumeric characters.
     * If the part is already quoted (starts with " or `), it is used as-is.
     */
    private static String quoteTableName(String tableName, Dialect dialect) {
        String[] parts = tableName.split("\\.");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) result.append(".");
            String part = parts[i];
            if (isAlreadyQuoted(part)) {
                // Already quoted, use as-is
                result.append(part);
            } else if (needsQuoting(part)) {
                result.append(dialect.quoteIdent(dialect.escapeIdent(part)));
            } else {
                result.append(part);
            }
        }
        return result.toString();
    }

    private static boolean isAlreadyQuoted(String identifier) {
        if (identifier.length() < 2) return false;
        char first = identifier.charAt(0);
        char last = identifier.charAt(identifier.length() - 1);
        // Check for double-quote or backtick quoting
        return (first == '"' && last == '"') || (first == '`' && last == '`');
    }

    private static boolean needsQuoting(String identifier) {
        for (char c : identifier.toCharArray()) {
            if (!Character.isLetterOrDigit(c) && c != '_') {
                return true;
            }
        }
        return false;
    }

    /**
     * Tuple helper class.
     */
    record Tuple2<A, B>(A first, B second) {}
    
    /**
     * Column tuple for instantiated queries.
     */
    record ColumnTuple(String alias, SqlExpr.FieldLike<?, ?> column) {
        /**
         * Generate a unique alias for this column that won't clash with other tables.
         * Format: {table_alias}_{column_name}
         */
        public String uniqueAlias() {
            return alias + "_" + column.name();
        }
    }
    
    /**
     * Common Table Expression.
     */
    record CTE(String name, Fragment sql, boolean isJoin, List<ColumnTuple> columns) {}

    /**
     * Generate a SELECT column list with unique aliases.
     * Example: t0.col1 AS t0_col1, t0.col2 AS t0_col2
     */
    static String renderColumnList(List<ColumnTuple> columns, Dialect dialect) {
        return columns.stream()
            .map(ct -> dialect.columnRef(ct.alias(), dialect.quoteIdent(ct.column().name()))
                + " AS " + ct.uniqueAlias())
            .collect(Collectors.joining(", "));
    }

    /**
     * Generate a SELECT column list for a join, referencing columns from CTEs by their unique aliases.
     * Uses the alias-to-CTE map to resolve the correct CTE name for each column.
     * Example: join_cte1.left0_col1 AS left0_col1, right0.right0_col2 AS right0_col2
     */
    static String renderJoinColumnList(List<CTE> ctes, Map<String, String> aliasToCteMap) {
        List<String> columnRefs = new ArrayList<>();
        for (CTE cte : ctes) {
            if (!cte.isJoin()) {
                for (ColumnTuple ct : cte.columns()) {
                    // Resolve the CTE name using the alias-to-CTE map
                    String cteName = aliasToCteMap.getOrDefault(ct.alias(), cte.name());
                    // Reference the column by resolved CTE name and its unique alias
                    columnRefs.add(cteName + "." + ct.uniqueAlias() + " AS " + ct.uniqueAlias());
                }
            }
        }
        return String.join(", ", columnRefs);
    }
    
    /**
     * Instantiated query data structure.
     */
    record Instantiated<Fields, Row>(
            String alias,
            boolean isJoin,
            List<ColumnTuple> columns,
            Fragment sqlFrag,
            List<CTE> upstreamCTEs,
            Structure<Fields, Row> structure,
            Function<Integer, RowParser<Row>> rowParser
    ) {
        public List<CTE> asCTEs() {
            List<CTE> result = new ArrayList<>(upstreamCTEs);
            result.add(new CTE(alias, sqlFrag, isJoin, columns));
            return result;
        }
    }
    
    /**
     * Relation implementation.
     */
    static class Relation<Fields, Row> extends SelectBuilderSql<Fields, Row> {
        private final String tableName;
        private final Structure<Fields, Row> structure;
        private final Function<Integer, RowParser<Row>> rowParser;
        private final SelectParams<Fields, Row> params;
        private final Dialect dialect;

        public String name() { return tableName; }
        public Dialect dialect() { return dialect; }

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
            return new Relation<>(tableName, structure.withPath(path), rowParser.apply(1), params, dialect);
        }

        @Override
        public Instantiated<Fields, Row> instantiate(RenderCtx ctx, AtomicInteger counter) {
            String alias = ctx.alias(structure.path()).orElse("t0");
            Optional<Fragment> whereAndMore = SelectParams.render(
                structure.fields(),
                ctx,
                counter,
                params
            );

            List<ColumnTuple> columns = structure.columns().stream()
                .map(c -> new ColumnTuple(alias, c))
                .collect(Collectors.toList());

            // Generate explicit column list with unique aliases: t0.col1 AS t0_col1, ...
            String columnList = renderColumnList(columns, ctx.dialect());

            Fragment sql = Fragment.lit("(select ")
                .append(Fragment.lit(columnList))
                .append(Fragment.lit(" from "))
                .append(Fragment.lit(quoteTableName(tableName, ctx.dialect())))
                .append(Fragment.lit(" "))
                .append(Fragment.lit(alias))
                .append(Fragment.lit(" "))
                .append(whereAndMore.orElse(Fragment.empty()))
                .append(Fragment.lit(")"));

            return new Instantiated<>(
                alias,
                false,
                columns,
                sql,
                List.of(),
                structure,
                rowParser
            );
        }
    }
    
    /**
     * SelectBuilder for joined queries.
     */
    static class TableJoin<Fields1, Row1, Fields2, Row2>
            extends SelectBuilderSql<Structure.Tuple2<Fields1, Fields2>, Structure.Tuple2<Row1, Row2>> {

        private final SelectBuilderSql<Fields1, Row1> leftBuilder;
        private final SelectBuilderSql<Fields2, Row2> rightBuilder;
        private final Function<Structure.Tuple2<Fields1, Fields2>, SqlExpr<Boolean>> pred;
        private final SelectParams<Structure.Tuple2<Fields1, Fields2>, Structure.Tuple2<Row1, Row2>> params;

        public SelectBuilderSql<Fields1, Row1> left() { return leftBuilder; }
        public SelectBuilderSql<Fields2, Row2> right() { return rightBuilder; }

        public TableJoin(
                SelectBuilderSql<Fields1, Row1> left,
                SelectBuilderSql<Fields2, Row2> right,
                Function<Structure.Tuple2<Fields1, Fields2>, SqlExpr<Boolean>> pred,
                SelectParams<Structure.Tuple2<Fields1, Fields2>, Structure.Tuple2<Row1, Row2>> params) {
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
        public Structure<Structure.Tuple2<Fields1, Fields2>, Structure.Tuple2<Row1, Row2>> structure() {
            return leftBuilder.structure().join(rightBuilder.structure());
        }

        @Override
        public SelectParams<Structure.Tuple2<Fields1, Fields2>, Structure.Tuple2<Row1, Row2>> params() {
            return params;
        }

        @Override
        public SelectBuilder<Structure.Tuple2<Fields1, Fields2>, Structure.Tuple2<Row1, Row2>> withParams(
                SelectParams<Structure.Tuple2<Fields1, Fields2>, Structure.Tuple2<Row1, Row2>> newParams) {
            return new TableJoin<>(leftBuilder, rightBuilder, pred, newParams);
        }

        @Override
        public SelectBuilderSql<Structure.Tuple2<Fields1, Fields2>, Structure.Tuple2<Row1, Row2>> withPath(Path path) {
            return new TableJoin<>(leftBuilder.withPath(path), rightBuilder.withPath(path), pred, params);
        }

        @Override
        public Instantiated<Structure.Tuple2<Fields1, Fields2>, Structure.Tuple2<Row1, Row2>> instantiate(
                RenderCtx ctx, AtomicInteger counter) {
            String alias = ctx.alias(structure().path()).orElse("join_cte");
            Instantiated<Fields1, Row1> leftInstance = leftBuilder.instantiate(ctx, counter);
            Instantiated<Fields2, Row2> rightInstance = rightBuilder.instantiate(ctx, counter);
            Structure<Structure.Tuple2<Fields1, Fields2>, Structure.Tuple2<Row1, Row2>> newStructure = 
                leftInstance.structure().join(rightInstance.structure());
            
            List<CTE> ctes = new ArrayList<>(leftInstance.asCTEs());
            ctes.addAll(rightInstance.asCTEs());

            // Build alias-to-CTE map for join context
            // Left side columns come from leftInstance.alias(), right side from rightInstance.alias()
            Map<String, String> aliasToCteMap = new HashMap<>();
            for (ColumnTuple ct : leftInstance.columns()) {
                aliasToCteMap.put(ct.alias(), leftInstance.alias());
            }
            for (ColumnTuple ct : rightInstance.columns()) {
                aliasToCteMap.put(ct.alias(), rightInstance.alias());
            }

            // Generate explicit column list from all non-join CTEs using resolved CTE names
            String joinColumnList = renderJoinColumnList(ctes, aliasToCteMap);

            // Use join context for rendering ON predicate - references CTE output columns
            RenderCtx joinCtx = ctx.withJoinContext(true).withAliasToCteMap(aliasToCteMap);

            Fragment sql = Fragment.lit("select ")
                .append(Fragment.lit(joinColumnList))
                .append(Fragment.lit("\n  from "))
                .append(Fragment.lit(leftInstance.alias()))
                .append(Fragment.lit("\n  join "))
                .append(Fragment.lit(rightInstance.alias()))
                .append(Fragment.lit("\n  on "))
                .append(pred.apply(newStructure.fields()).render(joinCtx, counter));

            Optional<Fragment> whereAndMore = SelectParams.render(
                newStructure.fields(), joinCtx, counter, params
            );
            if (whereAndMore.isPresent()) {
                sql = sql.append(Fragment.lit("\n  ")).append(whereAndMore.get());
            }

            List<ColumnTuple> columns = new ArrayList<>(leftInstance.columns());
            columns.addAll(rightInstance.columns());

            Function<Integer, RowParser<Row1>> leftParser = leftInstance.rowParser();
            Function<Integer, RowParser<Row2>> rightParser = rightInstance.rowParser();
            Function<Integer, RowParser<Structure.Tuple2<Row1, Row2>>> combinedParser = i -> {
                RowParser<Row1> r1Parser = leftParser.apply(i);
                RowParser<Row2> r2Parser = rightParser.apply(i + leftInstance.columns().size());
                RowParser<And<Row1, Row2>> andParser = r1Parser.joined(r2Parser);
                
                // Convert And<Row1, Row2> to Structure.Tuple2<Row1, Row2>
                var allColumns = new ArrayList<>(andParser.columns());
                Function<Object[], Structure.Tuple2<Row1, Row2>> decode = values -> {
                    And<Row1, Row2> and = andParser.decode().apply(values);
                    return Structure.Tuple2.of(and.left(), and.right());
                };
                Function<Structure.Tuple2<Row1, Row2>, Object[]> encode = tuple2 -> {
                    And<Row1, Row2> and = new And<>(tuple2._1(), tuple2._2());
                    return andParser.encode().apply(and);
                };
                
                return new RowParser<>(allColumns, decode, encode);
            };
            
            return new Instantiated<>(
                alias,
                true,
                columns,
                sql,
                ctes,
                newStructure,
                combinedParser
            );
        }
    }
    
    /**
     * SelectBuilder for left joined queries.
     */
    static class TableLeftJoin<Fields1, Row1, Fields2, Row2>
            extends SelectBuilderSql<Structure.Tuple2<Fields1, Fields2>, Structure.Tuple2<Row1, Optional<Row2>>> {

        private final SelectBuilderSql<Fields1, Row1> leftBuilder;
        private final SelectBuilderSql<Fields2, Row2> rightBuilder;
        private final Function<Structure.Tuple2<Fields1, Fields2>, SqlExpr<Boolean>> pred;
        private final SelectParams<Structure.Tuple2<Fields1, Fields2>, Structure.Tuple2<Row1, Optional<Row2>>> params;

        public SelectBuilderSql<Fields1, Row1> left() { return leftBuilder; }
        public SelectBuilderSql<Fields2, Row2> right() { return rightBuilder; }

        public TableLeftJoin(
                SelectBuilderSql<Fields1, Row1> left,
                SelectBuilderSql<Fields2, Row2> right,
                Function<Structure.Tuple2<Fields1, Fields2>, SqlExpr<Boolean>> pred,
                SelectParams<Structure.Tuple2<Fields1, Fields2>, Structure.Tuple2<Row1, Optional<Row2>>> params) {
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
        public Structure<Structure.Tuple2<Fields1, Fields2>, Structure.Tuple2<Row1, Optional<Row2>>> structure() {
            return leftBuilder.structure().leftJoin(rightBuilder.structure());
        }
        
        @Override
        public SelectParams<Structure.Tuple2<Fields1, Fields2>, Structure.Tuple2<Row1, Optional<Row2>>> params() {
            return params;
        }
        
        @Override
        public SelectBuilder<Structure.Tuple2<Fields1, Fields2>, Structure.Tuple2<Row1, Optional<Row2>>> withParams(
                SelectParams<Structure.Tuple2<Fields1, Fields2>, Structure.Tuple2<Row1, Optional<Row2>>> newParams) {
            return new TableLeftJoin<>(leftBuilder, rightBuilder, pred, newParams);
        }

        @Override
        public SelectBuilderSql<Structure.Tuple2<Fields1, Fields2>, Structure.Tuple2<Row1, Optional<Row2>>> withPath(Path path) {
            return new TableLeftJoin<>(leftBuilder.withPath(path), rightBuilder.withPath(path), pred, params);
        }

        @Override
        public Instantiated<Structure.Tuple2<Fields1, Fields2>, Structure.Tuple2<Row1, Optional<Row2>>> instantiate(
                RenderCtx ctx, AtomicInteger counter) {
            String alias = ctx.alias(structure().path()).orElse("left_join_cte");
            Instantiated<Fields1, Row1> leftInstance = leftBuilder.instantiate(ctx, counter);
            Instantiated<Fields2, Row2> rightInstance = rightBuilder.instantiate(ctx, counter);
            
            Structure<Structure.Tuple2<Fields1, Fields2>, Structure.Tuple2<Row1, Row2>> joinedStructure = 
                leftInstance.structure().join(rightInstance.structure());
            Structure<Structure.Tuple2<Fields1, Fields2>, Structure.Tuple2<Row1, Optional<Row2>>> newStructure = 
                leftInstance.structure().leftJoin(rightInstance.structure());
            
            List<CTE> ctes = new ArrayList<>(leftInstance.asCTEs());
            ctes.addAll(rightInstance.asCTEs());

            // Build alias-to-CTE map for join context
            // Left side columns come from leftInstance.alias(), right side from rightInstance.alias()
            Map<String, String> aliasToCteMap = new HashMap<>();
            for (ColumnTuple ct : leftInstance.columns()) {
                aliasToCteMap.put(ct.alias(), leftInstance.alias());
            }
            for (ColumnTuple ct : rightInstance.columns()) {
                aliasToCteMap.put(ct.alias(), rightInstance.alias());
            }

            // Generate explicit column list from all non-join CTEs using resolved CTE names
            String joinColumnList = renderJoinColumnList(ctes, aliasToCteMap);

            // Use join context for rendering ON predicate - references CTE output columns
            RenderCtx joinCtx = ctx.withJoinContext(true).withAliasToCteMap(aliasToCteMap);

            Fragment sql = Fragment.lit("select ")
                .append(Fragment.lit(joinColumnList))
                .append(Fragment.lit("\n  from "))
                .append(Fragment.lit(leftInstance.alias()))
                .append(Fragment.lit("\n  left join "))
                .append(Fragment.lit(rightInstance.alias()))
                .append(Fragment.lit("\n  on "))
                .append(pred.apply(joinedStructure.fields()).render(joinCtx, counter));

            Optional<Fragment> whereAndMore = SelectParams.render(
                newStructure.fields(), joinCtx, counter, params
            );
            if (whereAndMore.isPresent()) {
                sql = sql.append(Fragment.lit("\n  ")).append(whereAndMore.get());
            }

            List<ColumnTuple> columns = new ArrayList<>(leftInstance.columns());
            columns.addAll(rightInstance.columns());

            Function<Integer, RowParser<Row1>> leftParser = leftInstance.rowParser();
            Function<Integer, RowParser<Row2>> rightParser = rightInstance.rowParser();
            Function<Integer, RowParser<Structure.Tuple2<Row1, Optional<Row2>>>> combinedParser = i -> {
                RowParser<Row1> r1Parser = leftParser.apply(i);
                RowParser<Row2> r2Parser = rightParser.apply(i + leftInstance.columns().size());
                RowParser<And<Row1, Optional<Row2>>> andParser = r1Parser.leftJoined(r2Parser);
                
                // Convert And<Row1, Optional<Row2>> to Structure.Tuple2<Row1, Optional<Row2>>
                var allColumns = new ArrayList<>(andParser.columns());
                Function<Object[], Structure.Tuple2<Row1, Optional<Row2>>> decode = values -> {
                    And<Row1, Optional<Row2>> and = andParser.decode().apply(values);
                    return Structure.Tuple2.of(and.left(), and.right());
                };
                Function<Structure.Tuple2<Row1, Optional<Row2>>, Object[]> encode = tuple2 -> {
                    And<Row1, Optional<Row2>> and = new And<>(tuple2._1(), tuple2._2());
                    return andParser.encode().apply(and);
                };
                
                return new RowParser<>(allColumns, decode, encode);
            };
            
            return new Instantiated<>(
                alias,
                true,
                columns,
                sql,
                ctes,
                newStructure,
                combinedParser
            );
        }
    }
}