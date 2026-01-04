package dev.typr.foundations.connect.postgres;

/** PostgreSQL GSS library selection for Kerberos authentication. */
public enum PgGssLib {
  /** Auto-detect GSS library (default). */
  AUTO("auto"),
  /** Force native GSSAPI library. */
  GSSAPI("gssapi"),
  /** Force Windows SSPI library. */
  SSPI("sspi");

  private final String value;

  PgGssLib(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
