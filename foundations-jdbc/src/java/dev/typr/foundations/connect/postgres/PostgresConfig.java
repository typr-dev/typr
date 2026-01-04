package dev.typr.foundations.connect.postgres;

import dev.typr.foundations.connect.DatabaseConfig;
import dev.typr.foundations.connect.DatabaseKind;
import java.util.HashMap;
import java.util.Map;

/**
 * PostgreSQL database configuration with typed builder methods for all documented JDBC driver
 * properties.
 *
 * <p>Properties are based on the PostgreSQL JDBC driver documentation. Default values are the
 * driver defaults unless noted otherwise with "OUR DEFAULT" in the builder method documentation.
 *
 * @see <a href="https://jdbc.postgresql.org/documentation/use/">PostgreSQL JDBC Documentation</a>
 */
public final class PostgresConfig implements DatabaseConfig {

  private final String host;
  private final int port;
  private final String database;
  private final String username;
  private final String password;

  // SSL/TLS properties
  private final Boolean ssl;
  private final String sslfactory;
  private final PgSslMode sslmode;
  private final PgSslNegotiation sslNegotiation;
  private final String sslcert;
  private final String sslkey;
  private final String sslrootcert;
  private final String sslhostnameverifier;
  private final String sslpasswordcallback;
  private final String sslpassword;
  private final Integer sslResponseTimeout;

  // Performance properties
  private final Boolean reWriteBatchedInserts;
  private final Boolean binaryTransfer;
  private final String binaryTransferEnable;
  private final String binaryTransferDisable;
  private final Integer prepareThreshold;
  private final Integer preparedStatementCacheQueries;
  private final Integer preparedStatementCacheSizeMiB;
  private final PgQueryMode preferQueryMode;
  private final Integer defaultRowFetchSize;
  private final Integer databaseMetadataCacheFields;
  private final Integer databaseMetadataCacheFieldsMiB;
  private final Boolean adaptiveFetch;
  private final Integer adaptiveFetchMinimum;
  private final Integer adaptiveFetchMaximum;

  // Timeout properties
  private final Integer loginTimeout;
  private final Integer connectTimeout;
  private final Integer socketTimeout;
  private final Integer cancelSignalTimeout;

  // Network properties
  private final Boolean tcpKeepAlive;
  private final Boolean tcpNoDelay;
  private final Integer sendBufferSize;
  private final Integer receiveBufferSize;
  private final Integer maxSendBufferSize;

  // Kerberos/GSSAPI properties
  private final PgGssLib gsslib;
  private final String kerberosServerName;
  private final String jaasApplicationName;
  private final Boolean jaasLogin;
  private final Boolean gssUseDefaultCreds;
  private final PgGssEncMode gssEncMode;
  private final Integer gssResponseTimeout;
  private final String sspiServiceClass;
  private final Boolean useSpnego;
  private final PgChannelBinding channelBinding;

  // Behavior properties
  private final Boolean allowEncodingChanges;
  private final Boolean logUnclosedConnections;
  private final PgAutosave autosave;
  private final Boolean cleanupSavepoints;
  private final String stringtype;
  private final String applicationName;
  private final String currentSchema;
  private final Boolean readOnly;
  private final PgReadOnlyMode readOnlyMode;
  private final Boolean disableColumnSanitiser;
  private final String assumeMinServerVersion;
  private final Integer unknownLength;
  private final Boolean logServerErrorDetail;
  private final Boolean quoteReturningIdentifiers;
  private final Boolean hideUnprivilegedExceptions;
  private final String options;

  // Replication properties
  private final PgReplication replication;
  private final PgTargetServerType targetServerType;
  private final Integer hostRecheckSeconds;
  private final Boolean loadBalanceHosts;

  // Socket factory properties
  private final String socketFactory;
  private final String socketFactoryArg;

  // Result handling properties
  private final String maxResultBuffer;
  private final PgEscapeSyntaxCallMode escapeSyntaxCallMode;

  // Auth plugin property
  private final String authenticationPluginClassName;

  // Escape hatch for undocumented/future properties
  private final Map<String, String> extraProperties;

  private PostgresConfig(Builder b) {
    this.host = b.host;
    this.port = b.port;
    this.database = b.database;
    this.username = b.username;
    this.password = b.password;

    // SSL/TLS
    this.ssl = b.ssl;
    this.sslfactory = b.sslfactory;
    this.sslmode = b.sslmode;
    this.sslNegotiation = b.sslNegotiation;
    this.sslcert = b.sslcert;
    this.sslkey = b.sslkey;
    this.sslrootcert = b.sslrootcert;
    this.sslhostnameverifier = b.sslhostnameverifier;
    this.sslpasswordcallback = b.sslpasswordcallback;
    this.sslpassword = b.sslpassword;
    this.sslResponseTimeout = b.sslResponseTimeout;

    // Performance
    this.reWriteBatchedInserts = b.reWriteBatchedInserts;
    this.binaryTransfer = b.binaryTransfer;
    this.binaryTransferEnable = b.binaryTransferEnable;
    this.binaryTransferDisable = b.binaryTransferDisable;
    this.prepareThreshold = b.prepareThreshold;
    this.preparedStatementCacheQueries = b.preparedStatementCacheQueries;
    this.preparedStatementCacheSizeMiB = b.preparedStatementCacheSizeMiB;
    this.preferQueryMode = b.preferQueryMode;
    this.defaultRowFetchSize = b.defaultRowFetchSize;
    this.databaseMetadataCacheFields = b.databaseMetadataCacheFields;
    this.databaseMetadataCacheFieldsMiB = b.databaseMetadataCacheFieldsMiB;
    this.adaptiveFetch = b.adaptiveFetch;
    this.adaptiveFetchMinimum = b.adaptiveFetchMinimum;
    this.adaptiveFetchMaximum = b.adaptiveFetchMaximum;

    // Timeouts
    this.loginTimeout = b.loginTimeout;
    this.connectTimeout = b.connectTimeout;
    this.socketTimeout = b.socketTimeout;
    this.cancelSignalTimeout = b.cancelSignalTimeout;

    // Network
    this.tcpKeepAlive = b.tcpKeepAlive;
    this.tcpNoDelay = b.tcpNoDelay;
    this.sendBufferSize = b.sendBufferSize;
    this.receiveBufferSize = b.receiveBufferSize;
    this.maxSendBufferSize = b.maxSendBufferSize;

    // Kerberos/GSSAPI
    this.gsslib = b.gsslib;
    this.kerberosServerName = b.kerberosServerName;
    this.jaasApplicationName = b.jaasApplicationName;
    this.jaasLogin = b.jaasLogin;
    this.gssUseDefaultCreds = b.gssUseDefaultCreds;
    this.gssEncMode = b.gssEncMode;
    this.gssResponseTimeout = b.gssResponseTimeout;
    this.sspiServiceClass = b.sspiServiceClass;
    this.useSpnego = b.useSpnego;
    this.channelBinding = b.channelBinding;

    // Behavior
    this.allowEncodingChanges = b.allowEncodingChanges;
    this.logUnclosedConnections = b.logUnclosedConnections;
    this.autosave = b.autosave;
    this.cleanupSavepoints = b.cleanupSavepoints;
    this.stringtype = b.stringtype;
    this.applicationName = b.applicationName;
    this.currentSchema = b.currentSchema;
    this.readOnly = b.readOnly;
    this.readOnlyMode = b.readOnlyMode;
    this.disableColumnSanitiser = b.disableColumnSanitiser;
    this.assumeMinServerVersion = b.assumeMinServerVersion;
    this.unknownLength = b.unknownLength;
    this.logServerErrorDetail = b.logServerErrorDetail;
    this.quoteReturningIdentifiers = b.quoteReturningIdentifiers;
    this.hideUnprivilegedExceptions = b.hideUnprivilegedExceptions;
    this.options = b.options;

    // Replication
    this.replication = b.replication;
    this.targetServerType = b.targetServerType;
    this.hostRecheckSeconds = b.hostRecheckSeconds;
    this.loadBalanceHosts = b.loadBalanceHosts;

    // Socket factory
    this.socketFactory = b.socketFactory;
    this.socketFactoryArg = b.socketFactoryArg;

    // Result handling
    this.maxResultBuffer = b.maxResultBuffer;
    this.escapeSyntaxCallMode = b.escapeSyntaxCallMode;

    // Auth plugin
    this.authenticationPluginClassName = b.authenticationPluginClassName;

    // Extra properties
    this.extraProperties = Map.copyOf(b.extraProperties);
  }

  /**
   * Create a new builder with required connection parameters.
   *
   * @param host database server hostname
   * @param port database server port (typically 5432)
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
    return "jdbc:postgresql://" + host + ":" + port + "/" + database;
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
    return DatabaseKind.POSTGRESQL;
  }

  @Override
  public Map<String, String> driverProperties() {
    Map<String, String> props = new HashMap<>();

    // SSL/TLS
    if (ssl != null) props.put("ssl", ssl.toString());
    if (sslfactory != null) props.put("sslfactory", sslfactory);
    if (sslmode != null) props.put("sslmode", sslmode.value());
    if (sslNegotiation != null) props.put("sslNegotiation", sslNegotiation.value());
    if (sslcert != null) props.put("sslcert", sslcert);
    if (sslkey != null) props.put("sslkey", sslkey);
    if (sslrootcert != null) props.put("sslrootcert", sslrootcert);
    if (sslhostnameverifier != null) props.put("sslhostnameverifier", sslhostnameverifier);
    if (sslpasswordcallback != null) props.put("sslpasswordcallback", sslpasswordcallback);
    if (sslpassword != null) props.put("sslpassword", sslpassword);
    if (sslResponseTimeout != null) props.put("sslResponseTimeout", sslResponseTimeout.toString());

    // Performance
    if (reWriteBatchedInserts != null)
      props.put("reWriteBatchedInserts", reWriteBatchedInserts.toString());
    if (binaryTransfer != null) props.put("binaryTransfer", binaryTransfer.toString());
    if (binaryTransferEnable != null) props.put("binaryTransferEnable", binaryTransferEnable);
    if (binaryTransferDisable != null) props.put("binaryTransferDisable", binaryTransferDisable);
    if (prepareThreshold != null) props.put("prepareThreshold", prepareThreshold.toString());
    if (preparedStatementCacheQueries != null)
      props.put("preparedStatementCacheQueries", preparedStatementCacheQueries.toString());
    if (preparedStatementCacheSizeMiB != null)
      props.put("preparedStatementCacheSizeMiB", preparedStatementCacheSizeMiB.toString());
    if (preferQueryMode != null) props.put("preferQueryMode", preferQueryMode.value());
    if (defaultRowFetchSize != null)
      props.put("defaultRowFetchSize", defaultRowFetchSize.toString());
    if (databaseMetadataCacheFields != null)
      props.put("databaseMetadataCacheFields", databaseMetadataCacheFields.toString());
    if (databaseMetadataCacheFieldsMiB != null)
      props.put("databaseMetadataCacheFieldsMiB", databaseMetadataCacheFieldsMiB.toString());
    if (adaptiveFetch != null) props.put("adaptiveFetch", adaptiveFetch.toString());
    if (adaptiveFetchMinimum != null)
      props.put("adaptiveFetchMinimum", adaptiveFetchMinimum.toString());
    if (adaptiveFetchMaximum != null)
      props.put("adaptiveFetchMaximum", adaptiveFetchMaximum.toString());

    // Timeouts
    if (loginTimeout != null) props.put("loginTimeout", loginTimeout.toString());
    if (connectTimeout != null) props.put("connectTimeout", connectTimeout.toString());
    if (socketTimeout != null) props.put("socketTimeout", socketTimeout.toString());
    if (cancelSignalTimeout != null)
      props.put("cancelSignalTimeout", cancelSignalTimeout.toString());

    // Network
    if (tcpKeepAlive != null) props.put("tcpKeepAlive", tcpKeepAlive.toString());
    if (tcpNoDelay != null) props.put("tcpNoDelay", tcpNoDelay.toString());
    if (sendBufferSize != null) props.put("sendBufferSize", sendBufferSize.toString());
    if (receiveBufferSize != null) props.put("receiveBufferSize", receiveBufferSize.toString());
    if (maxSendBufferSize != null) props.put("maxSendBufferSize", maxSendBufferSize.toString());

    // Kerberos/GSSAPI
    if (gsslib != null) props.put("gsslib", gsslib.value());
    if (kerberosServerName != null) props.put("kerberosServerName", kerberosServerName);
    if (jaasApplicationName != null) props.put("jaasApplicationName", jaasApplicationName);
    if (jaasLogin != null) props.put("jaasLogin", jaasLogin.toString());
    if (gssUseDefaultCreds != null) props.put("gssUseDefaultCreds", gssUseDefaultCreds.toString());
    if (gssEncMode != null) props.put("gssEncMode", gssEncMode.value());
    if (gssResponseTimeout != null) props.put("gssResponseTimeout", gssResponseTimeout.toString());
    if (sspiServiceClass != null) props.put("sspiServiceClass", sspiServiceClass);
    if (useSpnego != null) props.put("useSpnego", useSpnego.toString());
    if (channelBinding != null) props.put("channelBinding", channelBinding.value());

    // Behavior
    if (allowEncodingChanges != null)
      props.put("allowEncodingChanges", allowEncodingChanges.toString());
    if (logUnclosedConnections != null)
      props.put("logUnclosedConnections", logUnclosedConnections.toString());
    if (autosave != null) props.put("autosave", autosave.value());
    if (cleanupSavepoints != null) props.put("cleanupSavepoints", cleanupSavepoints.toString());
    if (stringtype != null) props.put("stringtype", stringtype);
    if (applicationName != null) props.put("ApplicationName", applicationName);
    if (currentSchema != null) props.put("currentSchema", currentSchema);
    if (readOnly != null) props.put("readOnly", readOnly.toString());
    if (readOnlyMode != null) props.put("readOnlyMode", readOnlyMode.value());
    if (disableColumnSanitiser != null)
      props.put("disableColumnSanitiser", disableColumnSanitiser.toString());
    if (assumeMinServerVersion != null) props.put("assumeMinServerVersion", assumeMinServerVersion);
    if (unknownLength != null) props.put("unknownLength", unknownLength.toString());
    if (logServerErrorDetail != null)
      props.put("logServerErrorDetail", logServerErrorDetail.toString());
    if (quoteReturningIdentifiers != null)
      props.put("quoteReturningIdentifiers", quoteReturningIdentifiers.toString());
    if (hideUnprivilegedExceptions != null)
      props.put("hideUnprivilegedExceptions", hideUnprivilegedExceptions.toString());
    if (options != null) props.put("options", options);

    // Replication
    if (replication != null) props.put("replication", replication.value());
    if (targetServerType != null) props.put("targetServerType", targetServerType.value());
    if (hostRecheckSeconds != null) props.put("hostRecheckSeconds", hostRecheckSeconds.toString());
    if (loadBalanceHosts != null) props.put("loadBalanceHosts", loadBalanceHosts.toString());

    // Socket factory
    if (socketFactory != null) props.put("socketFactory", socketFactory);
    if (socketFactoryArg != null) props.put("socketFactoryArg", socketFactoryArg);

    // Result handling
    if (maxResultBuffer != null) props.put("maxResultBuffer", maxResultBuffer);
    if (escapeSyntaxCallMode != null)
      props.put("escapeSyntaxCallMode", escapeSyntaxCallMode.value());

    // Auth plugin
    if (authenticationPluginClassName != null)
      props.put("authenticationPluginClassName", authenticationPluginClassName);

    // Extra properties
    props.putAll(extraProperties);

    return props;
  }

  /** Builder for PostgresConfig with typed methods for all JDBC driver properties. */
  public static final class Builder {
    // Required
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;

    // SSL/TLS
    private Boolean ssl;
    private String sslfactory;
    private PgSslMode sslmode;
    private PgSslNegotiation sslNegotiation;
    private String sslcert;
    private String sslkey;
    private String sslrootcert;
    private String sslhostnameverifier;
    private String sslpasswordcallback;
    private String sslpassword;
    private Integer sslResponseTimeout;

    // Performance
    private Boolean reWriteBatchedInserts;
    private Boolean binaryTransfer;
    private String binaryTransferEnable;
    private String binaryTransferDisable;
    private Integer prepareThreshold;
    private Integer preparedStatementCacheQueries;
    private Integer preparedStatementCacheSizeMiB;
    private PgQueryMode preferQueryMode;
    private Integer defaultRowFetchSize;
    private Integer databaseMetadataCacheFields;
    private Integer databaseMetadataCacheFieldsMiB;
    private Boolean adaptiveFetch;
    private Integer adaptiveFetchMinimum;
    private Integer adaptiveFetchMaximum;

    // Timeouts
    private Integer loginTimeout;
    private Integer connectTimeout;
    private Integer socketTimeout;
    private Integer cancelSignalTimeout;

    // Network
    private Boolean tcpKeepAlive;
    private Boolean tcpNoDelay;
    private Integer sendBufferSize;
    private Integer receiveBufferSize;
    private Integer maxSendBufferSize;

    // Kerberos/GSSAPI
    private PgGssLib gsslib;
    private String kerberosServerName;
    private String jaasApplicationName;
    private Boolean jaasLogin;
    private Boolean gssUseDefaultCreds;
    private PgGssEncMode gssEncMode;
    private Integer gssResponseTimeout;
    private String sspiServiceClass;
    private Boolean useSpnego;
    private PgChannelBinding channelBinding;

    // Behavior
    private Boolean allowEncodingChanges;
    private Boolean logUnclosedConnections;
    private PgAutosave autosave;
    private Boolean cleanupSavepoints;
    private String stringtype;
    private String applicationName;
    private String currentSchema;
    private Boolean readOnly;
    private PgReadOnlyMode readOnlyMode;
    private Boolean disableColumnSanitiser;
    private String assumeMinServerVersion;
    private Integer unknownLength;
    private Boolean logServerErrorDetail;
    private Boolean quoteReturningIdentifiers;
    private Boolean hideUnprivilegedExceptions;
    private String options;

    // Replication
    private PgReplication replication;
    private PgTargetServerType targetServerType;
    private Integer hostRecheckSeconds;
    private Boolean loadBalanceHosts;

    // Socket factory
    private String socketFactory;
    private String socketFactoryArg;

    // Result handling
    private String maxResultBuffer;
    private PgEscapeSyntaxCallMode escapeSyntaxCallMode;

    // Auth plugin
    private String authenticationPluginClassName;

    // Escape hatch
    private final Map<String, String> extraProperties = new HashMap<>();

    private Builder(String host, int port, String database, String username, String password) {
      this.host = host;
      this.port = port;
      this.database = database;
      this.username = username;
      this.password = password;

      // OUR DEFAULTS (better than driver defaults)
      this.reWriteBatchedInserts =
          true; // Driver default is false, but true is almost always better
    }

    // ==================== SSL/TLS ====================

    /**
     * Enable SSL connection. Driver default: false.
     *
     * @param ssl true to enable SSL
     * @return this builder
     */
    public Builder ssl(boolean ssl) {
      this.ssl = ssl;
      return this;
    }

    /**
     * SSL socket factory class name. Driver default: org.postgresql.ssl.LibPQFactory
     *
     * @param sslfactory fully qualified class name
     * @return this builder
     */
    public Builder sslfactory(String sslfactory) {
      this.sslfactory = sslfactory;
      return this;
    }

    /**
     * SSL mode for connection security. Driver default: prefer.
     *
     * @param sslmode SSL mode
     * @return this builder
     */
    public Builder sslmode(PgSslMode sslmode) {
      this.sslmode = sslmode;
      return this;
    }

    /**
     * SSL negotiation mode. Driver default: postgres.
     *
     * @param sslNegotiation SSL negotiation mode
     * @return this builder
     */
    public Builder sslNegotiation(PgSslNegotiation sslNegotiation) {
      this.sslNegotiation = sslNegotiation;
      return this;
    }

    /**
     * Path to client SSL certificate. Driver default: ~/.postgresql/postgresql.crt
     *
     * @param sslcert path to certificate file
     * @return this builder
     */
    public Builder sslcert(String sslcert) {
      this.sslcert = sslcert;
      return this;
    }

    /**
     * Path to client SSL private key. Driver default: ~/.postgresql/postgresql.pk8
     *
     * @param sslkey path to key file (PKCS#8 format)
     * @return this builder
     */
    public Builder sslkey(String sslkey) {
      this.sslkey = sslkey;
      return this;
    }

    /**
     * Path to root CA certificate. Driver default: ~/.postgresql/root.crt
     *
     * @param sslrootcert path to root CA file
     * @return this builder
     */
    public Builder sslrootcert(String sslrootcert) {
      this.sslrootcert = sslrootcert;
      return this;
    }

    /**
     * Hostname verifier class for SSL. Driver default: null (use default verifier).
     *
     * @param sslhostnameverifier fully qualified class name
     * @return this builder
     */
    public Builder sslhostnameverifier(String sslhostnameverifier) {
      this.sslhostnameverifier = sslhostnameverifier;
      return this;
    }

    /**
     * SSL password callback class. Driver default: null.
     *
     * @param sslpasswordcallback fully qualified class name implementing
     *     javax.security.auth.callback.CallbackHandler
     * @return this builder
     */
    public Builder sslpasswordcallback(String sslpasswordcallback) {
      this.sslpasswordcallback = sslpasswordcallback;
      return this;
    }

    /**
     * Password for encrypted SSL private key. Driver default: null.
     *
     * @param sslpassword password for the key file
     * @return this builder
     */
    public Builder sslpassword(String sslpassword) {
      this.sslpassword = sslpassword;
      return this;
    }

    /**
     * Timeout for SSL negotiation response in milliseconds. Driver default: 5000.
     *
     * @param sslResponseTimeout timeout in milliseconds
     * @return this builder
     */
    public Builder sslResponseTimeout(int sslResponseTimeout) {
      this.sslResponseTimeout = sslResponseTimeout;
      return this;
    }

    // ==================== PERFORMANCE ====================

    /**
     * Rewrite INSERT statements for batch optimization. Driver default: false. OUR DEFAULT: true
     * (significantly improves batch insert performance).
     *
     * @param reWriteBatchedInserts true to enable
     * @return this builder
     */
    public Builder reWriteBatchedInserts(boolean reWriteBatchedInserts) {
      this.reWriteBatchedInserts = reWriteBatchedInserts;
      return this;
    }

    /**
     * Enable binary transfer for supported types. Driver default: true.
     *
     * @param binaryTransfer true to enable
     * @return this builder
     */
    public Builder binaryTransfer(boolean binaryTransfer) {
      this.binaryTransfer = binaryTransfer;
      return this;
    }

    /**
     * Comma-separated list of OIDs to enable binary transfer for. Driver default: empty.
     *
     * @param binaryTransferEnable comma-separated OIDs
     * @return this builder
     */
    public Builder binaryTransferEnable(String binaryTransferEnable) {
      this.binaryTransferEnable = binaryTransferEnable;
      return this;
    }

    /**
     * Comma-separated list of OIDs to disable binary transfer for. Driver default: empty.
     *
     * @param binaryTransferDisable comma-separated OIDs
     * @return this builder
     */
    public Builder binaryTransferDisable(String binaryTransferDisable) {
      this.binaryTransferDisable = binaryTransferDisable;
      return this;
    }

    /**
     * Number of executions before using server-side prepared statement. Driver default: 5. Use 0 to
     * disable, -1 to always use prepared statements.
     *
     * @param prepareThreshold threshold count
     * @return this builder
     */
    public Builder prepareThreshold(int prepareThreshold) {
      this.prepareThreshold = prepareThreshold;
      return this;
    }

    /**
     * Maximum number of prepared statements cached per connection. Driver default: 256.
     *
     * @param preparedStatementCacheQueries cache size
     * @return this builder
     */
    public Builder preparedStatementCacheQueries(int preparedStatementCacheQueries) {
      this.preparedStatementCacheQueries = preparedStatementCacheQueries;
      return this;
    }

    /**
     * Maximum size of prepared statement cache in MiB. Driver default: 5.
     *
     * @param preparedStatementCacheSizeMiB cache size in MiB
     * @return this builder
     */
    public Builder preparedStatementCacheSizeMiB(int preparedStatementCacheSizeMiB) {
      this.preparedStatementCacheSizeMiB = preparedStatementCacheSizeMiB;
      return this;
    }

    /**
     * Query execution mode. Driver default: extended.
     *
     * @param preferQueryMode query mode
     * @return this builder
     */
    public Builder preferQueryMode(PgQueryMode preferQueryMode) {
      this.preferQueryMode = preferQueryMode;
      return this;
    }

    /**
     * Default fetch size for statements. Driver default: 0 (fetch all rows).
     *
     * @param defaultRowFetchSize fetch size (0 = fetch all)
     * @return this builder
     */
    public Builder defaultRowFetchSize(int defaultRowFetchSize) {
      this.defaultRowFetchSize = defaultRowFetchSize;
      return this;
    }

    /**
     * Maximum number of fields to cache in DatabaseMetaData. Driver default: 65536.
     *
     * @param databaseMetadataCacheFields cache size
     * @return this builder
     */
    public Builder databaseMetadataCacheFields(int databaseMetadataCacheFields) {
      this.databaseMetadataCacheFields = databaseMetadataCacheFields;
      return this;
    }

    /**
     * Maximum size of DatabaseMetaData cache in MiB. Driver default: 5.
     *
     * @param databaseMetadataCacheFieldsMiB cache size in MiB
     * @return this builder
     */
    public Builder databaseMetadataCacheFieldsMiB(int databaseMetadataCacheFieldsMiB) {
      this.databaseMetadataCacheFieldsMiB = databaseMetadataCacheFieldsMiB;
      return this;
    }

    /**
     * Enable adaptive fetch size based on result set size. Driver default: false.
     *
     * @param adaptiveFetch true to enable
     * @return this builder
     */
    public Builder adaptiveFetch(boolean adaptiveFetch) {
      this.adaptiveFetch = adaptiveFetch;
      return this;
    }

    /**
     * Minimum fetch size for adaptive fetch. Driver default: 0.
     *
     * @param adaptiveFetchMinimum minimum fetch size
     * @return this builder
     */
    public Builder adaptiveFetchMinimum(int adaptiveFetchMinimum) {
      this.adaptiveFetchMinimum = adaptiveFetchMinimum;
      return this;
    }

    /**
     * Maximum fetch size for adaptive fetch. Driver default: -1 (unlimited).
     *
     * @param adaptiveFetchMaximum maximum fetch size
     * @return this builder
     */
    public Builder adaptiveFetchMaximum(int adaptiveFetchMaximum) {
      this.adaptiveFetchMaximum = adaptiveFetchMaximum;
      return this;
    }

    // ==================== TIMEOUTS ====================

    /**
     * Timeout for login (authentication) in seconds. Driver default: 0 (unlimited).
     *
     * @param loginTimeout timeout in seconds
     * @return this builder
     */
    public Builder loginTimeout(int loginTimeout) {
      this.loginTimeout = loginTimeout;
      return this;
    }

    /**
     * Timeout for establishing connection in seconds. Driver default: 10.
     *
     * @param connectTimeout timeout in seconds
     * @return this builder
     */
    public Builder connectTimeout(int connectTimeout) {
      this.connectTimeout = connectTimeout;
      return this;
    }

    /**
     * Socket read timeout in seconds. Driver default: 0 (unlimited).
     *
     * @param socketTimeout timeout in seconds
     * @return this builder
     */
    public Builder socketTimeout(int socketTimeout) {
      this.socketTimeout = socketTimeout;
      return this;
    }

    /**
     * Timeout for cancel signal in seconds. Driver default: 10.
     *
     * @param cancelSignalTimeout timeout in seconds
     * @return this builder
     */
    public Builder cancelSignalTimeout(int cancelSignalTimeout) {
      this.cancelSignalTimeout = cancelSignalTimeout;
      return this;
    }

    // ==================== NETWORK ====================

    /**
     * Enable TCP keepalive. Driver default: false.
     *
     * @param tcpKeepAlive true to enable
     * @return this builder
     */
    public Builder tcpKeepAlive(boolean tcpKeepAlive) {
      this.tcpKeepAlive = tcpKeepAlive;
      return this;
    }

    /**
     * Enable TCP no-delay (Nagle's algorithm disabled). Driver default: true.
     *
     * @param tcpNoDelay true to disable Nagle's algorithm
     * @return this builder
     */
    public Builder tcpNoDelay(boolean tcpNoDelay) {
      this.tcpNoDelay = tcpNoDelay;
      return this;
    }

    /**
     * Socket send buffer size in bytes. Driver default: -1 (system default).
     *
     * @param sendBufferSize buffer size in bytes
     * @return this builder
     */
    public Builder sendBufferSize(int sendBufferSize) {
      this.sendBufferSize = sendBufferSize;
      return this;
    }

    /**
     * Socket receive buffer size in bytes. Driver default: -1 (system default).
     *
     * @param receiveBufferSize buffer size in bytes
     * @return this builder
     */
    public Builder receiveBufferSize(int receiveBufferSize) {
      this.receiveBufferSize = receiveBufferSize;
      return this;
    }

    /**
     * Maximum size of data to send in one packet in bytes. Driver default: 8192.
     *
     * @param maxSendBufferSize maximum send size in bytes
     * @return this builder
     */
    public Builder maxSendBufferSize(int maxSendBufferSize) {
      this.maxSendBufferSize = maxSendBufferSize;
      return this;
    }

    // ==================== KERBEROS/GSSAPI ====================

    /**
     * GSS library selection for Kerberos authentication. Driver default: auto.
     *
     * @param gsslib GSS library
     * @return this builder
     */
    public Builder gsslib(PgGssLib gsslib) {
      this.gsslib = gsslib;
      return this;
    }

    /**
     * Kerberos server name (principal). Driver default: postgres.
     *
     * @param kerberosServerName server name
     * @return this builder
     */
    public Builder kerberosServerName(String kerberosServerName) {
      this.kerberosServerName = kerberosServerName;
      return this;
    }

    /**
     * JAAS application name for Kerberos. Driver default: pgjdbc.
     *
     * @param jaasApplicationName application name
     * @return this builder
     */
    public Builder jaasApplicationName(String jaasApplicationName) {
      this.jaasApplicationName = jaasApplicationName;
      return this;
    }

    /**
     * Whether to perform JAAS login. Driver default: true.
     *
     * @param jaasLogin true to perform JAAS login
     * @return this builder
     */
    public Builder jaasLogin(boolean jaasLogin) {
      this.jaasLogin = jaasLogin;
      return this;
    }

    /**
     * Use default GSS credentials from Subject. Driver default: false.
     *
     * @param gssUseDefaultCreds true to use default credentials
     * @return this builder
     */
    public Builder gssUseDefaultCreds(boolean gssUseDefaultCreds) {
      this.gssUseDefaultCreds = gssUseDefaultCreds;
      return this;
    }

    /**
     * GSS encryption mode. Driver default: prefer.
     *
     * @param gssEncMode encryption mode
     * @return this builder
     */
    public Builder gssEncMode(PgGssEncMode gssEncMode) {
      this.gssEncMode = gssEncMode;
      return this;
    }

    /**
     * Timeout for GSS response in milliseconds. Driver default: 5000.
     *
     * @param gssResponseTimeout timeout in milliseconds
     * @return this builder
     */
    public Builder gssResponseTimeout(int gssResponseTimeout) {
      this.gssResponseTimeout = gssResponseTimeout;
      return this;
    }

    /**
     * SSPI service class (Windows). Driver default: POSTGRES.
     *
     * @param sspiServiceClass service class
     * @return this builder
     */
    public Builder sspiServiceClass(String sspiServiceClass) {
      this.sspiServiceClass = sspiServiceClass;
      return this;
    }

    /**
     * Use SPNEGO for SSPI (Windows). Driver default: false.
     *
     * @param useSpnego true to use SPNEGO
     * @return this builder
     */
    public Builder useSpnego(boolean useSpnego) {
      this.useSpnego = useSpnego;
      return this;
    }

    /**
     * Channel binding mode for SCRAM authentication. Driver default: prefer.
     *
     * @param channelBinding channel binding mode
     * @return this builder
     */
    public Builder channelBinding(PgChannelBinding channelBinding) {
      this.channelBinding = channelBinding;
      return this;
    }

    // ==================== BEHAVIOR ====================

    /**
     * Allow encoding changes via SET NAMES. Driver default: false.
     *
     * @param allowEncodingChanges true to allow
     * @return this builder
     */
    public Builder allowEncodingChanges(boolean allowEncodingChanges) {
      this.allowEncodingChanges = allowEncodingChanges;
      return this;
    }

    /**
     * Log a warning when connection is not closed properly. Driver default: false.
     *
     * @param logUnclosedConnections true to log warnings
     * @return this builder
     */
    public Builder logUnclosedConnections(boolean logUnclosedConnections) {
      this.logUnclosedConnections = logUnclosedConnections;
      return this;
    }

    /**
     * Autosave mode for savepoint handling. Driver default: never.
     *
     * @param autosave autosave mode
     * @return this builder
     */
    public Builder autosave(PgAutosave autosave) {
      this.autosave = autosave;
      return this;
    }

    /**
     * Clean up savepoints after transaction. Driver default: false.
     *
     * @param cleanupSavepoints true to clean up
     * @return this builder
     */
    public Builder cleanupSavepoints(boolean cleanupSavepoints) {
      this.cleanupSavepoints = cleanupSavepoints;
      return this;
    }

    /**
     * Type to use for String parameters. Driver default: null (unspecified).
     *
     * @param stringtype type name (e.g., "varchar", "unspecified")
     * @return this builder
     */
    public Builder stringtype(String stringtype) {
      this.stringtype = stringtype;
      return this;
    }

    /**
     * Application name for pg_stat_activity. Driver default: PostgreSQL JDBC Driver.
     *
     * @param applicationName application name
     * @return this builder
     */
    public Builder applicationName(String applicationName) {
      this.applicationName = applicationName;
      return this;
    }

    /**
     * Current schema search path. Driver default: null (use server default).
     *
     * @param currentSchema comma-separated schema names
     * @return this builder
     */
    public Builder currentSchema(String currentSchema) {
      this.currentSchema = currentSchema;
      return this;
    }

    /**
     * Set connection to read-only mode. Driver default: false.
     *
     * @param readOnly true for read-only
     * @return this builder
     */
    public Builder readOnly(boolean readOnly) {
      this.readOnly = readOnly;
      return this;
    }

    /**
     * How to apply read-only mode. Driver default: transaction.
     *
     * @param readOnlyMode read-only mode behavior
     * @return this builder
     */
    public Builder readOnlyMode(PgReadOnlyMode readOnlyMode) {
      this.readOnlyMode = readOnlyMode;
      return this;
    }

    /**
     * Disable column name sanitizer. Driver default: false.
     *
     * @param disableColumnSanitiser true to disable
     * @return this builder
     */
    public Builder disableColumnSanitiser(boolean disableColumnSanitiser) {
      this.disableColumnSanitiser = disableColumnSanitiser;
      return this;
    }

    /**
     * Assume minimum server version (skip version detection). Driver default: null.
     *
     * @param assumeMinServerVersion version string (e.g., "9.6")
     * @return this builder
     */
    public Builder assumeMinServerVersion(String assumeMinServerVersion) {
      this.assumeMinServerVersion = assumeMinServerVersion;
      return this;
    }

    /**
     * Length to assume for unknown types. Driver default: Integer.MAX_VALUE.
     *
     * @param unknownLength length value
     * @return this builder
     */
    public Builder unknownLength(int unknownLength) {
      this.unknownLength = unknownLength;
      return this;
    }

    /**
     * Log server error details in exceptions. Driver default: true.
     *
     * @param logServerErrorDetail true to log details
     * @return this builder
     */
    public Builder logServerErrorDetail(boolean logServerErrorDetail) {
      this.logServerErrorDetail = logServerErrorDetail;
      return this;
    }

    /**
     * Quote identifiers in RETURNING clause. Driver default: false.
     *
     * @param quoteReturningIdentifiers true to quote
     * @return this builder
     */
    public Builder quoteReturningIdentifiers(boolean quoteReturningIdentifiers) {
      this.quoteReturningIdentifiers = quoteReturningIdentifiers;
      return this;
    }

    /**
     * Hide privileged exception details for unprivileged users. Driver default: false.
     *
     * @param hideUnprivilegedExceptions true to hide
     * @return this builder
     */
    public Builder hideUnprivilegedExceptions(boolean hideUnprivilegedExceptions) {
      this.hideUnprivilegedExceptions = hideUnprivilegedExceptions;
      return this;
    }

    /**
     * Server startup parameters (passed as -c options). Driver default: null.
     *
     * @param options startup options (e.g., "-c search_path=myschema")
     * @return this builder
     */
    public Builder options(String options) {
      this.options = options;
      return this;
    }

    // ==================== REPLICATION ====================

    /**
     * Replication mode. Driver default: false (normal connection).
     *
     * @param replication replication mode
     * @return this builder
     */
    public Builder replication(PgReplication replication) {
      this.replication = replication;
      return this;
    }

    /**
     * Target server type for multi-server setups. Driver default: any.
     *
     * @param targetServerType target server type
     * @return this builder
     */
    public Builder targetServerType(PgTargetServerType targetServerType) {
      this.targetServerType = targetServerType;
      return this;
    }

    /**
     * Interval to recheck host status in seconds. Driver default: 10.
     *
     * @param hostRecheckSeconds interval in seconds
     * @return this builder
     */
    public Builder hostRecheckSeconds(int hostRecheckSeconds) {
      this.hostRecheckSeconds = hostRecheckSeconds;
      return this;
    }

    /**
     * Load balance connections across hosts. Driver default: false.
     *
     * @param loadBalanceHosts true to load balance
     * @return this builder
     */
    public Builder loadBalanceHosts(boolean loadBalanceHosts) {
      this.loadBalanceHosts = loadBalanceHosts;
      return this;
    }

    // ==================== SOCKET FACTORY ====================

    /**
     * Custom socket factory class name. Driver default: null.
     *
     * @param socketFactory fully qualified class name
     * @return this builder
     */
    public Builder socketFactory(String socketFactory) {
      this.socketFactory = socketFactory;
      return this;
    }

    /**
     * Argument passed to socket factory constructor. Driver default: null.
     *
     * @param socketFactoryArg factory argument
     * @return this builder
     */
    public Builder socketFactoryArg(String socketFactoryArg) {
      this.socketFactoryArg = socketFactoryArg;
      return this;
    }

    // ==================== RESULT HANDLING ====================

    /**
     * Maximum result buffer size (e.g., "64m", "256k"). Driver default: null (unlimited).
     *
     * @param maxResultBuffer buffer size with unit suffix
     * @return this builder
     */
    public Builder maxResultBuffer(String maxResultBuffer) {
      this.maxResultBuffer = maxResultBuffer;
      return this;
    }

    /**
     * Escape syntax call mode for JDBC escape functions. Driver default: select.
     *
     * @param escapeSyntaxCallMode call mode
     * @return this builder
     */
    public Builder escapeSyntaxCallMode(PgEscapeSyntaxCallMode escapeSyntaxCallMode) {
      this.escapeSyntaxCallMode = escapeSyntaxCallMode;
      return this;
    }

    // ==================== AUTH PLUGIN ====================

    /**
     * Custom authentication plugin class name. Driver default: null.
     *
     * @param authenticationPluginClassName fully qualified class name
     * @return this builder
     */
    public Builder authenticationPluginClassName(String authenticationPluginClassName) {
      this.authenticationPluginClassName = authenticationPluginClassName;
      return this;
    }

    // ==================== ESCAPE HATCH ====================

    /**
     * Set an arbitrary driver property. Use this for undocumented or future properties.
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
     * Build the PostgresConfig.
     *
     * @return immutable PostgresConfig
     */
    public PostgresConfig build() {
      return new PostgresConfig(this);
    }
  }
}
