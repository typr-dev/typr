package dev.typr.foundations.connect.sqlserver;

/** SQL Server authentication mode. */
public enum SqlServerAuthentication {
  /** SQL Server authentication using username and password. */
  SQL_PASSWORD("SqlPassword"),
  /** Active Directory password authentication. */
  ACTIVE_DIRECTORY_PASSWORD("ActiveDirectoryPassword"),
  /** Active Directory integrated authentication (Windows). */
  ACTIVE_DIRECTORY_INTEGRATED("ActiveDirectoryIntegrated"),
  /** Active Directory interactive authentication (MFA). */
  ACTIVE_DIRECTORY_INTERACTIVE("ActiveDirectoryInteractive"),
  /** Active Directory service principal authentication. */
  ACTIVE_DIRECTORY_SERVICE_PRINCIPAL("ActiveDirectoryServicePrincipal"),
  /** Active Directory managed identity authentication. */
  ACTIVE_DIRECTORY_MANAGED_IDENTITY("ActiveDirectoryManagedIdentity"),
  /** Active Directory default authentication (uses DefaultAzureCredential). */
  ACTIVE_DIRECTORY_DEFAULT("ActiveDirectoryDefault"),
  /** Windows integrated authentication (NTLM/Kerberos). */
  NTLM("NTLM"),
  /** Use access token for authentication. */
  ACCESS_TOKEN("accessToken");

  private final String value;

  SqlServerAuthentication(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
