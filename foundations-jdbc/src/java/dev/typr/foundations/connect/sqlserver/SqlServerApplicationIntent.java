package dev.typr.foundations.connect.sqlserver;

/** SQL Server application intent for read-only routing in Always On clusters. */
public enum SqlServerApplicationIntent {
  /** Read-write operations (default). Routes to primary replica. */
  READ_WRITE("ReadWrite"),
  /** Read-only operations. May route to read-only secondary replica. */
  READ_ONLY("ReadOnly");

  private final String value;

  SqlServerApplicationIntent(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
