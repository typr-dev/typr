package dev.typr.foundations.connect.postgres;

/** PostgreSQL channel binding mode for SCRAM authentication. */
public enum PgChannelBinding {
  /** Do not use channel binding. */
  DISABLE("disable"),
  /** Use channel binding if server supports it (default). */
  PREFER("prefer"),
  /** Require channel binding (fail if not available). */
  REQUIRE("require");

  private final String value;

  PgChannelBinding(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
