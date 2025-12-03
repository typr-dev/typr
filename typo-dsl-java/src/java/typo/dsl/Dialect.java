package typo.dsl;

import typo.runtime.Fragment;

/**
 * Interface for database dialect-specific SQL syntax.
 * Different databases use different quote characters and type cast syntax.
 */
public interface Dialect {
    /**
     * Quote an identifier (table name, column name, etc.)
     */
    String quoteIdent(String name);

    /**
     * Escape a quote character within an identifier.
     * PostgreSQL: " -> ""
     * MariaDB: ` -> ``
     */
    String escapeIdent(String name);

    /**
     * Wrap a Fragment with a type cast.
     * Different databases use different syntax: PostgreSQL uses ::type, MariaDB uses CAST(value AS type)
     * Preserves Fragment composability and parameter binding.
     */
    Fragment typeCast(Fragment value, String typeName);

    /**
     * Format a column reference with an alias.
     * PostgreSQL uses (alias)."column" format to allow proper column reference from CTEs.
     * MariaDB uses alias.`column` format.
     */
    String columnRef(String alias, String quotedColumn);

    /**
     * PostgreSQL dialect - uses double quotes for identifiers and :: for casts.
     */
    Dialect POSTGRESQL = new Dialect() {
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

    /**
     * MariaDB dialect - uses backticks for identifiers and CAST() for casts.
     */
    Dialect MARIADB = new Dialect() {
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
}
