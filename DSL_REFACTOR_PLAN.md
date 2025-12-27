# DSL Refactor Plan: Unified Tuple and In Expressions

## Goal
Create a generic unified `In<T>` operator that works for scalars, tuples, and subqueries by making TupleN/TupleExprN into interfaces that Row/ID/Fields types can implement.

## Design Summary

### TupleN as interfaces (Row/ID can be records implementing them)
```java
public interface Tuple2<T0, T1> extends Tuple {
  T0 _1();
  T1 _2();
  default Object[] asArray() { return new Object[] { _1(), _2() }; }

  record Impl<T0, T1>(T0 _1, T1 _2) implements Tuple2<T0, T1> {}

  static <T0, T1> Tuple2<T0, T1> of(T0 a, T1 b) { return new Impl<>(a, b); }
}
```

### Generated Row as record implementing TupleN
```java
public record DepartmentsRow(String deptCode, String deptRegion, String deptName, Optional<BigDecimal> budget)
    implements Tuple4<String, String, String, Optional<BigDecimal>> {
  @Override public String _1() { return deptCode; }
  @Override public String _2() { return deptRegion; }
  @Override public String _3() { return deptName; }
  @Override public Optional<BigDecimal> _4() { return budget; }
}
```

### Generated ID as record implementing TupleN
```java
public record DepartmentsId(String deptCode, String deptRegion)
    implements Tuple2<String, String> {
  @Override public String _1() { return deptCode; }
  @Override public String _2() { return deptRegion; }
}
```

### TupleExprN as interfaces (Fields classes implement them)
```java
public interface TupleExpr2<T0, T1> extends TupleExpr<Tuple2<T0, T1>> {
  SqlExpr<T0> _1();
  SqlExpr<T1> _2();

  default List<SqlExpr<?>> exprs() { return List.of(_1(), _2()); }
  default Tuple2<T0, T1> construct(T0 v0, T1 v1) { return Tuple2.of(v0, v1); }

  record Impl<T0, T1>(SqlExpr<T0> _1, SqlExpr<T1> _2) implements TupleExpr2<T0, T1> {}
}
```

### Generated Fields class implementing TupleExprN
```java
public final class DepartmentsFields
    implements TupleExpr4<String, String, String, Optional<BigDecimal>> {

  public final IdField<String, DepartmentsRow> deptCode;
  public final IdField<String, DepartmentsRow> deptRegion;
  public final Field<String, DepartmentsRow> deptName;
  public final OptField<BigDecimal, DepartmentsRow> budget;

  public DepartmentsFields(List<Path> _path) { /* initialize fields */ }

  @Override public SqlExpr<String> _1() { return deptCode; }
  @Override public SqlExpr<String> _2() { return deptRegion; }
  @Override public SqlExpr<String> _3() { return deptName; }
  @Override public SqlExpr<Optional<BigDecimal>> _4() { return budget; }

  @Override public DepartmentsRow construct(String a, String b, String c, Optional<BigDecimal> d) {
    return new DepartmentsRow(a, b, c, d);
  }
}
```

### Unified In<T>
```java
record In<T>(SqlExpr<T> lhs, SqlExpr<T[]> rhs) implements SqlExpr<Boolean> {
  @Override public DbType<Boolean> dbType() { return PgTypes.bool; }
  @Override public Fragment render(RenderCtx ctx, AtomicInteger counter) {
    // Delegate to ctx.dialect().renderIn() for tuple cases
  }
}
```

### Values<T> for constant lists
```java
record Values<T>(List<T> values, DbType<T> elementType) implements SqlExpr<T[]> { ... }
```

## Implementation Phases

### Phase 1-2: Update Tuples.java generator - TupleN/TupleExprN as interfaces [DONE]
- File: `typr-scripts/src/scala/scripts/GeneratedTuples.scala`
- Change TupleN from records to non-sealed interfaces with abstract `_N()` methods
- Add default `asArray()` implementation
- Add inner `Impl` record
- Add static `of()` factory method

### Phase 2: Update Tuples.java generator - TupleExprN with abstract _N() [DONE]
- Same file as Phase 1
- Change TupleExprN from records to non-sealed interfaces with abstract `_N()` methods
- Add default `exprs()`, `render()`, `construct()` implementations
- Add inner `Impl` record

### Phase 13: Update Scala DSL wrapper [DONE]
- Updated to use `Tuple2.of()` instead of `new Tuple2()`

### Phase 3: Add TupleDbTypeN classes for row parsing
- File: Create new `foundations-jdbc-dsl/src/java/dev/typr/foundations/dsl/TupleDbTypes.java`
- Generate TupleDbType1...TupleDbType22 that can parse/render tuple values

### Phase 4: Create Values<T> SqlExpr for constant lists
- File: `foundations-jdbc-dsl/src/java/dev/typr/foundations/dsl/SqlExpr.java`
- Add `record Values<T>(List<T> values, DbType<T> elementType) implements SqlExpr<T[]>`

### Phase 5: Create unified In<T> expression
- File: `foundations-jdbc-dsl/src/java/dev/typr/foundations/dsl/SqlExpr.java`
- Add unified `record In<T>(SqlExpr<T> lhs, SqlExpr<T[]> rhs) implements SqlExpr<Boolean>`

### Phase 6: Add Dialect.renderTupleIn() for db-specific rendering
- File: `foundations-jdbc-dsl/src/java/dev/typr/foundations/dsl/Dialect.java`
- Add method to handle tuple IN rendering
- SQL Server falls back to AND/OR expansion
- PostgreSQL/Oracle/MariaDB/DuckDB use row value expressions

### Phase 7: Update SelectBuilder to extend SqlExpr<R[]>
- File: `foundations-jdbc-dsl/src/java/dev/typr/foundations/dsl/SelectBuilder.java`
- Make SelectBuilder extend SqlExpr so it can be used as subquery in In

### Phase 8: Remove FieldsExpr, update SqlExprVisitor
- File: `foundations-jdbc-dsl/src/java/dev/typr/foundations/dsl/FieldsExpr.java` (delete)
- File: `foundations-jdbc-dsl/src/java/dev/typr/foundations/dsl/SqlExprVisitor.java` (update)
- FieldsExpr is subsumed by TupleExpr

### Phase 9: Update Structure.java mock interpreter
- File: `foundations-jdbc-dsl/src/java/dev/typr/foundations/dsl/Structure.java`
- Update to handle new tuple types for mock/test execution

### Phase 10: Update DbLibTypo - generate Row records implementing TupleN
- File: `typr/src/scala/typr/internal/codegen/DbLibTypo.scala`
- Generate Row types as records implementing TupleN
- Add `_N()` method implementations

### Phase 11: Update DbLibTypo - generate ID records implementing TupleN
- Same file as Phase 10
- Generate ID types as records implementing TupleN (for composite keys)

### Phase 12: Update DbLibTypo - generate Fields implementing TupleExprN
- Same file as Phase 10
- Generate Fields classes implementing TupleExprN instead of FieldsExpr
- Add `_N()` and `construct()` method implementations

### Phase 13: Update Scala DSL wrapper for new types
- File: `foundations-jdbc-dsl-scala/src/scala/typr/dsl/SqlExpr.scala`
- Update Scala wrappers to work with new interface-based tuples

### Phase 14: Update Kotlin DSL wrapper for new types
- File: `foundations-jdbc-dsl-kotlin/src/kotlin/dev/typr/dsl/SqlExpr.kt`
- Update Kotlin wrappers to work with new interface-based tuples

### Phase 15: Remove deprecated types (CompositeIn, TupleIn, etc.)
- Remove old CompositeIn, TupleIn, TupleInSubquery from SqlExpr
- Clean up SqlExprVisitor

### Phase 16: Test all databases (PostgreSQL, Oracle, MariaDB, DuckDB, SQL Server)
- Run full test suite
- Verify tuple IN works correctly on all databases
- Ensure SQL Server fallback works

### Phase 17: Dog-food DSL - refactor PgMetaDb to use DSL
- File: `typr/src/scala/typr/internal/metadb/PgMetaDb.scala`
- Replace raw SQL with generated DSL
- This was the original goal that motivated this refactor

## Important Notes

### Legacy DSLs (DO NOT TOUCH)
These are separate Scala DSL implementations for the OLD typo:
- `typr-dsl-anorm/` - Anorm-specific DSL
- `typr-dsl-doobie/` - Doobie-specific DSL
- `typr-dsl-zio-jdbc/` - ZIO JDBC-specific DSL
- `typr-dsl-shared/` - Shared Scala DSL code

These are NOT used by the new foundations-based system and should not be modified.

### New DSL (what we're refactoring)
- `foundations-jdbc-dsl/` - Java-based DSL
- `foundations-jdbc-dsl-scala/` - Scala wrapper for Java DSL
- `foundations-jdbc-dsl-kotlin/` - Kotlin wrapper for Java DSL

### Code Generator
- `typr/src/scala/typr/internal/codegen/DbLibTypo.scala` - Generates code for new DSL
