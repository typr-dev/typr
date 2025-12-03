package typo.runtime;

/**
 * Common interface for database type names.
 * Implemented by both PgTypename (PostgreSQL) and MariaTypename (MariaDB).
 */
public interface DbTypename<A> {
    /**
     * Get the SQL type string (e.g., "text", "int4", "varchar(255)").
     */
    String sqlType();

    /**
     * Whether to render type casts in SQL (e.g., ?::typename for PostgreSQL).
     * PostgreSQL uses type casts, MariaDB does not.
     */
    default boolean renderTypeCast() {
        return true; // Default to PostgreSQL behavior
    }
}
