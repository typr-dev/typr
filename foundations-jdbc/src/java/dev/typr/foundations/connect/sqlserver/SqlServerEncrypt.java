package dev.typr.foundations.connect.sqlserver;

/** SQL Server encryption mode for connections. */
public enum SqlServerEncrypt {
  /** Do not use encryption. */
  FALSE("false"),
  /** Use TDS 8.0 encryption (requires driver 10.x+, SQL Server 2022+). */
  STRICT("strict"),
  /** Use encryption with TDS 7.x protocol. */
  TRUE("true"),
  /** Use encryption only if server requires it (default for driver 9.x and earlier). */
  OPTIONAL("optional");

  private final String value;

  SqlServerEncrypt(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
