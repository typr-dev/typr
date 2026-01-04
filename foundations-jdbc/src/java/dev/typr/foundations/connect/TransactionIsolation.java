package dev.typr.foundations.connect;

import java.sql.Connection;

/**
 * Transaction isolation levels for database connections.
 *
 * <p>Maps to JDBC Connection constants with database-specific names for use with HikariCP and other
 * connection pools.
 */
public enum TransactionIsolation {
  /** Read uncommitted - allows dirty reads. Lowest isolation level. */
  READ_UNCOMMITTED(Connection.TRANSACTION_READ_UNCOMMITTED, "TRANSACTION_READ_UNCOMMITTED"),

  /** Read committed - prevents dirty reads. Default for most databases. */
  READ_COMMITTED(Connection.TRANSACTION_READ_COMMITTED, "TRANSACTION_READ_COMMITTED"),

  /** Repeatable read - prevents non-repeatable reads. */
  REPEATABLE_READ(Connection.TRANSACTION_REPEATABLE_READ, "TRANSACTION_REPEATABLE_READ"),

  /** Serializable - highest isolation, prevents phantom reads. */
  SERIALIZABLE(Connection.TRANSACTION_SERIALIZABLE, "TRANSACTION_SERIALIZABLE"),

  /** No transactions - use for auto-commit mode. */
  NONE(Connection.TRANSACTION_NONE, "TRANSACTION_NONE");

  private final int jdbcLevel;
  private final String jdbcName;

  TransactionIsolation(int jdbcLevel, String jdbcName) {
    this.jdbcLevel = jdbcLevel;
    this.jdbcName = jdbcName;
  }

  /**
   * Get the JDBC isolation level constant.
   *
   * @return JDBC level from java.sql.Connection
   */
  public int jdbcLevel() {
    return jdbcLevel;
  }

  /**
   * Get the JDBC name for HikariCP configuration.
   *
   * @return JDBC constant name (e.g., "TRANSACTION_READ_COMMITTED")
   */
  public String jdbcName() {
    return jdbcName;
  }

  /**
   * Convert from JDBC isolation level.
   *
   * @param jdbcLevel JDBC level constant
   * @return matching TransactionIsolation
   * @throws IllegalArgumentException if level is unknown
   */
  public static TransactionIsolation fromJdbcLevel(int jdbcLevel) {
    for (TransactionIsolation isolation : values()) {
      if (isolation.jdbcLevel == jdbcLevel) {
        return isolation;
      }
    }
    throw new IllegalArgumentException("Unknown JDBC isolation level: " + jdbcLevel);
  }
}
