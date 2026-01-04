package dev.typr.foundations.connect.sqlserver;

/** SQL Server select method for result set handling. */
public enum SqlServerSelectMethod {
  /** Use direct processing (forward-only, read-only result sets). Default. */
  DIRECT("direct"),
  /** Use server-side cursors (required for scrollable/updatable result sets). */
  CURSOR("cursor");

  private final String value;

  SqlServerSelectMethod(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
