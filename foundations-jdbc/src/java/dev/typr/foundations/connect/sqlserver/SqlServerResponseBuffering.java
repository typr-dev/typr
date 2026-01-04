package dev.typr.foundations.connect.sqlserver;

/** SQL Server response buffering mode. */
public enum SqlServerResponseBuffering {
  /** Buffer the entire response in memory (default for drivers before 2.0). */
  FULL("full"),
  /** Adaptively buffer based on response size (default for 2.0+). */
  ADAPTIVE("adaptive");

  private final String value;

  SqlServerResponseBuffering(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
