package dev.typr.foundations.connect.sqlserver;

import dev.typr.foundations.connect.DatabaseConfig;
import dev.typr.foundations.connect.DatabaseKind;
import java.util.HashMap;
import java.util.Map;

/**
 * SQL Server database configuration with typed builder methods for all documented JDBC driver
 * properties.
 *
 * <p>Properties are based on the Microsoft JDBC Driver for SQL Server documentation.
 *
 * @see <a
 *     href="https://learn.microsoft.com/en-us/sql/connect/jdbc/setting-the-connection-properties">Microsoft
 *     JDBC Documentation</a>
 */
public final class SqlServerConfig implements DatabaseConfig {

  private final String host;
  private final int port;
  private final String database;
  private final String username;
  private final String password;

  // Connection properties
  private final String instanceName;
  private final Boolean integratedSecurity;
  private final SqlServerAuthentication authentication;
  private final SqlServerAuthenticationScheme authenticationScheme;
  private final String accessToken;
  private final String realm;
  private final String serverSpn;

  // Encryption/SSL properties
  private final SqlServerEncrypt encrypt;
  private final Boolean trustServerCertificate;
  private final String hostNameInCertificate;
  private final String trustStore;
  private final String trustStorePassword;
  private final String trustStoreType;
  private final String sslProtocol;
  private final String keyStoreLocation;
  private final String keyStoreSecret;

  // Performance properties
  private final Boolean useBulkCopyForBatchInsert;
  private final Boolean sendStringParametersAsUnicode;
  private final SqlServerResponseBuffering responseBuffering;
  private final SqlServerSelectMethod selectMethod;
  private final Integer packetSize;
  private final Boolean enablePrepareOnFirstPreparedStatementCall;
  private final Integer serverPreparedStatementDiscardThreshold;
  private final Integer statementPoolingCacheSize;
  private final Boolean disableStatementPooling;
  private final Boolean useFmtOnly;
  private final Boolean delayLoadingLobs;
  private final Integer maxResultBuffer;
  private final Boolean sendTemporalDataTypesAsStringForBulkCopy;

  // Timeout properties
  private final Integer loginTimeout;
  private final Integer queryTimeout;
  private final Integer socketTimeout;
  private final Integer lockTimeout;
  private final Integer cancelQueryTimeout;

  // HA/Failover properties
  private final Boolean multiSubnetFailover;
  private final SqlServerApplicationIntent applicationIntent;
  private final String failoverPartner;
  private final Boolean transparentNetworkIPResolution;
  private final Integer connectRetryCount;
  private final Integer connectRetryInterval;

  // Always Encrypted properties
  private final SqlServerColumnEncryptionSetting columnEncryptionSetting;
  private final String keyStoreAuthentication;
  private final String keyStorePrincipalId;
  private final String enclaveAttestationUrl;
  private final String enclaveAttestationProtocol;
  private final Boolean alwaysEncryptedTraceEnabled;

  // Date/Time properties
  private final Boolean sendTimeAsDatetime;
  private final String datetimeParameterType;

  // Application properties
  private final String applicationName;
  private final String workstationID;

  // Logging/Debugging properties
  private final String traceDirectory;
  private final Boolean traceEnabled;
  private final String jaasConfigurationName;
  private final String clientCertificate;
  private final String clientKey;
  private final String clientKeyPassword;

  // Misc properties
  private final String lastUpdateCount;
  private final Boolean xopenStates;
  private final Boolean replication;
  private final String gsscredential;
  private final String serverNameAsACE;
  private final Boolean useDefaultGSSCredential;
  private final String msiClientId;
  private final String prepareMethod;

  // Escape hatch
  private final Map<String, String> extraProperties;

  private SqlServerConfig(Builder b) {
    this.host = b.host;
    this.port = b.port;
    this.database = b.database;
    this.username = b.username;
    this.password = b.password;

    // Connection
    this.instanceName = b.instanceName;
    this.integratedSecurity = b.integratedSecurity;
    this.authentication = b.authentication;
    this.authenticationScheme = b.authenticationScheme;
    this.accessToken = b.accessToken;
    this.realm = b.realm;
    this.serverSpn = b.serverSpn;

    // Encryption/SSL
    this.encrypt = b.encrypt;
    this.trustServerCertificate = b.trustServerCertificate;
    this.hostNameInCertificate = b.hostNameInCertificate;
    this.trustStore = b.trustStore;
    this.trustStorePassword = b.trustStorePassword;
    this.trustStoreType = b.trustStoreType;
    this.sslProtocol = b.sslProtocol;
    this.keyStoreLocation = b.keyStoreLocation;
    this.keyStoreSecret = b.keyStoreSecret;

    // Performance
    this.useBulkCopyForBatchInsert = b.useBulkCopyForBatchInsert;
    this.sendStringParametersAsUnicode = b.sendStringParametersAsUnicode;
    this.responseBuffering = b.responseBuffering;
    this.selectMethod = b.selectMethod;
    this.packetSize = b.packetSize;
    this.enablePrepareOnFirstPreparedStatementCall = b.enablePrepareOnFirstPreparedStatementCall;
    this.serverPreparedStatementDiscardThreshold = b.serverPreparedStatementDiscardThreshold;
    this.statementPoolingCacheSize = b.statementPoolingCacheSize;
    this.disableStatementPooling = b.disableStatementPooling;
    this.useFmtOnly = b.useFmtOnly;
    this.delayLoadingLobs = b.delayLoadingLobs;
    this.maxResultBuffer = b.maxResultBuffer;
    this.sendTemporalDataTypesAsStringForBulkCopy = b.sendTemporalDataTypesAsStringForBulkCopy;

    // Timeouts
    this.loginTimeout = b.loginTimeout;
    this.queryTimeout = b.queryTimeout;
    this.socketTimeout = b.socketTimeout;
    this.lockTimeout = b.lockTimeout;
    this.cancelQueryTimeout = b.cancelQueryTimeout;

    // HA/Failover
    this.multiSubnetFailover = b.multiSubnetFailover;
    this.applicationIntent = b.applicationIntent;
    this.failoverPartner = b.failoverPartner;
    this.transparentNetworkIPResolution = b.transparentNetworkIPResolution;
    this.connectRetryCount = b.connectRetryCount;
    this.connectRetryInterval = b.connectRetryInterval;

    // Always Encrypted
    this.columnEncryptionSetting = b.columnEncryptionSetting;
    this.keyStoreAuthentication = b.keyStoreAuthentication;
    this.keyStorePrincipalId = b.keyStorePrincipalId;
    this.enclaveAttestationUrl = b.enclaveAttestationUrl;
    this.enclaveAttestationProtocol = b.enclaveAttestationProtocol;
    this.alwaysEncryptedTraceEnabled = b.alwaysEncryptedTraceEnabled;

    // Date/Time
    this.sendTimeAsDatetime = b.sendTimeAsDatetime;
    this.datetimeParameterType = b.datetimeParameterType;

    // Application
    this.applicationName = b.applicationName;
    this.workstationID = b.workstationID;

    // Logging/Debugging
    this.traceDirectory = b.traceDirectory;
    this.traceEnabled = b.traceEnabled;
    this.jaasConfigurationName = b.jaasConfigurationName;
    this.clientCertificate = b.clientCertificate;
    this.clientKey = b.clientKey;
    this.clientKeyPassword = b.clientKeyPassword;

    // Misc
    this.lastUpdateCount = b.lastUpdateCount;
    this.xopenStates = b.xopenStates;
    this.replication = b.replication;
    this.gsscredential = b.gsscredential;
    this.serverNameAsACE = b.serverNameAsACE;
    this.useDefaultGSSCredential = b.useDefaultGSSCredential;
    this.msiClientId = b.msiClientId;
    this.prepareMethod = b.prepareMethod;

    this.extraProperties = Map.copyOf(b.extraProperties);
  }

  /**
   * Create a new builder with required connection parameters.
   *
   * @param host SQL Server hostname
   * @param port SQL Server port (typically 1433)
   * @param database database name
   * @param username username for authentication
   * @param password password for authentication
   * @return a new builder
   */
  public static Builder builder(
      String host, int port, String database, String username, String password) {
    return new Builder(host, port, database, username, password);
  }

  @Override
  public String jdbcUrl() {
    StringBuilder url = new StringBuilder("jdbc:sqlserver://");
    url.append(host).append(":").append(port);
    url.append(";databaseName=").append(database);
    if (instanceName != null) {
      url.append(";instanceName=").append(instanceName);
    }
    return url.toString();
  }

  @Override
  public String username() {
    return username;
  }

  @Override
  public String password() {
    return password;
  }

  @Override
  public DatabaseKind kind() {
    return DatabaseKind.SQLSERVER;
  }

  @Override
  public Map<String, String> driverProperties() {
    Map<String, String> props = new HashMap<>();

    // Connection
    if (integratedSecurity != null) props.put("integratedSecurity", integratedSecurity.toString());
    if (authentication != null) props.put("authentication", authentication.value());
    if (authenticationScheme != null)
      props.put("authenticationScheme", authenticationScheme.value());
    if (accessToken != null) props.put("accessToken", accessToken);
    if (realm != null) props.put("realm", realm);
    if (serverSpn != null) props.put("serverSpn", serverSpn);

    // Encryption/SSL
    if (encrypt != null) props.put("encrypt", encrypt.value());
    if (trustServerCertificate != null)
      props.put("trustServerCertificate", trustServerCertificate.toString());
    if (hostNameInCertificate != null) props.put("hostNameInCertificate", hostNameInCertificate);
    if (trustStore != null) props.put("trustStore", trustStore);
    if (trustStorePassword != null) props.put("trustStorePassword", trustStorePassword);
    if (trustStoreType != null) props.put("trustStoreType", trustStoreType);
    if (sslProtocol != null) props.put("sslProtocol", sslProtocol);
    if (keyStoreLocation != null) props.put("keyStoreLocation", keyStoreLocation);
    if (keyStoreSecret != null) props.put("keyStoreSecret", keyStoreSecret);

    // Performance
    if (useBulkCopyForBatchInsert != null)
      props.put("useBulkCopyForBatchInsert", useBulkCopyForBatchInsert.toString());
    if (sendStringParametersAsUnicode != null)
      props.put("sendStringParametersAsUnicode", sendStringParametersAsUnicode.toString());
    if (responseBuffering != null) props.put("responseBuffering", responseBuffering.value());
    if (selectMethod != null) props.put("selectMethod", selectMethod.value());
    if (packetSize != null) props.put("packetSize", packetSize.toString());
    if (enablePrepareOnFirstPreparedStatementCall != null)
      props.put(
          "enablePrepareOnFirstPreparedStatementCall",
          enablePrepareOnFirstPreparedStatementCall.toString());
    if (serverPreparedStatementDiscardThreshold != null)
      props.put(
          "serverPreparedStatementDiscardThreshold",
          serverPreparedStatementDiscardThreshold.toString());
    if (statementPoolingCacheSize != null)
      props.put("statementPoolingCacheSize", statementPoolingCacheSize.toString());
    if (disableStatementPooling != null)
      props.put("disableStatementPooling", disableStatementPooling.toString());
    if (useFmtOnly != null) props.put("useFmtOnly", useFmtOnly.toString());
    if (delayLoadingLobs != null) props.put("delayLoadingLobs", delayLoadingLobs.toString());
    if (maxResultBuffer != null) props.put("maxResultBuffer", maxResultBuffer.toString());
    if (sendTemporalDataTypesAsStringForBulkCopy != null)
      props.put(
          "sendTemporalDataTypesAsStringForBulkCopy",
          sendTemporalDataTypesAsStringForBulkCopy.toString());

    // Timeouts
    if (loginTimeout != null) props.put("loginTimeout", loginTimeout.toString());
    if (queryTimeout != null) props.put("queryTimeout", queryTimeout.toString());
    if (socketTimeout != null) props.put("socketTimeout", socketTimeout.toString());
    if (lockTimeout != null) props.put("lockTimeout", lockTimeout.toString());
    if (cancelQueryTimeout != null) props.put("cancelQueryTimeout", cancelQueryTimeout.toString());

    // HA/Failover
    if (multiSubnetFailover != null)
      props.put("multiSubnetFailover", multiSubnetFailover.toString());
    if (applicationIntent != null) props.put("applicationIntent", applicationIntent.value());
    if (failoverPartner != null) props.put("failoverPartner", failoverPartner);
    if (transparentNetworkIPResolution != null)
      props.put("transparentNetworkIPResolution", transparentNetworkIPResolution.toString());
    if (connectRetryCount != null) props.put("connectRetryCount", connectRetryCount.toString());
    if (connectRetryInterval != null)
      props.put("connectRetryInterval", connectRetryInterval.toString());

    // Always Encrypted
    if (columnEncryptionSetting != null)
      props.put("columnEncryptionSetting", columnEncryptionSetting.value());
    if (keyStoreAuthentication != null) props.put("keyStoreAuthentication", keyStoreAuthentication);
    if (keyStorePrincipalId != null) props.put("keyStorePrincipalId", keyStorePrincipalId);
    if (enclaveAttestationUrl != null) props.put("enclaveAttestationUrl", enclaveAttestationUrl);
    if (enclaveAttestationProtocol != null)
      props.put("enclaveAttestationProtocol", enclaveAttestationProtocol);
    if (alwaysEncryptedTraceEnabled != null)
      props.put("alwaysEncryptedTraceEnabled", alwaysEncryptedTraceEnabled.toString());

    // Date/Time
    if (sendTimeAsDatetime != null) props.put("sendTimeAsDatetime", sendTimeAsDatetime.toString());
    if (datetimeParameterType != null) props.put("datetimeParameterType", datetimeParameterType);

    // Application
    if (applicationName != null) props.put("applicationName", applicationName);
    if (workstationID != null) props.put("workstationID", workstationID);

    // Logging/Debugging
    if (traceDirectory != null) props.put("traceDirectory", traceDirectory);
    if (traceEnabled != null) props.put("traceEnabled", traceEnabled.toString());
    if (jaasConfigurationName != null) props.put("jaasConfigurationName", jaasConfigurationName);
    if (clientCertificate != null) props.put("clientCertificate", clientCertificate);
    if (clientKey != null) props.put("clientKey", clientKey);
    if (clientKeyPassword != null) props.put("clientKeyPassword", clientKeyPassword);

    // Misc
    if (lastUpdateCount != null) props.put("lastUpdateCount", lastUpdateCount);
    if (xopenStates != null) props.put("xopenStates", xopenStates.toString());
    if (replication != null) props.put("replication", replication.toString());
    if (gsscredential != null) props.put("gsscredential", gsscredential);
    if (serverNameAsACE != null) props.put("serverNameAsACE", serverNameAsACE);
    if (useDefaultGSSCredential != null)
      props.put("useDefaultGSSCredential", useDefaultGSSCredential.toString());
    if (msiClientId != null) props.put("msiClientId", msiClientId);
    if (prepareMethod != null) props.put("prepareMethod", prepareMethod);

    props.putAll(extraProperties);
    return props;
  }

  /** Builder for SqlServerConfig with typed methods for all JDBC driver properties. */
  public static final class Builder {
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;

    // Connection
    private String instanceName;
    private Boolean integratedSecurity;
    private SqlServerAuthentication authentication;
    private SqlServerAuthenticationScheme authenticationScheme;
    private String accessToken;
    private String realm;
    private String serverSpn;

    // Encryption/SSL
    private SqlServerEncrypt encrypt;
    private Boolean trustServerCertificate;
    private String hostNameInCertificate;
    private String trustStore;
    private String trustStorePassword;
    private String trustStoreType;
    private String sslProtocol;
    private String keyStoreLocation;
    private String keyStoreSecret;

    // Performance
    private Boolean useBulkCopyForBatchInsert;
    private Boolean sendStringParametersAsUnicode;
    private SqlServerResponseBuffering responseBuffering;
    private SqlServerSelectMethod selectMethod;
    private Integer packetSize;
    private Boolean enablePrepareOnFirstPreparedStatementCall;
    private Integer serverPreparedStatementDiscardThreshold;
    private Integer statementPoolingCacheSize;
    private Boolean disableStatementPooling;
    private Boolean useFmtOnly;
    private Boolean delayLoadingLobs;
    private Integer maxResultBuffer;
    private Boolean sendTemporalDataTypesAsStringForBulkCopy;

    // Timeouts
    private Integer loginTimeout;
    private Integer queryTimeout;
    private Integer socketTimeout;
    private Integer lockTimeout;
    private Integer cancelQueryTimeout;

    // HA/Failover
    private Boolean multiSubnetFailover;
    private SqlServerApplicationIntent applicationIntent;
    private String failoverPartner;
    private Boolean transparentNetworkIPResolution;
    private Integer connectRetryCount;
    private Integer connectRetryInterval;

    // Always Encrypted
    private SqlServerColumnEncryptionSetting columnEncryptionSetting;
    private String keyStoreAuthentication;
    private String keyStorePrincipalId;
    private String enclaveAttestationUrl;
    private String enclaveAttestationProtocol;
    private Boolean alwaysEncryptedTraceEnabled;

    // Date/Time
    private Boolean sendTimeAsDatetime;
    private String datetimeParameterType;

    // Application
    private String applicationName;
    private String workstationID;

    // Logging/Debugging
    private String traceDirectory;
    private Boolean traceEnabled;
    private String jaasConfigurationName;
    private String clientCertificate;
    private String clientKey;
    private String clientKeyPassword;

    // Misc
    private String lastUpdateCount;
    private Boolean xopenStates;
    private Boolean replication;
    private String gsscredential;
    private String serverNameAsACE;
    private Boolean useDefaultGSSCredential;
    private String msiClientId;
    private String prepareMethod;

    private final Map<String, String> extraProperties = new HashMap<>();

    private Builder(String host, int port, String database, String username, String password) {
      this.host = host;
      this.port = port;
      this.database = database;
      this.username = username;
      this.password = password;
    }

    // ==================== CONNECTION ====================

    /**
     * SQL Server named instance. Driver default: null (default instance).
     *
     * @param instanceName instance name
     * @return this builder
     */
    public Builder instanceName(String instanceName) {
      this.instanceName = instanceName;
      return this;
    }

    /**
     * Use Windows integrated authentication. Driver default: false.
     *
     * @param integratedSecurity true to use Windows auth
     * @return this builder
     */
    public Builder integratedSecurity(boolean integratedSecurity) {
      this.integratedSecurity = integratedSecurity;
      return this;
    }

    /**
     * Authentication mode. Driver default: null (use SQL authentication).
     *
     * @param authentication authentication mode
     * @return this builder
     */
    public Builder authentication(SqlServerAuthentication authentication) {
      this.authentication = authentication;
      return this;
    }

    /**
     * Authentication scheme for integrated security. Driver default: nativeAuthentication.
     *
     * @param authenticationScheme authentication scheme
     * @return this builder
     */
    public Builder authenticationScheme(SqlServerAuthenticationScheme authenticationScheme) {
      this.authenticationScheme = authenticationScheme;
      return this;
    }

    /**
     * Access token for Azure AD authentication. Driver default: null.
     *
     * @param accessToken Azure AD access token
     * @return this builder
     */
    public Builder accessToken(String accessToken) {
      this.accessToken = accessToken;
      return this;
    }

    /**
     * Kerberos realm for authentication. Driver default: null.
     *
     * @param realm Kerberos realm
     * @return this builder
     */
    public Builder realm(String realm) {
      this.realm = realm;
      return this;
    }

    /**
     * Server Service Principal Name for Kerberos. Driver default: null.
     *
     * @param serverSpn SPN in format MSSQLSvc/hostname:port
     * @return this builder
     */
    public Builder serverSpn(String serverSpn) {
      this.serverSpn = serverSpn;
      return this;
    }

    // ==================== ENCRYPTION/SSL ====================

    /**
     * Encryption mode. Driver default: true (as of driver 10.x).
     *
     * @param encrypt encryption mode
     * @return this builder
     */
    public Builder encrypt(SqlServerEncrypt encrypt) {
      this.encrypt = encrypt;
      return this;
    }

    /**
     * Trust the server certificate without validation. Driver default: false.
     *
     * @param trustServerCertificate true to trust all certificates
     * @return this builder
     */
    public Builder trustServerCertificate(boolean trustServerCertificate) {
      this.trustServerCertificate = trustServerCertificate;
      return this;
    }

    /**
     * Hostname to verify in server certificate. Driver default: null.
     *
     * @param hostNameInCertificate expected hostname
     * @return this builder
     */
    public Builder hostNameInCertificate(String hostNameInCertificate) {
      this.hostNameInCertificate = hostNameInCertificate;
      return this;
    }

    /**
     * Path to trust store file. Driver default: null.
     *
     * @param trustStore path to JKS/PKCS12 file
     * @return this builder
     */
    public Builder trustStore(String trustStore) {
      this.trustStore = trustStore;
      return this;
    }

    /**
     * Password for trust store. Driver default: null.
     *
     * @param trustStorePassword trust store password
     * @return this builder
     */
    public Builder trustStorePassword(String trustStorePassword) {
      this.trustStorePassword = trustStorePassword;
      return this;
    }

    /**
     * Trust store type. Driver default: JKS.
     *
     * @param trustStoreType trust store type (JKS, PKCS12)
     * @return this builder
     */
    public Builder trustStoreType(String trustStoreType) {
      this.trustStoreType = trustStoreType;
      return this;
    }

    /**
     * SSL/TLS protocol version. Driver default: TLS.
     *
     * @param sslProtocol protocol version (TLS, TLSv1.2, TLSv1.3)
     * @return this builder
     */
    public Builder sslProtocol(String sslProtocol) {
      this.sslProtocol = sslProtocol;
      return this;
    }

    /**
     * Path to client key store for mutual TLS. Driver default: null.
     *
     * @param keyStoreLocation path to key store file
     * @return this builder
     */
    public Builder keyStoreLocation(String keyStoreLocation) {
      this.keyStoreLocation = keyStoreLocation;
      return this;
    }

    /**
     * Password for client key store. Driver default: null.
     *
     * @param keyStoreSecret key store password
     * @return this builder
     */
    public Builder keyStoreSecret(String keyStoreSecret) {
      this.keyStoreSecret = keyStoreSecret;
      return this;
    }

    // ==================== PERFORMANCE ====================

    /**
     * Use bulk copy API for batch inserts. Driver default: false. OUR RECOMMENDATION: true
     * (significantly faster batch inserts).
     *
     * @param useBulkCopyForBatchInsert true to enable
     * @return this builder
     */
    public Builder useBulkCopyForBatchInsert(boolean useBulkCopyForBatchInsert) {
      this.useBulkCopyForBatchInsert = useBulkCopyForBatchInsert;
      return this;
    }

    /**
     * Send string parameters as Unicode. Driver default: true.
     *
     * @param sendStringParametersAsUnicode true for Unicode, false for ASCII
     * @return this builder
     */
    public Builder sendStringParametersAsUnicode(boolean sendStringParametersAsUnicode) {
      this.sendStringParametersAsUnicode = sendStringParametersAsUnicode;
      return this;
    }

    /**
     * Response buffering mode. Driver default: adaptive (since 2.0).
     *
     * @param responseBuffering buffering mode
     * @return this builder
     */
    public Builder responseBuffering(SqlServerResponseBuffering responseBuffering) {
      this.responseBuffering = responseBuffering;
      return this;
    }

    /**
     * Select method for result sets. Driver default: direct.
     *
     * @param selectMethod select method
     * @return this builder
     */
    public Builder selectMethod(SqlServerSelectMethod selectMethod) {
      this.selectMethod = selectMethod;
      return this;
    }

    /**
     * TDS packet size in bytes. Driver default: 8000.
     *
     * @param packetSize packet size (512-32767)
     * @return this builder
     */
    public Builder packetSize(int packetSize) {
      this.packetSize = packetSize;
      return this;
    }

    /**
     * Create server-prepared statement on first execute. Driver default: null.
     *
     * @param enablePrepareOnFirstPreparedStatementCall true to prepare on first call
     * @return this builder
     */
    public Builder enablePrepareOnFirstPreparedStatementCall(
        boolean enablePrepareOnFirstPreparedStatementCall) {
      this.enablePrepareOnFirstPreparedStatementCall = enablePrepareOnFirstPreparedStatementCall;
      return this;
    }

    /**
     * Threshold before unpreparing statements. Driver default: 10.
     *
     * @param serverPreparedStatementDiscardThreshold threshold count
     * @return this builder
     */
    public Builder serverPreparedStatementDiscardThreshold(
        int serverPreparedStatementDiscardThreshold) {
      this.serverPreparedStatementDiscardThreshold = serverPreparedStatementDiscardThreshold;
      return this;
    }

    /**
     * Statement pooling cache size. Driver default: 0 (disabled).
     *
     * @param statementPoolingCacheSize cache size
     * @return this builder
     */
    public Builder statementPoolingCacheSize(int statementPoolingCacheSize) {
      this.statementPoolingCacheSize = statementPoolingCacheSize;
      return this;
    }

    /**
     * Disable statement pooling. Driver default: true.
     *
     * @param disableStatementPooling true to disable
     * @return this builder
     */
    public Builder disableStatementPooling(boolean disableStatementPooling) {
      this.disableStatementPooling = disableStatementPooling;
      return this;
    }

    /**
     * Use SET FMTONLY for parameter metadata. Driver default: false.
     *
     * @param useFmtOnly true to use SET FMTONLY
     * @return this builder
     */
    public Builder useFmtOnly(boolean useFmtOnly) {
      this.useFmtOnly = useFmtOnly;
      return this;
    }

    /**
     * Delay loading LOBs until accessed. Driver default: true.
     *
     * @param delayLoadingLobs true to delay loading
     * @return this builder
     */
    public Builder delayLoadingLobs(boolean delayLoadingLobs) {
      this.delayLoadingLobs = delayLoadingLobs;
      return this;
    }

    /**
     * Maximum result buffer size in bytes. Driver default: -1 (unlimited).
     *
     * @param maxResultBuffer buffer size in bytes
     * @return this builder
     */
    public Builder maxResultBuffer(int maxResultBuffer) {
      this.maxResultBuffer = maxResultBuffer;
      return this;
    }

    /**
     * Send temporal types as strings in bulk copy. Driver default: true.
     *
     * @param sendTemporalDataTypesAsStringForBulkCopy true to send as strings
     * @return this builder
     */
    public Builder sendTemporalDataTypesAsStringForBulkCopy(
        boolean sendTemporalDataTypesAsStringForBulkCopy) {
      this.sendTemporalDataTypesAsStringForBulkCopy = sendTemporalDataTypesAsStringForBulkCopy;
      return this;
    }

    // ==================== TIMEOUTS ====================

    /**
     * Login timeout in seconds. Driver default: 15.
     *
     * @param loginTimeout timeout in seconds
     * @return this builder
     */
    public Builder loginTimeout(int loginTimeout) {
      this.loginTimeout = loginTimeout;
      return this;
    }

    /**
     * Query timeout in seconds. Driver default: -1 (use server default).
     *
     * @param queryTimeout timeout in seconds
     * @return this builder
     */
    public Builder queryTimeout(int queryTimeout) {
      this.queryTimeout = queryTimeout;
      return this;
    }

    /**
     * Socket timeout in milliseconds. Driver default: 0 (unlimited).
     *
     * @param socketTimeout timeout in milliseconds
     * @return this builder
     */
    public Builder socketTimeout(int socketTimeout) {
      this.socketTimeout = socketTimeout;
      return this;
    }

    /**
     * Lock timeout in milliseconds. Driver default: -1 (wait indefinitely).
     *
     * @param lockTimeout timeout in milliseconds
     * @return this builder
     */
    public Builder lockTimeout(int lockTimeout) {
      this.lockTimeout = lockTimeout;
      return this;
    }

    /**
     * Cancel query timeout in seconds. Driver default: -1 (disabled).
     *
     * @param cancelQueryTimeout timeout in seconds
     * @return this builder
     */
    public Builder cancelQueryTimeout(int cancelQueryTimeout) {
      this.cancelQueryTimeout = cancelQueryTimeout;
      return this;
    }

    // ==================== HA/FAILOVER ====================

    /**
     * Enable multi-subnet failover for Always On. Driver default: false.
     *
     * @param multiSubnetFailover true to enable
     * @return this builder
     */
    public Builder multiSubnetFailover(boolean multiSubnetFailover) {
      this.multiSubnetFailover = multiSubnetFailover;
      return this;
    }

    /**
     * Application intent for read-only routing. Driver default: ReadWrite.
     *
     * @param applicationIntent application intent
     * @return this builder
     */
    public Builder applicationIntent(SqlServerApplicationIntent applicationIntent) {
      this.applicationIntent = applicationIntent;
      return this;
    }

    /**
     * Failover partner server name. Driver default: null.
     *
     * @param failoverPartner partner server hostname
     * @return this builder
     */
    public Builder failoverPartner(String failoverPartner) {
      this.failoverPartner = failoverPartner;
      return this;
    }

    /**
     * Enable transparent network IP resolution. Driver default: true.
     *
     * @param transparentNetworkIPResolution true to enable
     * @return this builder
     */
    public Builder transparentNetworkIPResolution(boolean transparentNetworkIPResolution) {
      this.transparentNetworkIPResolution = transparentNetworkIPResolution;
      return this;
    }

    /**
     * Number of connection retry attempts. Driver default: 1.
     *
     * @param connectRetryCount retry count
     * @return this builder
     */
    public Builder connectRetryCount(int connectRetryCount) {
      this.connectRetryCount = connectRetryCount;
      return this;
    }

    /**
     * Interval between retry attempts in seconds. Driver default: 10.
     *
     * @param connectRetryInterval interval in seconds
     * @return this builder
     */
    public Builder connectRetryInterval(int connectRetryInterval) {
      this.connectRetryInterval = connectRetryInterval;
      return this;
    }

    // ==================== ALWAYS ENCRYPTED ====================

    /**
     * Always Encrypted column encryption setting. Driver default: Disabled.
     *
     * @param columnEncryptionSetting encryption setting
     * @return this builder
     */
    public Builder columnEncryptionSetting(
        SqlServerColumnEncryptionSetting columnEncryptionSetting) {
      this.columnEncryptionSetting = columnEncryptionSetting;
      return this;
    }

    /**
     * Key store authentication type. Driver default: null.
     *
     * @param keyStoreAuthentication authentication type (JavaKeyStorePassword,
     *     KeyVaultClientSecret, KeyVaultManagedIdentity)
     * @return this builder
     */
    public Builder keyStoreAuthentication(String keyStoreAuthentication) {
      this.keyStoreAuthentication = keyStoreAuthentication;
      return this;
    }

    /**
     * Key store principal ID (client ID for Azure). Driver default: null.
     *
     * @param keyStorePrincipalId principal/client ID
     * @return this builder
     */
    public Builder keyStorePrincipalId(String keyStorePrincipalId) {
      this.keyStorePrincipalId = keyStorePrincipalId;
      return this;
    }

    /**
     * Enclave attestation URL for secure enclaves. Driver default: null.
     *
     * @param enclaveAttestationUrl attestation service URL
     * @return this builder
     */
    public Builder enclaveAttestationUrl(String enclaveAttestationUrl) {
      this.enclaveAttestationUrl = enclaveAttestationUrl;
      return this;
    }

    /**
     * Enclave attestation protocol. Driver default: null.
     *
     * @param enclaveAttestationProtocol protocol (HGS, AAS, NONE)
     * @return this builder
     */
    public Builder enclaveAttestationProtocol(String enclaveAttestationProtocol) {
      this.enclaveAttestationProtocol = enclaveAttestationProtocol;
      return this;
    }

    /**
     * Enable Always Encrypted tracing. Driver default: false.
     *
     * @param alwaysEncryptedTraceEnabled true to enable tracing
     * @return this builder
     */
    public Builder alwaysEncryptedTraceEnabled(boolean alwaysEncryptedTraceEnabled) {
      this.alwaysEncryptedTraceEnabled = alwaysEncryptedTraceEnabled;
      return this;
    }

    // ==================== DATE/TIME ====================

    /**
     * Send java.sql.Time as datetime. Driver default: true.
     *
     * @param sendTimeAsDatetime true to send as datetime
     * @return this builder
     */
    public Builder sendTimeAsDatetime(boolean sendTimeAsDatetime) {
      this.sendTimeAsDatetime = sendTimeAsDatetime;
      return this;
    }

    /**
     * Type to use for datetime parameters. Driver default: null.
     *
     * @param datetimeParameterType parameter type (datetime, datetime2, datetimeoffset)
     * @return this builder
     */
    public Builder datetimeParameterType(String datetimeParameterType) {
      this.datetimeParameterType = datetimeParameterType;
      return this;
    }

    // ==================== APPLICATION ====================

    /**
     * Application name for monitoring. Driver default: Microsoft JDBC Driver for SQL Server.
     *
     * @param applicationName application name
     * @return this builder
     */
    public Builder applicationName(String applicationName) {
      this.applicationName = applicationName;
      return this;
    }

    /**
     * Workstation ID for monitoring. Driver default: local hostname.
     *
     * @param workstationID workstation identifier
     * @return this builder
     */
    public Builder workstationID(String workstationID) {
      this.workstationID = workstationID;
      return this;
    }

    // ==================== LOGGING/DEBUGGING ====================

    /**
     * Directory for trace logs. Driver default: null.
     *
     * @param traceDirectory path to directory
     * @return this builder
     */
    public Builder traceDirectory(String traceDirectory) {
      this.traceDirectory = traceDirectory;
      return this;
    }

    /**
     * Enable driver tracing. Driver default: false.
     *
     * @param traceEnabled true to enable
     * @return this builder
     */
    public Builder traceEnabled(boolean traceEnabled) {
      this.traceEnabled = traceEnabled;
      return this;
    }

    /**
     * JAAS configuration name for Kerberos. Driver default: null.
     *
     * @param jaasConfigurationName JAAS config name
     * @return this builder
     */
    public Builder jaasConfigurationName(String jaasConfigurationName) {
      this.jaasConfigurationName = jaasConfigurationName;
      return this;
    }

    /**
     * Path to client certificate for mutual TLS. Driver default: null.
     *
     * @param clientCertificate path to certificate file
     * @return this builder
     */
    public Builder clientCertificate(String clientCertificate) {
      this.clientCertificate = clientCertificate;
      return this;
    }

    /**
     * Path to client private key for mutual TLS. Driver default: null.
     *
     * @param clientKey path to key file
     * @return this builder
     */
    public Builder clientKey(String clientKey) {
      this.clientKey = clientKey;
      return this;
    }

    /**
     * Password for client private key. Driver default: null.
     *
     * @param clientKeyPassword key password
     * @return this builder
     */
    public Builder clientKeyPassword(String clientKeyPassword) {
      this.clientKeyPassword = clientKeyPassword;
      return this;
    }

    // ==================== MISC ====================

    /**
     * Return last update count. Driver default: true.
     *
     * @param lastUpdateCount "true" or "false"
     * @return this builder
     */
    public Builder lastUpdateCount(String lastUpdateCount) {
      this.lastUpdateCount = lastUpdateCount;
      return this;
    }

    /**
     * Use X/Open SQL states. Driver default: false.
     *
     * @param xopenStates true to use X/Open states
     * @return this builder
     */
    public Builder xopenStates(boolean xopenStates) {
      this.xopenStates = xopenStates;
      return this;
    }

    /**
     * Enable replication support. Driver default: false.
     *
     * @param replication true to enable
     * @return this builder
     */
    public Builder replication(boolean replication) {
      this.replication = replication;
      return this;
    }

    /**
     * GSS credential object class name. Driver default: null.
     *
     * @param gsscredential credential class name
     * @return this builder
     */
    public Builder gsscredential(String gsscredential) {
      this.gsscredential = gsscredential;
      return this;
    }

    /**
     * Server name as ACE (ASCII Compatible Encoding). Driver default: null.
     *
     * @param serverNameAsACE ACE hostname
     * @return this builder
     */
    public Builder serverNameAsACE(String serverNameAsACE) {
      this.serverNameAsACE = serverNameAsACE;
      return this;
    }

    /**
     * Use default GSS credential. Driver default: false.
     *
     * @param useDefaultGSSCredential true to use default
     * @return this builder
     */
    public Builder useDefaultGSSCredential(boolean useDefaultGSSCredential) {
      this.useDefaultGSSCredential = useDefaultGSSCredential;
      return this;
    }

    /**
     * Managed Identity client ID for Azure. Driver default: null.
     *
     * @param msiClientId MSI client ID
     * @return this builder
     */
    public Builder msiClientId(String msiClientId) {
      this.msiClientId = msiClientId;
      return this;
    }

    /**
     * Prepare method for statements. Driver default: null.
     *
     * @param prepareMethod prepare method (prepexec, prepare)
     * @return this builder
     */
    public Builder prepareMethod(String prepareMethod) {
      this.prepareMethod = prepareMethod;
      return this;
    }

    /**
     * Set an arbitrary driver property.
     *
     * @param key property name
     * @param value property value
     * @return this builder
     */
    public Builder property(String key, String value) {
      this.extraProperties.put(key, value);
      return this;
    }

    /**
     * Build the SqlServerConfig.
     *
     * @return immutable SqlServerConfig
     */
    public SqlServerConfig build() {
      return new SqlServerConfig(this);
    }
  }
}
