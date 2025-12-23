package typr.runtime;

import typr.dsl.Bijection;

/**
 * Common interface for database type codecs. Implemented by both PgType (PostgreSQL) and MariaType
 * (MariaDB).
 */
public interface DbType<A> {
  /** Get the typename for SQL rendering (e.g., for casts like ?::typename). */
  DbTypename<A> typename();

  /** Get the read codec for reading ResultSet columns. */
  DbRead<A> read();

  /** Get the write codec for setting PreparedStatement parameters. */
  DbWrite<A> write();

  /** Get the text encoder for bulk loading (COPY/LOAD DATA). */
  DbText<A> text();

  /**
   * Get the JSON codec for converting values to/from JSON format that the database can
   * produce/consume.
   */
  DbJson<A> json();

  /** Create an optional version of this type. */
  DbType<java.util.Optional<A>> opt();

  /**
   * Convert this DbType to handle a different type using a bijection. The bijection converts values
   * bidirectionally while preserving the underlying database type semantics.
   *
   * @param bijection The bijection to convert between A and B
   * @param <B> The target type
   * @return A DbType that handles type B by converting to/from type A
   */
  <B> DbType<B> to(Bijection<A, B> bijection);
}
