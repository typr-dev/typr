package dev.typr.foundations.connect.postgres;

/** PostgreSQL GSS encryption mode for Kerberos connections. */
public enum PgGssEncMode {
  /** Do not use GSS encryption. */
  DISABLE("disable"),
  /** Use GSS encryption if server supports it (default). */
  PREFER("prefer"),
  /** Require GSS encryption (fail if not available). */
  REQUIRE("require");

  private final String value;

  PgGssEncMode(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
