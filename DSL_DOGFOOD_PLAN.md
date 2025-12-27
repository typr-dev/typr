# DSL Dog-Fooding Plan: PgMetaDb Refactoring

## Goal
Replace the current PgMetaDb implementation with DSL-based queries to:
1. Fetch metadata efficiently in minimal database roundtrips
2. Dog-food our own DSL for internal typr code generation
3. Use `foundations-jdbc-dsl-scala` instead of legacy runtime

## Current State

### Generated Code Location
- `/Users/oyvind/pr/typr-2/typr/generated-and-checked-in/typr/generated/`
- Contains: `information_schema/`, `custom/`, `pg_catalog/`, domain types

### PgMetaDb Location
- `/Users/oyvind/pr/typr-2/typr/src/scala/typr/internal/pg/PgMetaDb.scala`
- Currently fetches ALL data from each table, then filters in Scala
- Problem: Fetches `columns` for ALL columns in database, not just wanted relations

### Current Data Fetching (Inefficient)
```scala
// Each of these fetches EVERYTHING, then filters later:
val tableConstraints = (new TableConstraintsViewRepoImpl).selectAll
val keyColumnUsage = (new KeyColumnUsageViewRepoImpl).selectAll
val columns = (new ColumnsViewRepoImpl).selectAll  // ALL columns!
// etc.
```

## New Architecture

### Two-Phase Approach

**Phase 1: Fetch Relation Names**
- Query `information_schema.tables` for all table/view names
- Apply `Selector` to determine which relations to include
- Returns `Set[db.RelationName]` (catalog, schema, name tuples)

**Phase 2: Mega-Query with Filtered Relations**
- Construct a single DSL query that:
  - Takes the filtered relation names as input
  - Fetches columns, constraints, etc. ONLY for selected relations
  - Uses MULTISET joins to get related data in one go
  - Projects only needed columns

### DSL Enhancements Required

#### 1. TupleIn - Check if tuple is in a list of values
```java
// Java DSL - new SqlExpr type
record TupleIn<T1, T2, T3>(
    SqlExpr<T1> expr1, SqlExpr<T2> expr2, SqlExpr<T3> expr3,
    List<Tuple3<T1, T2, T3>> values,
    DbType<T1> type1, DbType<T2> type2, DbType<T3> type3
) implements SqlExpr<Boolean>

// Renders as: (schema, table_name) IN (('public', 'users'), ('public', 'orders'))
```

#### 2. TupleInSubquery - Check if tuple is in subquery result
```java
// Java DSL
record TupleInSubquery<T extends Tuples.TupleExpr<R>, R extends Tuples.Tuple>(
    List<SqlExpr<?>> exprs,
    SelectBuilder<T, R> subquery
) implements SqlExpr<Boolean>

// Renders as: (schema, table_name) IN (SELECT schema, name FROM relations)
```

#### 3. Generalized Tuple Construction
```java
// Build tuples from SqlExprs for comparisons
static <T1, T2> TupleExpr2<T1, T2> tuple(SqlExpr<T1> e1, SqlExpr<T2> e2)
static <T1, T2, T3> TupleExpr3<T1, T2, T3> tuple(SqlExpr<T1> e1, SqlExpr<T2> e2, SqlExpr<T3> e3)
```

## Implementation Steps

### Step 1: DSL Tuple Enhancements (Java)

File: `foundations-jdbc-dsl/src/java/dev/typr/foundations/dsl/SqlExpr.java`

1. Add `TupleIn` record for checking if tuple is in value list
2. Add `TupleInSubquery` record for checking if tuple is in subquery
3. Add static factory methods for building tuples
4. Implement `render()` for both new types
5. Add to `SqlExprVisitor` switch

### Step 2: Wrap in Scala

File: `foundations-jdbc-dsl-scala/src/scala/dev/typr/foundations/scala/SqlExpr.scala`

1. Add Scala case classes wrapping Java tuple types
2. Add extension methods for tuple construction
3. Ensure all types are native Scala (no java.util.Optional, etc.)

### Step 3: Wrap in Kotlin

File: `foundations-jdbc-dsl-kotlin/src/kotlin/dev/typr/foundations/kotlin/SqlExpr.kt`

1. Add Kotlin data classes wrapping Java tuple types
2. Add extension functions for tuple construction
3. Ensure all types are native Kotlin (no Java Optional)

### Step 4: Update GeneratedSources

1. Change typr internal code generation to use `foundations-jdbc-dsl-scala`
2. Generate DSL-enabled repos for `information_schema` tables
3. Generate Fields classes with proper DSL support

### Step 5: Implement Phase 1 (fetchFilteredRelationNames)

Already implemented in commit 5f3a2067b0:
```scala
def fetchFilteredRelationNames(tableSelector: Selector, viewSelector: Selector)(using c: Connection): Set[db.RelationName] = {
  val tableNames = (new TablesViewRepoImpl).select
    .where(t => t.tableType.isEqual("BASE TABLE"))
    .toList
    .collect { case row if row.tableName.isDefined =>
      db.RelationName(row.tableSchema, row.tableName.get)
    }
    .filter(tableSelector.include)
  // ... similar for views
}
```

### Step 6: Implement Phase 2 (Mega-Query)

```scala
def fetchMetadata(relationNames: Set[db.RelationName])(using c: Connection): Input = {
  // Convert relation names to values for TupleIn
  val relationTuples = relationNames.toList.map(r => (r.schema, r.name))

  // Build mega-query with MULTISET
  val result = tablesRepo.select
    .where(t => SqlExpr.tupleIn(t.tableSchema, t.tableName, relationTuples))
    .multisetOn(columnsRepo.select, { case (t, c) =>
      t.tableSchema.isEqual(c.tableSchema) && t.tableName.isEqual(c.tableName)
    })
    .multisetOn(constraintsRepo.select, { case (t_c, con) =>
      t_c._1.tableSchema.isEqual(con.tableSchema) && t_c._1.tableName.isEqual(con.tableName)
    })
    // ... more multisets for other data
    .toList

  // Transform result to Input
  transformToInput(result)
}
```

### Step 7: Testing

1. Add tests in `testers/pg/scala/scalatypes` for:
   - TupleIn with various tuple sizes
   - TupleInSubquery
   - Combined with MULTISET
2. Add tests for Oracle to ensure cross-database compatibility
3. Verify generated SQL is correct (snapshot tests)

## Files to Modify

### New/Modified DSL Files
- `foundations-jdbc-dsl/src/java/dev/typr/foundations/dsl/SqlExpr.java` - Add TupleIn, TupleInSubquery
- `foundations-jdbc-dsl/src/java/dev/typr/foundations/dsl/SqlExprVisitor.java` - Add visitor methods
- `foundations-jdbc-dsl-scala/src/scala/dev/typr/foundations/scala/SqlExpr.scala` - Scala wrappers
- `foundations-jdbc-dsl-kotlin/src/kotlin/dev/typr/foundations/kotlin/SqlExpr.kt` - Kotlin wrappers

### typr Internal Files
- `typr/src/scala/typr/internal/pg/PgMetaDb.scala` - New DSL-based implementation
- `typr-scripts/src/scala/scripts/GeneratedSources.scala` - Use foundations-jdbc-dsl-scala

### Test Files
- `testers/pg/scala/scalatypes/src/scala/adventureworks/DSLTest.scala` - Tuple tests
- `testers/oracle/scala/src/scala/oracledb/DSLTest.scala` - Cross-DB tests

## Progress Tracking

- [ ] Step 1: DSL Tuple Enhancements (Java)
  - [ ] TupleIn record
  - [ ] TupleInSubquery record
  - [ ] Factory methods
  - [ ] render() implementations
  - [ ] Visitor updates
- [ ] Step 2: Scala Wrappers
  - [ ] TupleIn wrapper
  - [ ] TupleInSubquery wrapper
  - [ ] Extension methods
- [ ] Step 3: Kotlin Wrappers
  - [ ] TupleIn wrapper
  - [ ] TupleInSubquery wrapper
  - [ ] Extension functions
- [ ] Step 4: GeneratedSources update
- [ ] Step 5: Phase 1 verification
- [ ] Step 6: Phase 2 mega-query implementation
- [ ] Step 7: Tests
  - [ ] PostgreSQL tests
  - [ ] Oracle tests
  - [ ] Snapshot tests

## Notes

- SqlExpr wrapping for Scala/Kotlin is already complete (current working tree changes)
- All surface area should be native Scala/Kotlin types
- Test on PostgreSQL AND Oracle to ensure cross-database compatibility
- Keep typo mostly compiling while experimenting - use separate codepath initially
