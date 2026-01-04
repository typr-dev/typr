package dev.typr.foundations.connect.postgres;

/** PostgreSQL escape syntax call mode for JDBC escape function calls. */
public enum PgEscapeSyntaxCallMode {
  /** Use SELECT for all escape calls (default). */
  SELECT("select"),
  /** Use CALL only when function returns void. */
  CALL_IF_NO_RETURN("callIfNoReturn"),
  /** Always use CALL syntax. */
  CALL("call");

  private final String value;

  PgEscapeSyntaxCallMode(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
