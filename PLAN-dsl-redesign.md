# DSL SQL Codegen Redesign

## Problem

Current Java DSL generates verbose SQL with excessive CTEs:
- Every join creates a new CTE
- All columns are re-aliased at each CTE level
- Results in ~68 lines for a 10-table join that could be ~20 lines

Example of current output:
```sql
with
salessalesperson0 as (
  (select t0.col1 AS t0_col1, t0.col2 AS t0_col2, ... from "sales"."salesperson" t0 where ...)
), humanresourcesemployee0 as (
  (select t1.col1 AS t1_col1, ... from "humanresources"."employee" t1)
), join_cte4 as (
  select t0.t0_col1 AS t0_col1, t0.t0_col2 AS t0_col2, ..., t1.t1_col1 AS t1_col1, ...
  from salessalesperson0 t0
  join humanresourcesemployee0 t1 on ...
),
-- ... more CTEs for each join level
```

## Solution

Generate direct JOINs with subqueries only where needed.

### Core Rule

A table needs a subquery when it has **any** of:
1. `WHERE` clause (from `table.where(...)`)
2. `ORDER BY` clause (from `table.orderBy(...)`)
3. `LIMIT` clause (from `table.limit(...)`)

Otherwise, reference the table directly in the JOIN.

### Target Output

```sql
SELECT
  t0.businessentityid AS t0_businessentityid,
  t0.territoryid AS t0_territoryid,
  t1.nationalidnumber AS t1_nationalidnumber,
  ...
FROM (
  SELECT * FROM "sales"."salesperson" WHERE rowguid = ?
) t0
JOIN "humanresources"."employee" t1 ON t0.businessentityid = t1.businessentityid
JOIN "person"."person" t2 ON t1.businessentityid = t2.businessentityid
JOIN "person"."businessentity" t3 ON t2.businessentityid = t3.businessentityid
JOIN (
  SELECT * FROM "person"."emailaddress" ORDER BY rowguid
) t4 ON t3.businessentityid = t4.businessentityid
WHERE t2.persontype = 'SP'
ORDER BY t1.hiredate
LIMIT 100
```

## Implementation

### Data Model

```java
record TableState(
    String tableName,
    String alias,                  // t0, t1, t2, ...
    List<Column> columns,
    Optional<Fragment> where,      // per-table WHERE
    Optional<Fragment> orderBy,    // per-table ORDER BY
    Optional<Integer> limit,       // per-table LIMIT
    Optional<Integer> offset
) {
    boolean needsSubquery() {
        return where.isPresent() || orderBy.isPresent() || limit.isPresent();
    }
}

record JoinInfo(
    TableState table,
    JoinType type,                 // INNER, LEFT
    Fragment onCondition
)

record Query(
    TableState firstTable,
    List<JoinInfo> joins,
    Optional<Fragment> where,      // top-level WHERE (after joins)
    Optional<Fragment> orderBy,    // top-level ORDER BY
    Optional<Integer> limit,       // top-level LIMIT
    Optional<Integer> offset
)
```

### Rendering Logic

```java
Fragment renderTableRef(TableState table, RenderCtx ctx) {
    if (table.needsSubquery()) {
        return frag("(\n  SELECT ")
            .append(renderColumns(table))
            .append(frag("\n  FROM "))
            .append(quoteTable(table.tableName))
            .append(table.where.map(w -> frag("\n  WHERE ").append(w)))
            .append(table.orderBy.map(o -> frag("\n  ORDER BY ").append(o)))
            .append(table.limit.map(l -> frag("\n  LIMIT " + l)))
            .append(frag("\n) "))
            .append(frag(table.alias));
    } else {
        return frag(quoteTable(table.tableName))
            .append(frag(" "))
            .append(frag(table.alias));
    }
}

Fragment renderQuery(Query query) {
    var allTables = collectAllTables(query);

    // SELECT: list all columns with unique aliases
    var select = frag("SELECT ")
        .append(allTables.stream()
            .flatMap(t -> t.columns.stream()
                .map(c -> t.alias + "." + quote(c.name) + " AS " + t.alias + "_" + c.name))
            .collect(joining(",\n  ")));

    // FROM: first table (possibly as subquery)
    var from = frag("\nFROM ").append(renderTableRef(query.firstTable));

    // JOINs
    var joins = query.joins.stream()
        .map(j -> frag("\n")
            .append(frag(j.type.sql()))  // "JOIN" or "LEFT JOIN"
            .append(frag(" "))
            .append(renderTableRef(j.table))
            .append(frag("\n  ON "))
            .append(j.onCondition))
        .collect(toFragment());

    // Top-level clauses
    var where = query.where.map(w -> frag("\nWHERE ").append(w));
    var orderBy = query.orderBy.map(o -> frag("\nORDER BY ").append(o));
    var limit = query.limit.map(l -> frag("\nLIMIT " + l));
    var offset = query.offset.map(o -> frag("\nOFFSET " + o));

    return select.append(from).append(joins)
        .append(where).append(orderBy).append(limit).append(offset);
}
```

### Column Reference in ON/WHERE

When rendering column references:
- In subquery: reference original column name
- After subquery: reference aliased name (`t0_columnname`)
- Direct table: reference with table alias (`t0.columnname`)

```java
Fragment renderColumnRef(Column col, TableState table, RenderCtx ctx) {
    if (table.needsSubquery() && ctx.isOutsideSubquery()) {
        // After subquery, use the aliased name
        return frag(table.alias + "." + table.alias + "_" + col.name);
    } else {
        // Direct reference
        return frag(table.alias + "." + quote(col.name));
    }
}
```

## Migration

### Files to Modify

1. `typo-dsl-java/src/java/typo/dsl/SelectBuilderSql.java`
   - Replace `Instantiated` and CTE-based approach
   - Implement `TableState`, `JoinInfo`, `Query` records
   - New `renderTableRef` and `renderQuery` methods

2. `typo-dsl-java/src/java/typo/dsl/RenderCtx.java`
   - Track whether we're inside/outside a subquery
   - Simplify alias resolution (no more CTE mapping)

3. Snapshot tests will need updating:
   - `snapshot-tests/java-sql/DSLTest/*.sql`

### Backward Compatibility

- API remains the same (SelectBuilder interface unchanged)
- Only SQL output changes
- All existing tests should pass (same semantics, different SQL)

## Benefits

1. **~3-5x smaller SQL** for multi-table joins
2. **Better query plans** - optimizer sees full join structure
3. **Easier debugging** - SQL reads like hand-written queries
4. **Simpler code** - no CTE propagation complexity
5. **Cross-database** - works on PostgreSQL, MariaDB, MySQL

## Not Included (Future Work)

- Predicate pushdown (moving WHERE clauses closer to base tables)
- Column pruning (only selecting used columns)
- Join reordering hints
