package dev.typr.foundations.connect.postgres;

/** PostgreSQL replication mode for logical/physical replication connections. */
public enum PgReplication {
  /** Normal connection, not a replication connection (default). */
  FALSE("false"),
  /** Physical replication connection. */
  TRUE("true"),
  /** Logical replication connection (database name required). */
  DATABASE("database");

  private final String value;

  PgReplication(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
