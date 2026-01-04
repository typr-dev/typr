package dev.typr.foundations.connect.sqlserver;

/** SQL Server Always Encrypted column encryption setting. */
public enum SqlServerColumnEncryptionSetting {
  /** Disable Always Encrypted (default). */
  DISABLED("Disabled"),
  /** Enable Always Encrypted. */
  ENABLED("Enabled");

  private final String value;

  SqlServerColumnEncryptionSetting(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
