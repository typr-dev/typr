# Aggregation Implementation Plan - Detailed Design

## Design Principles

1. **Convenience first**: API should feel natural and require minimal boilerplate
2. **Type-safe where it matters**: Aggregate return types must be correct
3. **Pragmatic about GROUP BY validation**: Runtime validation is acceptable since Java's type system can't fully encode this
4. **Reuse existing infrastructure**: Build on SqlExpr, Structure, SelectBuilder patterns
5. **Support both SQL and Mock**: All features must work in-memory for testing

---

## Part 1: Core Type Model

### 1.1 Aggregate Expression Types

Aggregates are just another kind of `SqlExpr`. No need for a separate hierarchy.

```java
// In SqlExpr.java - add these to the permits list and as records

/**
 * COUNT(*) - counts all rows
 */
record CountStar() implements SqlExpr<Long> {
    @Override
    public DbType<Long> dbType() { return PgTypes.int8; }

    @Override
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
        return Fragment.lit("COUNT(*)");
    }
}

/**
 * COUNT(expr) - counts non-null values
 */
record Count<T>(SqlExpr<T> expr) implements SqlExpr<Long> {
    @Override
    public DbType<Long> dbType() { return PgTypes.int8; }

    @Override
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
        return Fragment.lit("COUNT(")
            .append(expr.render(ctx, counter))
            .append(Fragment.lit(")"));
    }
}

/**
 * COUNT(DISTINCT expr)
 */
record CountDistinct<T>(SqlExpr<T> expr) implements SqlExpr<Long> {
    @Override
    public DbType<Long> dbType() { return PgTypes.int8; }

    @Override
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
        return Fragment.lit("COUNT(DISTINCT ")
            .append(expr.render(ctx, counter))
            .append(Fragment.lit(")"));
    }
}

/**
 * SUM(expr) - for numeric types
 * Note: SUM of integers returns Long, SUM of decimals returns BigDecimal
 */
record Sum<T extends Number>(SqlExpr<T> expr, DbType<T> resultType) implements SqlExpr<T> {
    @Override
    public DbType<T> dbType() { return resultType; }

    @Override
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
        return Fragment.lit("SUM(")
            .append(expr.render(ctx, counter))
            .append(Fragment.lit(")"));
    }
}

/**
 * AVG(expr) - always returns Double (or BigDecimal for precision)
 */
record Avg<T extends Number>(SqlExpr<T> expr) implements SqlExpr<Double> {
    @Override
    public DbType<Double> dbType() { return PgTypes.float8; }

    @Override
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
        return Fragment.lit("AVG(")
            .append(expr.render(ctx, counter))
            .append(Fragment.lit(")"));
    }
}

/**
 * MIN(expr)
 */
record Min<T>(SqlExpr<T> expr, DbType<T> resultType) implements SqlExpr<T> {
    @Override
    public DbType<T> dbType() { return resultType; }

    @Override
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
        return Fragment.lit("MIN(")
            .append(expr.render(ctx, counter))
            .append(Fragment.lit(")"));
    }
}

/**
 * MAX(expr)
 */
record Max<T>(SqlExpr<T> expr, DbType<T> resultType) implements SqlExpr<T> {
    @Override
    public DbType<T> dbType() { return resultType; }

    @Override
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
        return Fragment.lit("MAX(")
            .append(expr.render(ctx, counter))
            .append(Fragment.lit(")"));
    }
}

/**
 * STRING_AGG(expr, delimiter) - PostgreSQL
 * GROUP_CONCAT(expr SEPARATOR delimiter) - MariaDB
 */
record StringAgg(SqlExpr<String> expr, String delimiter, Dialect dialect) implements SqlExpr<String> {
    @Override
    public DbType<String> dbType() { return PgTypes.text; }

    @Override
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
        if (dialect == Dialect.MARIADB) {
            return Fragment.lit("GROUP_CONCAT(")
                .append(expr.render(ctx, counter))
                .append(Fragment.lit(" SEPARATOR "))
                .append(Fragment.param(delimiter, PgTypes.text, counter))
                .append(Fragment.lit(")"));
        } else {
            return Fragment.lit("STRING_AGG(")
                .append(expr.render(ctx, counter))
                .append(Fragment.lit(", "))
                .append(Fragment.param(delimiter, PgTypes.text, counter))
                .append(Fragment.lit(")"));
        }
    }
}

/**
 * ARRAY_AGG(expr) - collects values into array
 */
record ArrayAgg<T>(SqlExpr<T> expr, DbType<List<T>> arrayType) implements SqlExpr<List<T>> {
    @Override
    public DbType<List<T>> dbType() { return arrayType; }

    @Override
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
        return Fragment.lit("ARRAY_AGG(")
            .append(expr.render(ctx, counter))
            .append(Fragment.lit(")"));
    }
}

/**
 * JSON_AGG(expr) - collects values into JSON array (PostgreSQL)
 * JSON_ARRAYAGG(expr) - MariaDB
 */
record JsonAgg<T>(SqlExpr<T> expr, Dialect dialect) implements SqlExpr<Json> {
    @Override
    public DbType<Json> dbType() { return PgTypes.json; }

    @Override
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
        String fn = dialect == Dialect.MARIADB ? "JSON_ARRAYAGG" : "JSON_AGG";
        return Fragment.lit(fn + "(")
            .append(expr.render(ctx, counter))
            .append(Fragment.lit(")"));
    }
}

/**
 * BOOL_AND(expr) - true if all values are true
 */
record BoolAnd(SqlExpr<Boolean> expr) implements SqlExpr<Boolean> {
    @Override
    public DbType<Boolean> dbType() { return PgTypes.bool; }

    @Override
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
        return Fragment.lit("BOOL_AND(")
            .append(expr.render(ctx, counter))
            .append(Fragment.lit(")"));
    }
}

/**
 * BOOL_OR(expr) - true if any value is true
 */
record BoolOr(SqlExpr<Boolean> expr) implements SqlExpr<Boolean> {
    @Override
    public DbType<Boolean> dbType() { return PgTypes.bool; }

    @Override
    public Fragment render(RenderCtx ctx, AtomicInteger counter) {
        return Fragment.lit("BOOL_OR(")
            .append(expr.render(ctx, counter))
            .append(Fragment.lit(")"));
    }
}
```

### 1.2 Convenient Factory Methods on SqlExpr

```java
// In SqlExpr.java - static factory methods

// Count
static SqlExpr<Long> count() {
    return new CountStar();
}

static <T> SqlExpr<Long> count(SqlExpr<T> expr) {
    return new Count<>(expr);
}

static <T> SqlExpr<Long> countDistinct(SqlExpr<T> expr) {
    return new CountDistinct<>(expr);
}

// Sum - with proper return type inference
static SqlExpr<Long> sum(SqlExpr<Integer> expr) {
    return new Sum<>(expr, PgTypes.int8);
}

static SqlExpr<Long> sumLong(SqlExpr<Long> expr) {
    return new Sum<>(expr, PgTypes.int8);
}

static SqlExpr<BigDecimal> sumBigDecimal(SqlExpr<BigDecimal> expr) {
    return new Sum<>(expr, PgTypes.numeric);
}

static SqlExpr<Double> sumDouble(SqlExpr<Double> expr) {
    return new Sum<>(expr, PgTypes.float8);
}

// Avg
static <T extends Number> SqlExpr<Double> avg(SqlExpr<T> expr) {
    return new Avg<>(expr);
}

// Min/Max - preserves type
static <T> SqlExpr<T> min(SqlExpr<T> expr) {
    return new Min<>(expr, expr.dbType());
}

static <T> SqlExpr<T> max(SqlExpr<T> expr) {
    return new Max<>(expr, expr.dbType());
}

// String aggregation
static SqlExpr<String> stringAgg(SqlExpr<String> expr, String delimiter, Dialect dialect) {
    return new StringAgg(expr, delimiter, dialect);
}

// Array aggregation
static <T> SqlExpr<List<T>> arrayAgg(SqlExpr<T> expr, DbType<List<T>> arrayType) {
    return new ArrayAgg<>(expr, arrayType);
}

// JSON aggregation
static <T> SqlExpr<Json> jsonAgg(SqlExpr<T> expr, Dialect dialect) {
    return new JsonAgg<>(expr, dialect);
}

// Boolean aggregation
static SqlExpr<Boolean> boolAnd(SqlExpr<Boolean> expr) {
    return new BoolAnd(expr);
}

static SqlExpr<Boolean> boolOr(SqlExpr<Boolean> expr) {
    return new BoolOr(expr);
}
```

---

## Part 2: GroupedBuilder API

### 2.1 The Key Insight: Simplify with BiFunction

Instead of a separate `AggregateContext` object, use the `SqlExpr` static methods directly.
The grouped builder just needs to track GROUP BY columns and HAVING predicates.

```java
/**
 * Builder for queries with GROUP BY.
 *
 * @param <Fields> The field accessor type from the original query
 * @param <Row> The row type from the original query
 * @param <GroupKey> The type of the GROUP BY expression(s)
 */
public interface GroupedBuilder<Fields, Row, GroupKey> {

    /**
     * Add a HAVING predicate to filter groups.
     * The predicate can use aggregate functions.
     */
    GroupedBuilder<Fields, Row, GroupKey> having(Function<Fields, SqlExpr<Boolean>> predicate);

    /**
     * Project the grouped result to specific columns.
     * Use SqlExpr.count(), SqlExpr.sum(), etc. for aggregates.
     * Non-aggregated columns should be from the GROUP BY key.
     */
    <NewFields extends Tuples.TupleExpr<NewRow>, NewRow extends Tuples.Tuple>
    SelectBuilder<NewFields, NewRow> select(Function<Fields, NewFields> projection);

    /**
     * Execute the grouped query and return results.
     * Shorthand for .select(projection).toList(conn)
     */
    default <NewFields extends Tuples.TupleExpr<NewRow>, NewRow extends Tuples.Tuple>
    List<NewRow> toList(Connection conn, Function<Fields, NewFields> projection) {
        return select(projection).toList(conn);
    }
}
```

### 2.2 Adding groupBy to SelectBuilder

```java
// In SelectBuilder.java

/**
 * Group results by a single column or expression.
 */
default <G> GroupedBuilder<Fields, Row, G> groupBy(Function<Fields, SqlExpr<G>> groupKey) {
    return groupByExpr(f -> List.of(groupKey.apply(f)));
}

/**
 * Group results by multiple columns (2 columns).
 */
default <G1, G2> GroupedBuilder<Fields, Row, Tuples.Tuple2<G1, G2>> groupBy(
        Function<Fields, SqlExpr<G1>> key1,
        Function<Fields, SqlExpr<G2>> key2) {
    return groupByExpr(f -> List.of(key1.apply(f), key2.apply(f)));
}

/**
 * Group results by multiple columns (3 columns).
 */
default <G1, G2, G3> GroupedBuilder<Fields, Row, Tuples.Tuple3<G1, G2, G3>> groupBy(
        Function<Fields, SqlExpr<G1>> key1,
        Function<Fields, SqlExpr<G2>> key2,
        Function<Fields, SqlExpr<G3>> key3) {
    return groupByExpr(f -> List.of(key1.apply(f), key2.apply(f), key3.apply(f)));
}

// ... up to groupBy with 5-6 keys

/**
 * Low-level groupBy that accepts a list of expressions.
 * Implementations must override this.
 */
<G> GroupedBuilder<Fields, Row, G> groupByExpr(Function<Fields, List<SqlExpr<?>>> groupKeys);
```

---

## Part 3: Usage Examples (API Design Validation)

### 3.1 Simple COUNT with GROUP BY

```java
// Count persons per department
var results = personRepo.select()
    .where(p -> p.active())
    .groupBy(p -> p.department())
    .select(p -> Tuples.of(
        p.department(),
        SqlExpr.count()
    ))
    .toList(conn);
// Type: List<Tuple2<Department, Long>>
```

### 3.2 Multiple Aggregates

```java
// Department stats
var stats = personRepo.select()
    .groupBy(p -> p.department())
    .select(p -> Tuples.of(
        p.department(),
        SqlExpr.count(),
        SqlExpr.avg(p.salary()),
        SqlExpr.min(p.hireDate()),
        SqlExpr.max(p.salary())
    ))
    .toList(conn);
// Type: List<Tuple5<Department, Long, Double, LocalDate, BigDecimal>>
```

### 3.3 HAVING Clause

```java
// Only departments with 5+ employees
var largeDepts = personRepo.select()
    .groupBy(p -> p.department())
    .having(p -> SqlExpr.count().greaterThan(5L))
    .select(p -> Tuples.of(
        p.department(),
        SqlExpr.count()
    ))
    .toList(conn);
```

### 3.4 Multiple GROUP BY Columns

```java
// Sales by year and quarter
var quarterly = salesRepo.select()
    .groupBy(
        s -> s.year(),
        s -> s.quarter()
    )
    .select(s -> Tuples.of(
        s.year(),
        s.quarter(),
        SqlExpr.sum(s.amount()),
        SqlExpr.count()
    ))
    .orderBy(r -> r._1().asc())
    .toList(conn);
// Type: List<Tuple4<Integer, Integer, BigDecimal, Long>>
```

### 3.5 String Aggregation

```java
// Concatenate all email addresses per person
var emailLists = emailRepo.select()
    .groupBy(e -> e.personId())
    .select(e -> Tuples.of(
        e.personId(),
        SqlExpr.stringAgg(e.email(), ", ", Dialect.POSTGRESQL)
    ))
    .toList(conn);
// Type: List<Tuple2<PersonId, String>>
```

### 3.6 Combining with JOIN

```java
// Department with employee count, joined to get department name
var result = employeeRepo.select()
    .joinOn(departmentRepo.select(),
        ed -> ed._1().departmentId().isEqual(ed._2().id()))
    .groupBy(ed -> ed._2().name())
    .select(ed -> Tuples.of(
        ed._2().name(),
        SqlExpr.count(),
        SqlExpr.avg(ed._1().salary())
    ))
    .toList(conn);
```

---

## Part 4: Implementation Details

### 4.1 GroupedBuilderSql

```java
public class GroupedBuilderSql<Fields, Row, GroupKey>
        implements GroupedBuilder<Fields, Row, GroupKey> {

    private final SelectBuilderSql<Fields, Row> source;
    private final Function<Fields, List<SqlExpr<?>>> groupByExprs;
    private final List<Function<Fields, SqlExpr<Boolean>>> havingPredicates;

    public GroupedBuilderSql(
            SelectBuilderSql<Fields, Row> source,
            Function<Fields, List<SqlExpr<?>>> groupByExprs) {
        this.source = source;
        this.groupByExprs = groupByExprs;
        this.havingPredicates = new ArrayList<>();
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
    public GroupedBuilder<Fields, Row, GroupKey> having(
            Function<Fields, SqlExpr<Boolean>> predicate) {
        var newHaving = new ArrayList<>(havingPredicates);
        newHaving.add(predicate);
        return new GroupedBuilderSql<>(source, groupByExprs, newHaving);
    }

    @Override
    public <NewFields extends Tuples.TupleExpr<NewRow>, NewRow extends Tuples.Tuple>
    SelectBuilder<NewFields, NewRow> select(Function<Fields, NewFields> projection) {
        // Create a new SelectBuilder that wraps the grouped query
        return new GroupedSelectBuilderSql<>(
            source, groupByExprs, havingPredicates, projection
        );
    }
}
```

### 4.2 SQL Generation for GROUP BY

```java
// In GroupedSelectBuilderSql or SelectBuilderSql

private Fragment renderGroupedQuery() {
    Fields fields = source.structure().fields();
    RenderCtx ctx = source.renderCtx();
    AtomicInteger counter = new AtomicInteger(0);

    // SELECT clause - from projection
    NewFields projected = projection.apply(fields);
    List<Fragment> selectFragments = new ArrayList<>();
    for (SqlExpr<?> expr : projected.exprs()) {
        selectFragments.add(expr.render(ctx, counter));
    }
    Fragment selectClause = Fragment.comma(selectFragments);

    // FROM clause - from source
    Fragment fromClause = source.renderFromClause(ctx, counter);

    // WHERE clause - from source
    Fragment whereClause = source.renderWhereClause(ctx, counter);

    // GROUP BY clause
    List<SqlExpr<?>> groupExprs = groupByExprs.apply(fields);
    List<Fragment> groupFragments = new ArrayList<>();
    for (SqlExpr<?> expr : groupExprs) {
        groupFragments.add(expr.render(ctx, counter));
    }
    Fragment groupByClause = Fragment.lit("GROUP BY ")
        .append(Fragment.comma(groupFragments));

    // HAVING clause
    Fragment havingClause = Fragment.empty();
    if (!havingPredicates.isEmpty()) {
        List<Fragment> havingFragments = new ArrayList<>();
        for (var predFn : havingPredicates) {
            SqlExpr<Boolean> pred = predFn.apply(fields);
            havingFragments.add(pred.render(ctx, counter));
        }
        havingClause = Fragment.lit("HAVING ")
            .append(Fragment.and(havingFragments));
    }

    // Combine
    return Fragment.lit("SELECT ")
        .append(selectClause)
        .append(Fragment.lit(" FROM "))
        .append(fromClause)
        .append(whereClause.isEmpty() ? Fragment.empty() : Fragment.lit(" WHERE ").append(whereClause))
        .append(Fragment.lit(" "))
        .append(groupByClause)
        .append(havingClause.isEmpty() ? Fragment.empty() : Fragment.lit(" ").append(havingClause));
}
```

### 4.3 GroupedBuilderMock

```java
public class GroupedBuilderMock<Fields, Row, GroupKey>
        implements GroupedBuilder<Fields, Row, GroupKey> {

    private final SelectBuilderMock<Fields, Row> source;
    private final Function<Fields, List<SqlExpr<?>>> groupByExprs;
    private final List<Function<Fields, SqlExpr<Boolean>>> havingPredicates;

    @Override
    public <NewFields extends Tuples.TupleExpr<NewRow>, NewRow extends Tuples.Tuple>
    SelectBuilder<NewFields, NewRow> select(Function<Fields, NewFields> projection) {

        // Create supplier that does the grouping
        Supplier<List<NewRow>> groupedSupplier = () -> {
            List<Row> allRows = source.toList(null);
            Structure<Fields, Row> structure = source.structure();
            Fields fields = structure.fields();

            // 1. Group rows by key
            Map<Object, List<Row>> groups = new LinkedHashMap<>();
            for (Row row : allRows) {
                Object key = evaluateGroupKey(structure, groupByExprs.apply(fields), row);
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
            }

            // 2. Filter by HAVING (if any)
            // For HAVING, we need to evaluate aggregates per group

            // 3. Project each group
            List<NewRow> results = new ArrayList<>();
            for (var entry : groups.entrySet()) {
                List<Row> groupRows = entry.getValue();

                // Check HAVING
                if (!havingPredicates.isEmpty()) {
                    boolean passesHaving = evaluateHaving(
                        structure, fields, groupRows, havingPredicates
                    );
                    if (!passesHaving) continue;
                }

                // Evaluate projection with aggregate context
                NewFields projected = projection.apply(fields);
                NewRow resultRow = evaluateProjection(structure, projected, groupRows);
                results.add(resultRow);
            }

            return results;
        };

        // Return a mock SelectBuilder with the grouped results
        return new GroupedSelectBuilderMock<>(groupedSupplier, projection);
    }

    // Evaluate a group key to a comparable object
    private Object evaluateGroupKey(
            Structure<Fields, Row> structure,
            List<SqlExpr<?>> keyExprs,
            Row row) {
        if (keyExprs.size() == 1) {
            return structure.untypedEval(keyExprs.get(0), row).orElse(null);
        }
        // Multiple keys - create a list
        List<Object> keyValues = new ArrayList<>();
        for (SqlExpr<?> expr : keyExprs) {
            keyValues.add(structure.untypedEval(expr, row).orElse(null));
        }
        return keyValues;
    }

    // Evaluate aggregate expressions against a group of rows
    private <T> T evaluateAggregate(
            Structure<Fields, Row> structure,
            SqlExpr<T> expr,
            List<Row> groupRows) {

        return switch (expr) {
            case CountStar c -> (T) Long.valueOf(groupRows.size());

            case Count<?> c -> {
                long count = 0;
                for (Row row : groupRows) {
                    if (structure.untypedEval(c.expr(), row).isPresent()) {
                        count++;
                    }
                }
                yield (T) Long.valueOf(count);
            }

            case Sum<?> s -> {
                BigDecimal sum = BigDecimal.ZERO;
                for (Row row : groupRows) {
                    var val = structure.untypedEval(s.expr(), row);
                    if (val.isPresent() && val.get() instanceof Number n) {
                        sum = sum.add(new BigDecimal(n.toString()));
                    }
                }
                // Convert to appropriate type
                yield (T) convertNumber(sum, s.resultType());
            }

            case Avg<?> a -> {
                BigDecimal sum = BigDecimal.ZERO;
                long count = 0;
                for (Row row : groupRows) {
                    var val = structure.untypedEval(a.expr(), row);
                    if (val.isPresent() && val.get() instanceof Number n) {
                        sum = sum.add(new BigDecimal(n.toString()));
                        count++;
                    }
                }
                yield (T) (count == 0 ? null : sum.divide(
                    BigDecimal.valueOf(count),
                    10,
                    RoundingMode.HALF_UP
                ).doubleValue());
            }

            case Min<?> m -> {
                Comparable min = null;
                for (Row row : groupRows) {
                    var val = structure.untypedEval(m.expr(), row);
                    if (val.isPresent()) {
                        Comparable v = (Comparable) val.get();
                        if (min == null || v.compareTo(min) < 0) {
                            min = v;
                        }
                    }
                }
                yield (T) min;
            }

            case Max<?> m -> {
                Comparable max = null;
                for (Row row : groupRows) {
                    var val = structure.untypedEval(m.expr(), row);
                    if (val.isPresent()) {
                        Comparable v = (Comparable) val.get();
                        if (max == null || v.compareTo(max) > 0) {
                            max = v;
                        }
                    }
                }
                yield (T) max;
            }

            case StringAgg sa -> {
                List<String> values = new ArrayList<>();
                for (Row row : groupRows) {
                    var val = structure.untypedEval(sa.expr(), row);
                    if (val.isPresent()) {
                        values.add((String) val.get());
                    }
                }
                yield (T) String.join(sa.delimiter(), values);
            }

            case ArrayAgg<?> aa -> {
                List<Object> values = new ArrayList<>();
                for (Row row : groupRows) {
                    var val = structure.untypedEval(aa.expr(), row);
                    if (val.isPresent()) {
                        values.add(val.get());
                    }
                }
                yield (T) values;
            }

            case BoolAnd ba -> {
                for (Row row : groupRows) {
                    var val = structure.untypedEval(ba.expr(), row);
                    if (val.isEmpty() || !((Boolean) val.get())) {
                        yield (T) Boolean.FALSE;
                    }
                }
                yield (T) Boolean.TRUE;
            }

            case BoolOr bo -> {
                for (Row row : groupRows) {
                    var val = structure.untypedEval(bo.expr(), row);
                    if (val.isPresent() && ((Boolean) val.get())) {
                        yield (T) Boolean.TRUE;
                    }
                }
                yield (T) Boolean.FALSE;
            }

            // For non-aggregate expressions, use the first row (assumes GROUP BY column)
            default -> {
                if (groupRows.isEmpty()) yield null;
                yield structure.untypedEval(expr, groupRows.get(0)).orElse(null);
            }
        };
    }
}
```

---

## Part 5: Visitor Updates

### 5.1 Add Aggregate Cases to SqlExprVisitor

```java
// In SqlExprVisitor.java

// Add visitor methods
Result visitCountStar(SqlExpr.CountStar countStar);
<U> Result visitCount(SqlExpr.Count<U> count);
<U> Result visitCountDistinct(SqlExpr.CountDistinct<U> countDistinct);
<U extends Number> Result visitSum(SqlExpr.Sum<U> sum);
<U extends Number> Result visitAvg(SqlExpr.Avg<U> avg);
<U> Result visitMin(SqlExpr.Min<U> min);
<U> Result visitMax(SqlExpr.Max<U> max);
Result visitStringAgg(SqlExpr.StringAgg stringAgg);
<U> Result visitArrayAgg(SqlExpr.ArrayAgg<U> arrayAgg);
<U> Result visitJsonAgg(SqlExpr.JsonAgg<U> jsonAgg);
Result visitBoolAnd(SqlExpr.BoolAnd boolAnd);
Result visitBoolOr(SqlExpr.BoolOr boolOr);

// Add cases to accept method
case SqlExpr.CountStar cs -> visitCountStar(cs);
case SqlExpr.Count<?> c -> visitCount(c);
case SqlExpr.CountDistinct<?> cd -> visitCountDistinct(cd);
case SqlExpr.Sum<?> s -> visitSum(s);
case SqlExpr.Avg<?> a -> visitAvg(a);
case SqlExpr.Min<?> m -> visitMin(m);
case SqlExpr.Max<?> mx -> visitMax(mx);
case SqlExpr.StringAgg sa -> visitStringAgg(sa);
case SqlExpr.ArrayAgg<?> aa -> visitArrayAgg(aa);
case SqlExpr.JsonAgg<?> ja -> visitJsonAgg(ja);
case SqlExpr.BoolAnd ba -> visitBoolAnd(ba);
case SqlExpr.BoolOr bo -> visitBoolOr(bo);
```

---

## Part 6: Files to Create/Modify

### New Files
1. `typo-dsl-java/src/java/typo/dsl/GroupedBuilder.java` - Interface
2. `typo-dsl-java/src/java/typo/dsl/GroupedBuilderSql.java` - SQL implementation
3. `typo-dsl-java/src/java/typo/dsl/GroupedBuilderMock.java` - Mock implementation

### Modified Files
1. `typo-dsl-java/src/java/typo/dsl/SqlExpr.java` - Add aggregate records and factory methods
2. `typo-dsl-java/src/java/typo/dsl/SqlExprVisitor.java` - Add visitor methods for aggregates
3. `typo-dsl-java/src/java/typo/dsl/SelectBuilder.java` - Add groupBy methods
4. `typo-dsl-java/src/java/typo/dsl/SelectBuilderSql.java` - Implement groupByExpr
5. `typo-dsl-java/src/java/typo/dsl/SelectBuilderMock.java` - Implement groupByExpr
6. `typo-dsl-java/src/java/typo/dsl/Structure.java` - Add aggregate evaluation to untypedEval

---

## Part 7: Implementation Order

### Phase 1: Core Aggregates (3-4 days)
1. Add CountStar, Count, Sum, Avg, Min, Max records to SqlExpr
2. Add factory methods
3. Add visitor methods
4. Test that aggregates render correct SQL

### Phase 2: GroupedBuilder SQL (3-4 days)
1. Create GroupedBuilder interface
2. Create GroupedBuilderSql
3. Add groupBy methods to SelectBuilder
4. Implement SQL generation
5. Write integration tests

### Phase 3: GroupedBuilder Mock (3-4 days)
1. Create GroupedBuilderMock
2. Implement aggregate evaluation in mock
3. Write mock tests

### Phase 4: HAVING (1-2 days)
1. Add having() to GroupedBuilder
2. Implement in both SQL and Mock
3. Write tests

### Phase 5: Additional Aggregates (2-3 days)
1. CountDistinct
2. StringAgg / GROUP_CONCAT
3. ArrayAgg
4. JsonAgg
5. BoolAnd / BoolOr

### Phase 6: Polish (2-3 days)
1. Dialect-specific rendering (PostgreSQL vs MariaDB)
2. Edge cases (empty groups, null handling)
3. Documentation
4. More tests

---

## Part 8: Test Cases

```java
@Test void countStarReturnsRowCount()
@Test void countColumnIgnoresNulls()
@Test void countDistinctCountsUniqueValues()
@Test void sumIntegersReturnsLong()
@Test void sumDecimalsReturnsBigDecimal()
@Test void avgReturnsDouble()
@Test void minFindsSmallest()
@Test void maxFindsLargest()
@Test void groupBySingleColumn()
@Test void groupByMultipleColumns()
@Test void havingFiltersGroups()
@Test void havingWithMultiplePredicates()
@Test void stringAggConcatenatesWithDelimiter()
@Test void arrayAggCollectsToList()
@Test void groupByWithJoin()
@Test void groupByWithWhere()
@Test void groupByWithOrderBy()
@Test void emptyGroupReturnsNoRows()
@Test void nullsInGroupByColumn()
@Test void aggregateInMockMatchesSql()
```

---

## Part 9: Future Extensions (Not in Scope)

These are explicitly NOT part of this implementation:

1. **Window Functions** - Separate feature, much more complex
2. **ROLLUP / CUBE / GROUPING SETS** - Advanced grouping
3. **FILTER clause on aggregates** - `COUNT(*) FILTER (WHERE condition)`
4. **WITHIN GROUP clause** - For ordered set aggregates
5. **User-defined aggregates** - Extension mechanism

These can be added later without breaking the core GROUP BY API.
