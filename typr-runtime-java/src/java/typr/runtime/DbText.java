package typr.runtime;

/**
 * Common interface for text encoding of values. Used for bulk loading (COPY in PostgreSQL, LOAD
 * DATA in MariaDB). Implemented by both PgText and MariaText.
 */
public interface DbText<A> {
  void unsafeEncode(A a, StringBuilder sb);
}
