package dev.typr.foundations.connect.postgres;

/** PostgreSQL target server type for connection routing in multi-server setups. */
public enum PgTargetServerType {
  /** Connect to any server (default). */
  ANY("any"),
  /** Connect only to a primary/master server (read-write). */
  PRIMARY("primary"),
  /** Alias for PRIMARY (deprecated). */
  MASTER("master"),
  /** Connect only to a secondary/slave server (read-only). */
  SECONDARY("secondary"),
  /** Alias for SECONDARY (deprecated). */
  SLAVE("slave"),
  /** Prefer secondary, fall back to primary if unavailable. */
  PREFER_SECONDARY("preferSecondary"),
  /** Alias for PREFER_SECONDARY (deprecated). */
  PREFER_SLAVE("preferSlave");

  private final String value;

  PgTargetServerType(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
