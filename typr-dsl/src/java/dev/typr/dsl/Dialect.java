package dev.typr.dsl;

import dev.typr.foundations.DbType;
import dev.typr.foundations.Fragment;
import java.util.ArrayList;
import java.util.List;

/**
 * Interface for database dialect-specific SQL syntax. Different databases use different quote
 * characters and type cast syntax.
 */
public interface Dialect {

  /**
   * The dialect-native 64-bit integer type, used by {@link SelectBuilderSql#count} to read the
   * result of {@code count(*)}. Each dialect returns its own {@code bigint} so we don't pull in
   * {@code PgTypes} (and its postgresql-jdbc dependency) on non-PostgreSQL classpaths.
   */
  DbType<Long> bigint();

  /** Quote an identifier (table name, column name, etc.) */
  String quoteIdent(String name);

  /** Escape a quote character within an identifier. PostgreSQL: " -> "" MariaDB: ` -> `` */
  String escapeIdent(String name);

  /**
   * Wrap a Fragment with a type cast. Different databases use different syntax: PostgreSQL uses
   * ::type, MariaDB uses CAST(value AS type) Preserves Fragment composability and parameter
   * binding.
   */
  Fragment typeCast(Fragment value, String typeName);

  /**
   * Format a column reference with an alias. PostgreSQL uses (alias)."column" format to allow
   * proper column reference from CTEs. MariaDB uses alias.`column` format.
   */
  String columnRef(String alias, String quotedColumn);

  /**
   * Generate SQL fragment for LIMIT clause. PostgreSQL/MariaDB: LIMIT n Oracle: FETCH FIRST n ROWS
   * ONLY
   */
  default String limitClause(int n) {
    return "LIMIT " + n;
  }

  /** Generate SQL fragment for OFFSET clause. PostgreSQL/MariaDB: OFFSET n Oracle: OFFSET n ROWS */
  default String offsetClause(int n) {
    return "OFFSET " + n;
  }

  /**
   * Append OFFSET and LIMIT clauses to a subquery fragment. Handles dialect-specific requirements
   * like SQL Server needing ORDER BY for OFFSET...FETCH by using the first ID column (or first
   * column if no ID) when no explicit ORDER BY is provided.
   *
   * <p>Note: The caller is responsible for rendering ORDER BY before calling this method. This
   * method only adds a default ORDER BY when the dialect requires it and none was provided.
   *
   * @param subquery the base subquery fragment (after WHERE, before ORDER BY/OFFSET/LIMIT)
   * @param alias the table alias for column references
   * @param queryIsAlreadyOrdered true if the query already has an ORDER BY clause
   * @param limit optional limit value
   * @param offset optional offset value
   * @param fields list of fields (used to find ID column for default ORDER BY when needed)
   * @return the subquery with OFFSET, LIMIT (and possibly ORDER BY) appended
   */
  default Fragment appendPaginationClauses(
      Fragment subquery,
      String alias,
      boolean queryIsAlreadyOrdered,
      java.util.Optional<Integer> limit,
      java.util.Optional<Integer> offset,
      java.util.List<? extends SqlExpr.FieldLike<?, ?>> fields) {

    // Add OFFSET if present
    if (offset.isPresent()) {
      subquery = subquery.append(Fragment.of(" " + offsetClause(offset.get())));
    }

    // Add LIMIT if present
    if (limit.isPresent()) {
      subquery = subquery.append(Fragment.of(" " + limitClause(limit.get())));
    }

    return subquery;
  }

  // ==================== Null-Safe Comparison Methods ====================

  /**
   * Render a null-safe equality comparison between two scalar values. Different databases have
   * different optimal syntax:
   *
   * <ul>
   *   <li>PostgreSQL/DuckDB/SQL Server 2022+: {@code a IS NOT DISTINCT FROM b}
   *   <li>MariaDB/MySQL: {@code a <=> b}
   *   <li>Oracle: {@code DECODE(a, b, 1, 0) = 1}
   *   <li>SQLite: {@code a IS b}
   *   <li>DB2: {@code a IS NOT DISTINCT FROM b}
   * </ul>
   *
   * @param left the left operand (already rendered)
   * @param right the right operand (already rendered)
   * @return a Fragment representing the null-safe equality check
   */
  Fragment nullSafeEquals(Fragment left, Fragment right);

  /**
   * Render a null-safe inequality comparison between two scalar values. This is the negation of
   * {@link #nullSafeEquals}.
   *
   * @param left the left operand (already rendered)
   * @param right the right operand (already rendered)
   * @return a Fragment representing the null-safe inequality check
   */
  Fragment nullSafeNotEquals(Fragment left, Fragment right);

  /**
   * Render a null-safe equality comparison between two tuples (rows). Default implementation
   * expands to column-by-column comparison using {@link #nullSafeEquals}. Dialects with native
   * tuple null-safe support (e.g., MariaDB with {@code (a,b) <=> (x,y)}) can override for optimal
   * SQL.
   *
   * @param leftCols the left tuple columns (already rendered)
   * @param rightCols the right tuple columns (already rendered)
   * @return a Fragment representing the null-safe tuple equality check
   */
  default Fragment nullSafeTupleEquals(List<Fragment> leftCols, List<Fragment> rightCols) {
    if (leftCols.size() != rightCols.size()) {
      throw new IllegalArgumentException(
          "Tuple size mismatch: " + leftCols.size() + " vs " + rightCols.size());
    }
    List<Fragment> conditions = new ArrayList<>();
    for (int i = 0; i < leftCols.size(); i++) {
      conditions.add(nullSafeEquals(leftCols.get(i), rightCols.get(i)));
    }
    return Fragment.of("(").append(Fragment.and(conditions)).append(Fragment.of(")"));
  }

  /**
   * Render a null-safe inequality comparison between two tuples. Default implementation negates
   * {@link #nullSafeTupleEquals}.
   *
   * @param leftCols the left tuple columns (already rendered)
   * @param rightCols the right tuple columns (already rendered)
   * @return a Fragment representing the null-safe tuple inequality check
   */
  default Fragment nullSafeTupleNotEquals(List<Fragment> leftCols, List<Fragment> rightCols) {
    return Fragment.of("NOT ").append(nullSafeTupleEquals(leftCols, rightCols));
  }

  // ==================== Tuple IN Support ====================

  /**
   * Whether this dialect supports native tuple IN syntax like {@code (a, b) IN ((1, 2), (3, 4))}.
   * Dialects that don't support this (SQL Server, DuckDB) must use EXISTS pattern instead.
   *
   * @return true if tuple IN is supported
   */
  default boolean supportsTupleIn() {
    return true;
  }

  /**
   * Whether this dialect supports NULLS FIRST/LAST in ORDER BY clauses. PostgreSQL, Oracle, SQL
   * Server 2022+ support this syntax. MariaDB/MySQL do not.
   *
   * @return true if NULLS FIRST/LAST is supported
   */
  default boolean supportsNullsFirstLast() {
    return true;
  }

  /**
   * Render a tuple IN expression with literal values. Chooses between native tuple IN syntax and
   * EXISTS pattern based on dialect support and nullability.
   *
   * @param lhsCols the left-hand side tuple columns (already rendered)
   * @param rhsRows the right-hand side rows, each row is a list of column values (already rendered)
   * @param hasNullable true if any LHS column is nullable
   * @return Fragment for the complete expression
   */
  default Fragment renderTupleIn(
      List<Fragment> lhsCols, List<List<Fragment>> rhsRows, boolean hasNullable) {
    if (rhsRows.isEmpty()) {
      return Fragment.of("1=0"); // Empty IN is always false
    }

    // Use EXISTS pattern when dialect doesn't support tuple IN or when nullable
    if (!supportsTupleIn() || hasNullable) {
      return renderTupleInExists(lhsCols, rhsRows, hasNullable);
    }

    // Native tuple IN: (a, b) IN ((1, 2), (3, 4))
    Fragment lhs = Fragment.of("(").append(Fragment.comma(lhsCols)).append(Fragment.of(")"));
    List<Fragment> rowFrags = new ArrayList<>();
    for (List<Fragment> row : rhsRows) {
      rowFrags.add(Fragment.of("(").append(Fragment.comma(row)).append(Fragment.of(")")));
    }
    return lhs.append(Fragment.of(" IN ("))
        .append(Fragment.comma(rowFrags))
        .append(Fragment.of(")"));
  }

  /**
   * Render a tuple IN expression using EXISTS pattern with VALUES. Used when dialect doesn't
   * support tuple IN or when null-safe comparison is needed.
   *
   * @param lhsCols the left-hand side tuple columns (already rendered)
   * @param rhsRows the right-hand side rows (already rendered)
   * @param hasNullable true if null-safe comparison should be used
   * @return Fragment for EXISTS (SELECT 1 FROM (VALUES ...) AS v(...) WHERE ...)
   */
  default Fragment renderTupleInExists(
      List<Fragment> lhsCols, List<List<Fragment>> rhsRows, boolean hasNullable) {
    int numCols = lhsCols.size();

    // Build VALUES clause: VALUES (?, ?), (?, ?)
    List<Fragment> valueRows = new ArrayList<>();
    for (List<Fragment> row : rhsRows) {
      valueRows.add(Fragment.of("(").append(Fragment.comma(row)).append(Fragment.of(")")));
    }
    Fragment valuesClause = Fragment.of("VALUES ").append(Fragment.comma(valueRows));

    // Build column aliases: c1, c2, ...
    StringBuilder colAliases = new StringBuilder();
    for (int i = 0; i < numCols; i++) {
      if (i > 0) colAliases.append(", ");
      colAliases.append("c").append(i + 1);
    }

    // Build WHERE clause
    List<Fragment> conditions = new ArrayList<>();
    for (int i = 0; i < numCols; i++) {
      Fragment colFrag = lhsCols.get(i);
      Fragment valueFrag = Fragment.of("v.c" + (i + 1));
      if (hasNullable) {
        conditions.add(nullSafeEquals(colFrag, valueFrag));
      } else {
        conditions.add(colFrag.append(Fragment.of(" = ")).append(valueFrag));
      }
    }
    Fragment whereClause = Fragment.and(conditions);

    return Fragment.of("EXISTS (SELECT 1 FROM (")
        .append(valuesClause)
        .append(Fragment.of(") AS v(" + colAliases + ") WHERE "))
        .append(whereClause)
        .append(Fragment.of(")"));
  }

  /**
   * Render a tuple IN expression with a subquery. Chooses between native tuple IN syntax and EXISTS
   * pattern based on dialect support and nullability.
   *
   * @param lhsCols the left-hand side tuple columns (already rendered)
   * @param subquerySql the subquery SQL (already rendered)
   * @param hasNullable true if any LHS column is nullable
   * @return Fragment for the complete expression
   */
  default Fragment renderTupleInSubquery(
      List<Fragment> lhsCols, Fragment subquerySql, boolean hasNullable) {
    // Use EXISTS pattern when dialect doesn't support tuple IN or when nullable
    if (!supportsTupleIn() || hasNullable) {
      return renderTupleInSubqueryExists(lhsCols, subquerySql, hasNullable);
    }

    // Native tuple IN: (a, b) IN (SELECT x, y FROM ...)
    Fragment lhs = Fragment.of("(").append(Fragment.comma(lhsCols)).append(Fragment.of(")"));
    return lhs.append(Fragment.of(" IN (")).append(subquerySql).append(Fragment.of(")"));
  }

  /**
   * Render a tuple IN subquery using EXISTS pattern. Used when dialect doesn't support tuple IN or
   * when null-safe comparison is needed.
   *
   * @param lhsCols the left-hand side tuple columns (already rendered)
   * @param subquerySql the subquery SQL (already rendered)
   * @param hasNullable true if null-safe comparison should be used
   * @return Fragment for EXISTS (SELECT 1 FROM (...) AS sq(...) WHERE ...)
   */
  default Fragment renderTupleInSubqueryExists(
      List<Fragment> lhsCols, Fragment subquerySql, boolean hasNullable) {
    int numCols = lhsCols.size();

    // Build column aliases: c1, c2, ...
    StringBuilder colAliases = new StringBuilder();
    for (int i = 0; i < numCols; i++) {
      if (i > 0) colAliases.append(", ");
      colAliases.append("c").append(i + 1);
    }

    // Build WHERE clause
    List<Fragment> conditions = new ArrayList<>();
    for (int i = 0; i < numCols; i++) {
      Fragment colFrag = lhsCols.get(i);
      Fragment valueFrag = Fragment.of("sq.c" + (i + 1));
      if (hasNullable) {
        conditions.add(nullSafeEquals(colFrag, valueFrag));
      } else {
        conditions.add(colFrag.append(Fragment.of(" = ")).append(valueFrag));
      }
    }
    Fragment whereClause = Fragment.and(conditions);

    return Fragment.of("EXISTS (SELECT 1 FROM (")
        .append(subquerySql)
        .append(Fragment.of(") AS sq(" + colAliases + ") WHERE "))
        .append(whereClause)
        .append(Fragment.of(")"));
  }

  /**
   * Quote a table name for SQL, handling schema.table format and special characters. Each part is
   * quoted if it contains special characters or is not already quoted.
   */
  default String quoteTableName(String tableName) {
    String[] parts = tableName.split("\\.");
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < parts.length; i++) {
      if (i > 0) result.append(".");
      String part = parts[i];
      if (isAlreadyQuoted(part)) {
        result.append(part);
      } else if (needsQuoting(part)) {
        result.append(quoteIdent(escapeIdent(part)));
      } else {
        result.append(part);
      }
    }
    return result.toString();
  }

  /** Check if an identifier is already quoted with double quotes, backticks, or square brackets. */
  private static boolean isAlreadyQuoted(String identifier) {
    if (identifier.length() < 2) return false;
    char first = identifier.charAt(0);
    char last = identifier.charAt(identifier.length() - 1);
    return (first == '"' && last == '"')
        || (first == '`' && last == '`')
        || (first == '[' && last == ']');
  }

  /**
   * Check if an identifier needs quoting (contains non-alphanumeric, non-underscore characters).
   */
  private static boolean needsQuoting(String identifier) {
    for (char c : identifier.toCharArray()) {
      if (!Character.isLetterOrDigit(c) && c != '_') {
        return true;
      }
    }
    return false;
  }

  /** PostgreSQL dialect - uses double quotes for identifiers and :: for casts. */
  Dialect POSTGRESQL =
      new Dialect() {
        @Override
        public DbType<Long> bigint() {
          return dev.typr.foundations.PgTypes.int8;
        }

        @Override
        public String quoteIdent(String name) {
          return "\"" + name + "\"";
        }

        @Override
        public String escapeIdent(String name) {
          return name.replace("\"", "\"\"");
        }

        @Override
        public Fragment typeCast(Fragment value, String typeName) {
          if (typeName == null || typeName.isEmpty()) {
            return value;
          }
          return value.append(Fragment.of("::" + typeName));
        }

        @Override
        public String columnRef(String alias, String quotedColumn) {
          // PostgreSQL supports (alias)."column" format
          return "(" + alias + ")." + quotedColumn;
        }

        @Override
        public Fragment nullSafeEquals(Fragment left, Fragment right) {
          // PostgreSQL: a IS NOT DISTINCT FROM b
          return Fragment.of("(")
              .append(left)
              .append(Fragment.of(" IS NOT DISTINCT FROM "))
              .append(right)
              .append(Fragment.of(")"));
        }

        @Override
        public Fragment nullSafeNotEquals(Fragment left, Fragment right) {
          // PostgreSQL: a IS DISTINCT FROM b
          return Fragment.of("(")
              .append(left)
              .append(Fragment.of(" IS DISTINCT FROM "))
              .append(right)
              .append(Fragment.of(")"));
        }

        @Override
        public Fragment nullSafeTupleEquals(List<Fragment> leftCols, List<Fragment> rightCols) {
          // PostgreSQL supports ROW() IS NOT DISTINCT FROM ROW()
          return Fragment.of("(ROW(")
              .append(Fragment.comma(leftCols))
              .append(Fragment.of(") IS NOT DISTINCT FROM ROW("))
              .append(Fragment.comma(rightCols))
              .append(Fragment.of("))"));
        }

        @Override
        public Fragment nullSafeTupleNotEquals(List<Fragment> leftCols, List<Fragment> rightCols) {
          // PostgreSQL supports ROW() IS DISTINCT FROM ROW()
          return Fragment.of("(ROW(")
              .append(Fragment.comma(leftCols))
              .append(Fragment.of(") IS DISTINCT FROM ROW("))
              .append(Fragment.comma(rightCols))
              .append(Fragment.of("))"));
        }
      };

  /** MariaDB dialect - uses backticks for identifiers and CAST() for casts. */
  Dialect MARIADB =
      new Dialect() {
        @Override
        public DbType<Long> bigint() {
          return dev.typr.foundations.MariaTypes.bigint;
        }

        @Override
        public String quoteIdent(String name) {
          return "`" + name + "`";
        }

        @Override
        public String escapeIdent(String name) {
          return name.replace("`", "``");
        }

        @Override
        public Fragment typeCast(Fragment value, String typeName) {
          if (typeName == null || typeName.isEmpty()) {
            return value;
          }
          return Fragment.of("CAST(").append(value).append(Fragment.of(" AS " + typeName + ")"));
        }

        @Override
        public String columnRef(String alias, String quotedColumn) {
          // MariaDB uses simple alias.`column` format
          return alias + "." + quotedColumn;
        }

        @Override
        public Fragment nullSafeEquals(Fragment left, Fragment right) {
          // MariaDB/MySQL: a <=> b (spaceship operator)
          return Fragment.of("(")
              .append(left)
              .append(Fragment.of(" <=> "))
              .append(right)
              .append(Fragment.of(")"));
        }

        @Override
        public Fragment nullSafeNotEquals(Fragment left, Fragment right) {
          // MariaDB/MySQL: NOT (a <=> b)
          return Fragment.of("NOT (")
              .append(left)
              .append(Fragment.of(" <=> "))
              .append(right)
              .append(Fragment.of(")"));
        }

        @Override
        public Fragment nullSafeTupleEquals(List<Fragment> leftCols, List<Fragment> rightCols) {
          // MariaDB supports tuple spaceship: (a, b) <=> (x, y)
          return Fragment.of("((")
              .append(Fragment.comma(leftCols))
              .append(Fragment.of(") <=> ("))
              .append(Fragment.comma(rightCols))
              .append(Fragment.of("))"));
        }

        @Override
        public Fragment nullSafeTupleNotEquals(List<Fragment> leftCols, List<Fragment> rightCols) {
          // MariaDB: NOT ((a, b) <=> (x, y))
          return Fragment.of("NOT ((")
              .append(Fragment.comma(leftCols))
              .append(Fragment.of(") <=> ("))
              .append(Fragment.comma(rightCols))
              .append(Fragment.of("))"));
        }

        @Override
        public boolean supportsNullsFirstLast() {
          // MariaDB/MySQL do not support NULLS FIRST/LAST syntax
          return false;
        }

        @Override
        public Fragment appendPaginationClauses(
            Fragment subquery,
            String alias,
            boolean queryIsAlreadyOrdered,
            java.util.Optional<Integer> limit,
            java.util.Optional<Integer> offset,
            java.util.List<? extends SqlExpr.FieldLike<?, ?>> fields) {

          // MariaDB uses LIMIT before OFFSET: LIMIT n OFFSET m
          if (limit.isPresent()) {
            subquery = subquery.append(Fragment.of(" " + limitClause(limit.get())));
          }

          if (offset.isPresent()) {
            subquery = subquery.append(Fragment.of(" " + offsetClause(offset.get())));
          }

          return subquery;
        }
      };

  /**
   * DuckDB dialect - uses double quotes for identifiers and :: for casts (PostgreSQL-compatible).
   */
  Dialect DUCKDB =
      new Dialect() {
        @Override
        public DbType<Long> bigint() {
          return dev.typr.foundations.DuckDbTypes.bigint;
        }

        @Override
        public String quoteIdent(String name) {
          return "\"" + name + "\"";
        }

        @Override
        public String escapeIdent(String name) {
          return name.replace("\"", "\"\"");
        }

        @Override
        public Fragment typeCast(Fragment value, String typeName) {
          if (typeName == null || typeName.isEmpty()) {
            return value;
          }
          return value.append(Fragment.of("::" + typeName));
        }

        @Override
        public String columnRef(String alias, String quotedColumn) {
          // DuckDB uses simple alias."column" format (not PostgreSQL's (alias)."column")
          return alias + "." + quotedColumn;
        }

        @Override
        public Fragment nullSafeEquals(Fragment left, Fragment right) {
          // DuckDB: a IS NOT DISTINCT FROM b
          return Fragment.of("(")
              .append(left)
              .append(Fragment.of(" IS NOT DISTINCT FROM "))
              .append(right)
              .append(Fragment.of(")"));
        }

        @Override
        public Fragment nullSafeNotEquals(Fragment left, Fragment right) {
          // DuckDB: a IS DISTINCT FROM b
          return Fragment.of("(")
              .append(left)
              .append(Fragment.of(" IS DISTINCT FROM "))
              .append(right)
              .append(Fragment.of(")"));
        }

        @Override
        public boolean supportsTupleIn() {
          // DuckDB doesn't support tuple IN syntax like (a,b) IN ((1,2), (3,4))
          return false;
        }

        // DuckDB doesn't support tuple IS NOT DISTINCT FROM, uses default column-by-column
      };

  /** Oracle dialect - uses double quotes for identifiers and FETCH FIRST for limits. */
  Dialect ORACLE =
      new Dialect() {
        @Override
        public DbType<Long> bigint() {
          return dev.typr.foundations.OracleTypes.numberLong;
        }

        @Override
        public String quoteIdent(String name) {
          return "\"" + name + "\"";
        }

        @Override
        public String escapeIdent(String name) {
          return name.replace("\"", "\"\"");
        }

        @Override
        public Fragment typeCast(Fragment value, String typeName) {
          if (typeName == null || typeName.isEmpty()) {
            return value;
          }
          // Oracle uses CAST() syntax, not ::
          return Fragment.of("CAST(").append(value).append(Fragment.of(" AS " + typeName + ")"));
        }

        @Override
        public String columnRef(String alias, String quotedColumn) {
          // Oracle uses simple alias."column" format
          return alias + "." + quotedColumn;
        }

        @Override
        public String limitClause(int n) {
          // Oracle 12c+ syntax
          return "FETCH FIRST " + n + " ROWS ONLY";
        }

        @Override
        public String offsetClause(int n) {
          // Oracle 12c+ syntax
          return "OFFSET " + n + " ROWS";
        }

        @Override
        public Fragment nullSafeEquals(Fragment left, Fragment right) {
          // Oracle: DECODE(a, b, 1, 0) = 1
          // DECODE treats NULL = NULL as true internally
          return Fragment.of("(DECODE(")
              .append(left)
              .append(Fragment.of(", "))
              .append(right)
              .append(Fragment.of(", 1, 0) = 1)"));
        }

        @Override
        public Fragment nullSafeNotEquals(Fragment left, Fragment right) {
          // Oracle: DECODE(a, b, 1, 0) = 0
          return Fragment.of("(DECODE(")
              .append(left)
              .append(Fragment.of(", "))
              .append(right)
              .append(Fragment.of(", 1, 0) = 0)"));
        }

        // Oracle uses default column-by-column for tuples
      };

  /** SQL Server dialect - uses square brackets for identifiers and CAST() for casts. */
  Dialect SQLSERVER =
      new Dialect() {
        @Override
        public DbType<Long> bigint() {
          return dev.typr.foundations.SqlServerTypes.bigint;
        }

        @Override
        public String quoteIdent(String name) {
          return "[" + name + "]";
        }

        @Override
        public String escapeIdent(String name) {
          return name.replace("]", "]]");
        }

        @Override
        public Fragment typeCast(Fragment value, String typeName) {
          if (typeName == null || typeName.isEmpty()) {
            return value;
          }
          return Fragment.of("CAST(").append(value).append(Fragment.of(" AS " + typeName + ")"));
        }

        @Override
        public String columnRef(String alias, String quotedColumn) {
          // SQL Server uses simple alias.[column] format
          return alias + "." + quotedColumn;
        }

        @Override
        public String limitClause(int n) {
          // SQL Server uses FETCH NEXT syntax (requires ORDER BY and OFFSET in query)
          return "FETCH NEXT " + n + " ROWS ONLY";
        }

        @Override
        public String offsetClause(int n) {
          // SQL Server uses OFFSET with ROWS
          return "OFFSET " + n + " ROWS";
        }

        @Override
        public Fragment appendPaginationClauses(
            Fragment subquery,
            String alias,
            boolean queryIsAlreadyOrdered,
            java.util.Optional<Integer> limit,
            java.util.Optional<Integer> offset,
            java.util.List<? extends SqlExpr.FieldLike<?, ?>> fields) {

          // SQL Server requires ORDER BY for OFFSET...FETCH syntax
          boolean needsOrderBy =
              (offset.isPresent() || limit.isPresent()) && !queryIsAlreadyOrdered;

          if (needsOrderBy && !fields.isEmpty()) {
            // Find ID columns for deterministic ordering (handles compound keys)
            String orderCols = buildOrderByColumns(alias, fields);
            subquery = subquery.append(Fragment.of(" order by " + orderCols));
          }

          // SQL Server requires OFFSET when using FETCH, default to 0 if only limit specified
          if (offset.isPresent()) {
            subquery = subquery.append(Fragment.of(" " + offsetClause(offset.get())));
          } else if (limit.isPresent()) {
            subquery = subquery.append(Fragment.of(" " + offsetClause(0)));
          }

          if (limit.isPresent()) {
            subquery = subquery.append(Fragment.of(" " + limitClause(limit.get())));
          }

          return subquery;
        }

        private String buildOrderByColumns(
            String alias, java.util.List<? extends SqlExpr.FieldLike<?, ?>> fields) {
          // Collect all ID columns for compound key support
          java.util.List<String> idCols = new java.util.ArrayList<>();
          for (var field : fields) {
            if (field instanceof SqlExpr.IdField<?, ?>) {
              idCols.add(alias + "." + quoteIdent(field.column()));
            }
          }
          // Fall back to first column if no ID columns found
          if (idCols.isEmpty()) {
            return alias + "." + quoteIdent(fields.getFirst().column());
          }
          return String.join(", ", idCols);
        }

        @Override
        public Fragment nullSafeEquals(Fragment left, Fragment right) {
          // SQL Server 2022+: a IS NOT DISTINCT FROM b
          return Fragment.of("(")
              .append(left)
              .append(Fragment.of(" IS NOT DISTINCT FROM "))
              .append(right)
              .append(Fragment.of(")"));
        }

        @Override
        public Fragment nullSafeNotEquals(Fragment left, Fragment right) {
          // SQL Server 2022+: a IS DISTINCT FROM b
          return Fragment.of("(")
              .append(left)
              .append(Fragment.of(" IS DISTINCT FROM "))
              .append(right)
              .append(Fragment.of(")"));
        }

        @Override
        public boolean supportsTupleIn() {
          // SQL Server doesn't support tuple IN syntax like (a,b) IN ((1,2), (3,4))
          return false;
        }

        // SQL Server uses default column-by-column for tuples
      };

  /**
   * DB2 dialect - uses double quotes for identifiers, CAST() for casts, and FETCH FIRST for limits.
   */
  Dialect DB2 =
      new Dialect() {
        @Override
        public DbType<Long> bigint() {
          return dev.typr.foundations.Db2Types.bigint;
        }

        @Override
        public String quoteIdent(String name) {
          return "\"" + name + "\"";
        }

        @Override
        public String escapeIdent(String name) {
          return name.replace("\"", "\"\"");
        }

        @Override
        public Fragment typeCast(Fragment value, String typeName) {
          if (typeName == null || typeName.isEmpty()) {
            return value;
          }
          // DB2 uses CAST() syntax
          return Fragment.of("CAST(").append(value).append(Fragment.of(" AS " + typeName + ")"));
        }

        @Override
        public String columnRef(String alias, String quotedColumn) {
          // DB2 uses simple alias."column" format
          return alias + "." + quotedColumn;
        }

        @Override
        public String limitClause(int n) {
          // DB2 uses FETCH FIRST syntax
          return "FETCH FIRST " + n + " ROWS ONLY";
        }

        @Override
        public String offsetClause(int n) {
          // DB2 uses OFFSET with ROWS
          return "OFFSET " + n + " ROWS";
        }

        @Override
        public Fragment nullSafeEquals(Fragment left, Fragment right) {
          // DB2 z/OS: a IS NOT DISTINCT FROM b
          // DB2 LUW: Also supports IS NOT DISTINCT FROM in recent versions
          return Fragment.of("(")
              .append(left)
              .append(Fragment.of(" IS NOT DISTINCT FROM "))
              .append(right)
              .append(Fragment.of(")"));
        }

        @Override
        public Fragment nullSafeNotEquals(Fragment left, Fragment right) {
          // DB2: a IS DISTINCT FROM b
          return Fragment.of("(")
              .append(left)
              .append(Fragment.of(" IS DISTINCT FROM "))
              .append(right)
              .append(Fragment.of(")"));
        }

        @Override
        public boolean supportsTupleIn() {
          // DB2 doesn't support tuple IN syntax like (a,b) IN ((1,2), (3,4))
          return false;
        }

        // DB2 uses default column-by-column for tuples
      };
}
