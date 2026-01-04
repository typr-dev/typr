package dev.typr.foundations.connect.postgres;

/** PostgreSQL autosave mode for savepoint handling. */
public enum PgAutosave {
  /** Do not use savepoints (default). Errors abort the entire transaction. */
  NEVER("never"),
  /** Create a savepoint before each statement. Rollback to savepoint on error. */
  ALWAYS("always"),
  /** Create savepoints only when PostgreSQL would fail the entire transaction. */
  CONSERVATIVE("conservative");

  private final String value;

  PgAutosave(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
