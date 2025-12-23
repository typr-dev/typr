package typr.dsl;

import typr.runtime.Fragment;

/**
 * Interface for database dialect-specific SQL syntax. Different databases use different quote
 * characters and type cast syntax.
 */
public interface Dialect {
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

  /** Check if an identifier is already quoted with double quotes or backticks. */
  private static boolean isAlreadyQuoted(String identifier) {
    if (identifier.length() < 2) return false;
    char first = identifier.charAt(0);
    char last = identifier.charAt(identifier.length() - 1);
    return (first == '"' && last == '"') || (first == '`' && last == '`');
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
          return value.append(Fragment.lit("::" + typeName));
        }

        @Override
        public String columnRef(String alias, String quotedColumn) {
          // PostgreSQL supports (alias)."column" format
          return "(" + alias + ")." + quotedColumn;
        }
      };

  /** MariaDB dialect - uses backticks for identifiers and CAST() for casts. */
  Dialect MARIADB =
      new Dialect() {
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
          return Fragment.lit("CAST(").append(value).append(Fragment.lit(" AS " + typeName + ")"));
        }

        @Override
        public String columnRef(String alias, String quotedColumn) {
          // MariaDB uses simple alias.`column` format
          return alias + "." + quotedColumn;
        }
      };

  /**
   * DuckDB dialect - uses double quotes for identifiers and :: for casts (PostgreSQL-compatible).
   */
  Dialect DUCKDB =
      new Dialect() {
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
          return value.append(Fragment.lit("::" + typeName));
        }

        @Override
        public String columnRef(String alias, String quotedColumn) {
          // DuckDB uses simple alias."column" format (not PostgreSQL's (alias)."column")
          return alias + "." + quotedColumn;
        }
      };

  /** Oracle dialect - uses double quotes for identifiers and FETCH FIRST for limits. */
  Dialect ORACLE =
      new Dialect() {
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
          return Fragment.lit("CAST(").append(value).append(Fragment.lit(" AS " + typeName + ")"));
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
      };

  /** SQL Server dialect - uses square brackets for identifiers and CAST() for casts. */
  Dialect SQLSERVER =
      new Dialect() {
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
          return Fragment.lit("CAST(").append(value).append(Fragment.lit(" AS " + typeName + ")"));
        }

        @Override
        public String columnRef(String alias, String quotedColumn) {
          // SQL Server uses simple alias.[column] format
          return alias + "." + quotedColumn;
        }
      };
}
