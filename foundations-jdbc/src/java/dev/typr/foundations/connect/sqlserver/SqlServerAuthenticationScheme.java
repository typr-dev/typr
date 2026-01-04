package dev.typr.foundations.connect.sqlserver;

/** SQL Server authentication scheme for integrated authentication. */
public enum SqlServerAuthenticationScheme {
  /** Use native SQL Server authentication (default). */
  NATIVE_AUTHENTICATION("nativeAuthentication"),
  /** Use NTLM authentication. */
  NTLM("NTLM"),
  /** Use Kerberos authentication (requires proper SPN configuration). */
  KERBEROS("Kerberos"),
  /** Use Java's GSS-API for Kerberos (cross-platform). */
  JAVA_KERBEROS("JavaKerberos");

  private final String value;

  SqlServerAuthenticationScheme(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
