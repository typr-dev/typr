package dev.typr.foundations.connect.postgres;

/** PostgreSQL read-only mode behavior. */
public enum PgReadOnlyMode {
  /** Ignore read-only setting (do not send SET TRANSACTION). */
  IGNORE("ignore"),
  /** Use SET TRANSACTION READ ONLY at transaction start (default). */
  TRANSACTION("transaction"),
  /** Use SET SESSION CHARACTERISTICS AS TRANSACTION READ ONLY on connect. */
  ALWAYS("always");

  private final String value;

  PgReadOnlyMode(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
