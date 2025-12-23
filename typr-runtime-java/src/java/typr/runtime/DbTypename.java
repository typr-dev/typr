package typr.runtime;

import typr.dsl.Bijection;

/**
 * Common interface for database type names. Implemented by both PgTypename (PostgreSQL) and
 * MariaTypename (MariaDB).
 */
public interface DbTypename<A> {
  /** Get the SQL type string (e.g., "text", "int4", "varchar(255)"). */
  String sqlType();

  /**
   * Whether to render type casts in SQL (e.g., ?::typename for PostgreSQL). PostgreSQL uses type
   * casts, MariaDB does not.
   */
  default boolean renderTypeCast() {
    return true; // Default to PostgreSQL behavior
  }

  /**
   * Type-safe conversion using a bijection as proof of type relationship. Since DbTypename is just
   * type metadata (SQL type string), the type parameter is phantom - no values of type A are ever
   * stored. The bijection proves that A and B are related types, providing compile-time type
   * safety.
   *
   * @param bijection proof that A and B are related types (not used at runtime)
   * @return this typename with type parameter B
   */
  default <B> DbTypename<B> to(Bijection<A, B> bijection) {
    return (DbTypename<B>) this;
  }
}
