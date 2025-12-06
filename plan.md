# Plan: Comprehensive Aggregation Support for Typo DSL

## Overview

This plan outlines a comprehensive approach to adding aggregation support to Typo's Java DSL,
bringing it closer to feature parity with jOOQ while maintaining Typo's strong type-safety guarantees.

## Goals

1. Support GROUP BY with type-safe aggregate functions
2. Support HAVING clause for filtering aggregated results
3. Support Window Functions (OVER, PARTITION BY, ranking functions)
4. Maintain compile-time type safety wherever possible
5. Support both SQL generation and mock evaluation

---

## Part 1: Core Architecture Decisions

### 1.1 Builder Phase Pattern

The key architectural decision is how to transition from a "row-level" query to an "aggregated" query.
Two main approaches:

**Option A: State Machine Pattern (Recommended)**
```java
SelectBuilder<F, R>                    // Base: can filter, join, order
    .groupBy(...)                      // Transitions to:
    -> GroupedBuilder<F, R, G>         // Grouped: can filter groups (HAVING), aggregate
    .select(...)                       // Transitions to:
    -> SelectBuilder<NewF, NewR>       // Back to select with aggregated columns
```

**Option B: Single Builder with Modes**
```java
SelectBuilder<F, R>
    .groupBy(...)                      // Sets internal state
    .having(...)                       // Only valid after groupBy
    .map(...)                          // Renders aggregates correctly
```

**Recommendation:** Option A is more type-safe. The return type change from `groupBy()` prevents
calling incompatible methods. jOOQ uses this approach.

### 1.2 Expression Type Hierarchy for Aggregates

```java
// Aggregate expressions that require GROUP BY context
sealed interface AggregateExpr<T> extends SqlExpr<T> {
    record Count(SqlExpr<?> expr) implements AggregateExpr<Long>
    record CountDistinct(SqlExpr<?> expr) implements AggregateExpr<Long>
    record Sum<T extends Number>(SqlExpr<T> expr) implements AggregateExpr<T>
    record Avg<T extends Number>(SqlExpr<T> expr) implements AggregateExpr<Double>
    record Min<T>(SqlExpr<T> expr) implements AggregateExpr<T>
    record Max<T>(SqlExpr<T> expr) implements AggregateExpr<T>
    record ArrayAgg<T>(SqlExpr<T> expr) implements AggregateExpr<List<T>>
    record StringAgg(SqlExpr<String> expr, String delimiter) implements AggregateExpr<String>
}
```

### 1.3 Window Function Architecture

```java
// Window function wrapping an aggregate or ranking function
record WindowExpr<T>(
    SqlExpr<T> function,           // The aggregate or ranking function
    List<SqlExpr<?>> partitionBy,  // PARTITION BY columns
    List<SortOrder<?>> orderBy,    // ORDER BY within partition
    WindowFrame frame              // Optional frame (ROWS/RANGE BETWEEN)
) implements SqlExpr<T>

// Ranking functions (only valid in window context)
sealed interface RankingExpr<T> extends SqlExpr<T> {
    record RowNumber() implements RankingExpr<Long>
    record Rank() implements RankingExpr<Long>
    record DenseRank() implements RankingExpr<Long>
    record NTile(int n) implements RankingExpr<Long>
    record Lag<T>(SqlExpr<T> expr, int offset, SqlExpr<T> defaultVal) implements RankingExpr<T>
    record Lead<T>(SqlExpr<T> expr, int offset, SqlExpr<T> defaultVal) implements RankingExpr<T>
}
```

---

## Part 2: GROUP BY Implementation

### 2.1 API Design

```java
// Current state
var query = personRepo.select()
    .where(p -> p.active().isEqual(true));

// After groupBy - returns GroupedBuilder
var grouped = query.groupBy(p -> p.department());

// Select aggregated columns - returns back to SelectBuilder
var result = grouped.select(
    (p, agg) -> Tuples.of(
        p.department(),           // Group key (non-aggregated)
        agg.count(),              // COUNT(*)
        agg.sum(p.salary()),      // SUM(salary)
        agg.avg(p.age())          // AVG(age)
    )
);

// With HAVING
var filtered = grouped
    .having((p, agg) -> agg.count().greaterThan(5L))
    .select((p, agg) -> Tuples.of(
        p.department(),
        agg.count()
    ));
```

### 2.2 GroupedBuilder Interface

```java
public interface GroupedBuilder<Fields, Row, GroupKey> {
    /**
     * Filter aggregated groups (HAVING clause).
     */
    GroupedBuilder<Fields, Row, GroupKey> having(
        BiFunction<Fields, AggregateContext, SqlExpr<Boolean>> predicate
    );

    /**
     * Select columns from grouped query.
     * @param projection Function receiving fields and aggregate context
     */
    <NewFields extends Tuples.TupleExpr<NewRow>, NewRow extends Tuples.Tuple>
    SelectBuilder<NewFields, NewRow> select(
        BiFunction<Fields, AggregateContext, NewFields> projection
    );
}
```

### 2.3 AggregateContext Interface

```java
public interface AggregateContext {
    // Count functions
    SqlExpr<Long> count();
    SqlExpr<Long> count(SqlExpr<?> expr);
    SqlExpr<Long> countDistinct(SqlExpr<?> expr);

    // Numeric aggregates
    <T extends Number> SqlExpr<T> sum(SqlExpr<T> expr);
    <T extends Number> SqlExpr<Double> avg(SqlExpr<T> expr);
    <T extends Comparable<T>> SqlExpr<T> min(SqlExpr<T> expr);
    <T extends Comparable<T>> SqlExpr<T> max(SqlExpr<T> expr);

    // String aggregates
    SqlExpr<String> stringAgg(SqlExpr<String> expr, String delimiter);

    // Array aggregates
    <T> SqlExpr<List<T>> arrayAgg(SqlExpr<T> expr);

    // JSON aggregates (PostgreSQL specific)
    <T> SqlExpr<Json> jsonAgg(SqlExpr<T> expr);
    SqlExpr<Json> jsonObjectAgg(SqlExpr<String> key, SqlExpr<?> value);
}
```

### 2.4 Multiple Group By Columns

```java
// Single column grouping
personRepo.select()
    .groupBy(p -> p.department())
    .select(...);

// Multiple column grouping - use tuple
personRepo.select()
    .groupBy(p -> Tuples.of(p.department(), p.location()))
    .select((p, agg) -> Tuples.of(
        p.department(),
        p.location(),
        agg.count()
    ));
```

### 2.5 SQL Generation

```sql
-- Input:
personRepo.select()
    .where(p -> p.active())
    .groupBy(p -> p.department())
    .having((p, agg) -> agg.count().greaterThan(5))
    .select((p, agg) -> Tuples.of(p.department(), agg.count(), agg.avg(p.salary())))

-- Output:
SELECT department, COUNT(*), AVG(salary)
FROM person
WHERE active = true
GROUP BY department
HAVING COUNT(*) > 5
```

---

## Part 3: Window Functions Implementation

### 3.1 API Design

```java
// Window function on aggregate
var query = personRepo.select()
    .map(p -> Tuples.of(
        p.name(),
        p.salary(),
        p.department(),
        // Running total of salary within department
        SqlExpr.sum(p.salary())
            .over()
            .partitionBy(p.department())
            .orderBy(p.hireDate().asc())
            .build()
    ));

// Ranking functions
var ranked = personRepo.select()
    .map(p -> Tuples.of(
        p.name(),
        p.salary(),
        // Rank within department by salary
        SqlExpr.rowNumber()
            .over()
            .partitionBy(p.department())
            .orderBy(p.salary().desc())
            .build(),
        // Dense rank
        SqlExpr.denseRank()
            .over()
            .partitionBy(p.department())
            .orderBy(p.salary().desc())
            .build()
    ));
```

### 3.2 Window Builder

```java
public class WindowBuilder<T> {
    private final SqlExpr<T> function;
    private List<SqlExpr<?>> partitionBy = new ArrayList<>();
    private List<SortOrder<?>> orderBy = new ArrayList<>();
    private WindowFrame frame = null;

    public WindowBuilder<T> partitionBy(SqlExpr<?>... exprs) {
        this.partitionBy.addAll(Arrays.asList(exprs));
        return this;
    }

    public WindowBuilder<T> orderBy(SortOrder<?>... orders) {
        this.orderBy.addAll(Arrays.asList(orders));
        return this;
    }

    public WindowBuilder<T> rowsBetween(FrameBound start, FrameBound end) {
        this.frame = new WindowFrame(FrameType.ROWS, start, end);
        return this;
    }

    public WindowBuilder<T> rangeBetween(FrameBound start, FrameBound end) {
        this.frame = new WindowFrame(FrameType.RANGE, start, end);
        return this;
    }

    public SqlExpr<T> build() {
        return new WindowExpr<>(function, partitionBy, orderBy, frame);
    }
}

// Frame bounds
public sealed interface FrameBound {
    record UnboundedPreceding() implements FrameBound {}
    record Preceding(int n) implements FrameBound {}
    record CurrentRow() implements FrameBound {}
    record Following(int n) implements FrameBound {}
    record UnboundedFollowing() implements FrameBound {}
}
```

### 3.3 SQL Generation

```sql
-- Input:
SqlExpr.sum(p.salary())
    .over()
    .partitionBy(p.department())
    .orderBy(p.hireDate().asc())
    .rowsBetween(FrameBound.unboundedPreceding(), FrameBound.currentRow())
    .build()

-- Output:
SUM(salary) OVER (
    PARTITION BY department
    ORDER BY hire_date ASC
    ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
)
```

---

## Part 4: Implementation Plan

### Phase 1: Core Aggregate Infrastructure
**Files to create/modify:**
- `typo-dsl-java/src/java/typo/dsl/AggregateExpr.java` - New file for aggregate expressions
- `typo-dsl-java/src/java/typo/dsl/AggregateContext.java` - New interface
- `typo-dsl-java/src/java/typo/dsl/SqlExpr.java` - Add aggregate to permits list
- `typo-dsl-java/src/java/typo/dsl/SqlExprVisitor.java` - Add visitAggregate methods

**Tasks:**
1. Create AggregateExpr sealed interface with Count, Sum, Avg, Min, Max, etc.
2. Create AggregateContext interface
3. Add aggregate rendering to SqlExpr (renders COUNT(*), SUM(col), etc.)
4. Add visitor methods for each aggregate type

### Phase 2: GroupedBuilder
**Files to create/modify:**
- `typo-dsl-java/src/java/typo/dsl/GroupedBuilder.java` - New interface
- `typo-dsl-java/src/java/typo/dsl/GroupedBuilderSql.java` - SQL implementation
- `typo-dsl-java/src/java/typo/dsl/GroupedBuilderMock.java` - Mock implementation
- `typo-dsl-java/src/java/typo/dsl/SelectBuilder.java` - Add groupBy method

**Tasks:**
1. Create GroupedBuilder interface with having() and select() methods
2. Implement GroupedBuilderSql that tracks GROUP BY columns and HAVING predicates
3. Implement GroupedBuilderMock that groups in-memory and evaluates aggregates
4. Add groupBy() method to SelectBuilder that returns GroupedBuilder
5. Write tests for single and multi-column GROUP BY

### Phase 3: HAVING Clause
**Tasks:**
1. Add having() to GroupedBuilder
2. SQL generation for HAVING
3. Mock evaluation for HAVING (filter after grouping)
4. Tests for HAVING with various predicates

### Phase 4: Window Functions
**Files to create/modify:**
- `typo-dsl-java/src/java/typo/dsl/WindowExpr.java` - Window expression
- `typo-dsl-java/src/java/typo/dsl/WindowBuilder.java` - Fluent builder
- `typo-dsl-java/src/java/typo/dsl/RankingExpr.java` - Ranking functions

**Tasks:**
1. Create WindowExpr record
2. Create WindowBuilder with partitionBy(), orderBy(), frame methods
3. Add static methods for ranking functions (rowNumber, rank, denseRank, etc.)
4. SQL generation for OVER clause
5. Add .over() method to aggregate expressions
6. Mock evaluation (complex - may need to sort and partition in memory)
7. Tests for window functions

### Phase 5: Advanced Aggregates
**Tasks:**
1. Add DISTINCT to aggregates: countDistinct, sumDistinct
2. Add string aggregates: stringAgg, arrayAgg
3. Add JSON aggregates: jsonAgg, jsonObjectAgg (PostgreSQL)
4. Add statistical aggregates: stddev, variance (optional)
5. Dialect-specific rendering (PostgreSQL vs MariaDB)

---

## Part 5: Mock Implementation Strategy

### 5.1 GROUP BY Mock Evaluation

```java
// In GroupedBuilderMock
public List<NewRow> evaluate(List<Row> rows, GroupKey groupKey,
                              BiFunction<Fields, AggregateContext, NewFields> projection) {
    // 1. Group rows by key
    Map<Object, List<Row>> groups = rows.stream()
        .collect(Collectors.groupingBy(row -> evaluateGroupKey(row, groupKey)));

    // 2. For each group, create aggregate context with actual values
    List<NewRow> results = new ArrayList<>();
    for (Map.Entry<Object, List<Row>> entry : groups.entrySet()) {
        List<Row> groupRows = entry.getValue();
        AggregateContext ctx = new MockAggregateContext(groupRows, structure);

        // 3. Evaluate projection with aggregate context
        NewFields fields = projection.apply(structure.fields(), ctx);
        NewRow row = evaluateProjection(fields, entry.getKey(), ctx);
        results.add(row);
    }

    return results;
}
```

### 5.2 Window Function Mock Evaluation

Window functions are complex to evaluate in-memory because they need:
1. The complete result set (not just current row)
2. Partition boundaries
3. Sort order within partitions
4. Frame boundaries for running calculations

```java
// Strategy: Two-pass evaluation
// Pass 1: Collect all rows and partition them
// Pass 2: Evaluate window functions for each row with context

class WindowEvaluator {
    List<Row> evaluateWindow(List<Row> allRows, WindowExpr<?> window) {
        // 1. Partition rows
        Map<Object, List<Row>> partitions = partitionRows(allRows, window.partitionBy());

        // 2. Sort within partitions
        for (List<Row> partition : partitions.values()) {
            sortByOrderBy(partition, window.orderBy());
        }

        // 3. Evaluate window function for each row
        List<Row> results = new ArrayList<>();
        for (List<Row> partition : partitions.values()) {
            for (int i = 0; i < partition.size(); i++) {
                Row row = partition.get(i);
                Object windowValue = evaluateWindowFunction(
                    window, partition, i, window.frame()
                );
                results.add(rowWithWindowValue(row, windowValue));
            }
        }

        return results;
    }
}
```

---

## Part 6: Type Safety Considerations

### 6.1 Aggregate Return Types

| Aggregate | Input Type | Output Type |
|-----------|-----------|-------------|
| COUNT(*) | any | Long |
| COUNT(col) | any | Long |
| SUM | Integer | Long |
| SUM | Long | Long |
| SUM | BigDecimal | BigDecimal |
| AVG | any numeric | Double |
| MIN/MAX | T | T |
| STRING_AGG | String | String |
| ARRAY_AGG | T | List<T> |
| JSON_AGG | T | Json |

### 6.2 Compile-Time Validation

The type system should prevent:
1. Using non-aggregated columns in SELECT without GROUP BY
2. Using aggregates in WHERE (should be HAVING)
3. Mixing aggregated and non-aggregated columns incorrectly

```java
// This should compile - department is in GROUP BY
personRepo.select()
    .groupBy(p -> p.department())
    .select((p, agg) -> Tuples.of(p.department(), agg.count()));

// This should NOT compile - name is not in GROUP BY
// Java's type system can't fully prevent this, but we can throw at runtime
personRepo.select()
    .groupBy(p -> p.department())
    .select((p, agg) -> Tuples.of(p.name(), agg.count())); // Runtime error
```

---

## Part 7: Test Cases

### GROUP BY Tests
```java
@Test void groupBySingleColumn()
@Test void groupByMultipleColumns()
@Test void groupByWithCount()
@Test void groupByWithSum()
@Test void groupByWithAvg()
@Test void groupByWithMinMax()
@Test void groupByWithHaving()
@Test void groupByWithHavingCount()
@Test void groupByWithOrderBy()
@Test void groupByEmptyResult()
```

### Window Function Tests
```java
@Test void rowNumberOverPartition()
@Test void rankOverPartition()
@Test void denseRankOverPartition()
@Test void sumOverPartition()
@Test void avgOverPartition()
@Test void windowWithOrderBy()
@Test void windowWithFrame()
@Test void multipleWindowsInSameQuery()
```

### Aggregate Tests
```java
@Test void countAll()
@Test void countColumn()
@Test void countDistinct()
@Test void sumIntegers()
@Test void sumDecimals()
@Test void avgReturnsDouble()
@Test void minMaxStrings()
@Test void minMaxDates()
@Test void stringAggWithDelimiter()
@Test void arrayAgg()
@Test void jsonAgg()
```

---

## Part 8: Estimated Complexity

| Component | Complexity | Effort |
|-----------|-----------|--------|
| AggregateExpr types | Low | 1-2 days |
| AggregateContext | Medium | 1-2 days |
| GroupedBuilder interface | Medium | 2-3 days |
| GroupedBuilderSql | Medium | 3-4 days |
| GroupedBuilderMock | High | 4-5 days |
| HAVING clause | Low | 1 day |
| Window expressions | Medium | 2-3 days |
| WindowBuilder | Low | 1-2 days |
| Window SQL generation | Medium | 2-3 days |
| Window mock evaluation | Very High | 5-7 days |
| Ranking functions | Low | 1-2 days |
| Tests | Medium | 3-4 days |

**Total estimated effort: 25-40 days**

---

## Part 9: Alternative Approaches Considered

### 9.1 Reuse Existing multisetOn Infrastructure

The current `multisetOn` already does a form of aggregation (JSON_AGG). Could potentially
extend this pattern rather than creating new GROUP BY infrastructure.

**Pros:** Less new code
**Cons:** multisetOn is specifically for one-to-many relationships, GROUP BY is more general

### 9.2 Raw SQL Escape Hatch

Allow users to write raw aggregate SQL when type-safe DSL is too limiting:

```java
personRepo.select()
    .rawGroupBy("department")
    .rawSelect("department, COUNT(*), AVG(salary)")
    .toList(conn);
```

**Pros:** Immediate flexibility
**Cons:** Loses type safety, harder to maintain

### 9.3 Annotation-Based Aggregates

Generate aggregate queries from annotated repository methods:

```java
@GroupBy(columns = "department")
@Aggregate(count = true, avgOf = "salary")
List<DepartmentStats> getDepartmentStats();
```

**Pros:** Very declarative
**Cons:** Less flexible, more magic, harder to compose

**Recommendation:** Implement the full GroupedBuilder approach (Option A in 9.1) as it provides
the best balance of type safety, flexibility, and consistency with the existing DSL design.
