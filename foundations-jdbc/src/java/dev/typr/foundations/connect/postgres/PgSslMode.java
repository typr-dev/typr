package dev.typr.foundations.connect.postgres;

/** PostgreSQL SSL mode for connection security. */
public enum PgSslMode {
  /** Only try a non-SSL connection. */
  DISABLE("disable"),
  /** First try a non-SSL connection; if that fails, try an SSL connection. */
  ALLOW("allow"),
  /** First try an SSL connection; if that fails, try a non-SSL connection. (Default) */
  PREFER("prefer"),
  /** Only try an SSL connection. If a root CA file is present, verify the certificate. */
  REQUIRE("require"),
  /**
   * Only try an SSL connection, and verify that the server certificate is issued by a trusted CA.
   */
  VERIFY_CA("verify-ca"),
  /** Only try an SSL connection, verify CA and that the server hostname matches the certificate. */
  VERIFY_FULL("verify-full");

  private final String value;

  PgSslMode(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
