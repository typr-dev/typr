package dev.typr.foundations.connect.mariadb;

/** MariaDB SSL mode for connection security. */
public enum MariaSslMode {
  /** Do not use SSL. */
  DISABLE("disable"),
  /** Use SSL if server supports it, but don't fail if not. */
  TRUST("trust"),
  /** Verify server certificate. */
  VERIFY_CA("verify-ca"),
  /** Verify server certificate and hostname. */
  VERIFY_FULL("verify-full");

  private final String value;

  MariaSslMode(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
