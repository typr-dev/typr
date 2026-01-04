package dev.typr.foundations.connect.mariadb;

import dev.typr.foundations.connect.DatabaseConfig;
import dev.typr.foundations.connect.DatabaseKind;
import java.util.HashMap;
import java.util.Map;

/**
 * MariaDB database configuration with typed builder methods for all documented JDBC driver
 * properties.
 *
 * <p>Properties are based on the MariaDB Connector/J documentation. Also works with MySQL
 * databases.
 *
 * @see <a href="https://mariadb.com/kb/en/about-mariadb-connector-j/">MariaDB Connector/J
 *     Documentation</a>
 */
public final class MariaDbConfig implements DatabaseConfig {

  private final String host;
  private final int port;
  private final String database;
  private final String username;
  private final String password;

  // SSL/TLS properties
  private final MariaSslMode sslMode;
  private final String serverSslCert;
  private final String keyStore;
  private final String keyStorePassword;
  private final String keyStoreType;
  private final String trustStore;
  private final String trustStorePassword;
  private final String trustStoreType;
  private final String enabledSslCipherSuites;
  private final String enabledSslProtocolSuites;
  private final Boolean disableSslHostnameVerification;

  // Performance properties
  private final Boolean useBulkStmts;
  private final Boolean useBulkStmtsForInserts;
  private final Boolean rewriteBatchedStatements;
  private final Boolean cachePrepStmts;
  private final Integer prepStmtCacheSize;
  private final Integer prepStmtCacheSqlLimit;
  private final Boolean useServerPrepStmts;
  private final Boolean useCompression;
  private final Integer defaultFetchSize;
  private final Boolean useReadAheadInput;
  private final Boolean cacheCallableStmts;
  private final Integer callableStmtCacheSize;
  private final Boolean useBatchMultiSend;
  private final Integer useBatchMultiSendNumber;

  // Timeout properties
  private final Integer connectTimeout;
  private final Integer socketTimeout;
  private final Integer queryTimeout;

  // TCP properties
  private final Boolean tcpKeepAlive;
  private final Integer tcpKeepCount;
  private final Integer tcpKeepIdle;
  private final Integer tcpKeepInterval;
  private final Boolean tcpNoDelay;
  private final Boolean tcpAbortiveClose;

  // Pool properties (for connection pooling in the driver itself)
  private final Boolean pool;
  private final String poolName;
  private final Integer maxPoolSize;
  private final Integer minPoolSize;
  private final Integer maxIdleTime;
  private final Boolean staticGlobal;
  private final Boolean poolValidMinDelay;
  private final Boolean registerJmxPool;

  // Connection properties
  private final Boolean autoReconnect;
  private final String connectionAttributes;
  private final String sessionVariables;
  private final String initSql;
  private final Boolean localSocket;
  private final String pipe;
  private final Boolean tinyInt1isBit;
  private final Boolean yearIsDateType;
  private final Boolean dumpQueriesOnException;
  private final Boolean includeInnodbStatusInDeadlockExceptions;
  private final Boolean includeThreadDumpInDeadlockExceptions;
  private final Integer retriesAllDown;
  private final String galeraAllowedState;
  private final Boolean transactionReplay;

  // Logging properties
  private final Boolean log;
  private final String logSlowQueries;
  private final Long slowQueryThresholdNanos;
  private final Integer maxQuerySizeToLog;
  private final Boolean profileSql;

  // High Availability properties
  private final Boolean assureReadOnly;
  private final Integer validConnectionTimeout;
  private final Integer loadBalanceBlacklistTimeout;
  private final Integer failoverLoopRetries;
  private final Boolean allowMultiQueries;
  private final Boolean allowLocalInfile;

  // Character set properties
  private final String collation;
  private final Boolean useMysqlMetadata;
  private final String nullCatalogMeansCurrent;
  private final Boolean blankTableNameMeta;
  private final Boolean databaseTerm;
  private final Boolean createDatabaseIfNotExist;

  // Timezone properties
  private final String serverTimezone;
  private final Boolean forceConnectionTimeZoneToSession;
  private final Boolean useLegacyDatetimeCode;
  private final Boolean useTimezone;

  // Misc properties
  private final Integer maxAllowedPacket;
  private final Boolean allowPublicKeyRetrieval;
  private final String rsaPublicKey;
  private final Boolean cachingRsaPublicKey;
  private final String serverRsaPublicKeyFile;
  private final String geometryDefaultType;
  private final Boolean restrictedAuth;
  private final String connectionCollation;
  private final Boolean permitMysqlScheme;
  private final String credentialType;
  private final Boolean ensureSocketState;

  // Escape hatch
  private final Map<String, String> extraProperties;

  private MariaDbConfig(Builder b) {
    this.host = b.host;
    this.port = b.port;
    this.database = b.database;
    this.username = b.username;
    this.password = b.password;

    // SSL/TLS
    this.sslMode = b.sslMode;
    this.serverSslCert = b.serverSslCert;
    this.keyStore = b.keyStore;
    this.keyStorePassword = b.keyStorePassword;
    this.keyStoreType = b.keyStoreType;
    this.trustStore = b.trustStore;
    this.trustStorePassword = b.trustStorePassword;
    this.trustStoreType = b.trustStoreType;
    this.enabledSslCipherSuites = b.enabledSslCipherSuites;
    this.enabledSslProtocolSuites = b.enabledSslProtocolSuites;
    this.disableSslHostnameVerification = b.disableSslHostnameVerification;

    // Performance
    this.useBulkStmts = b.useBulkStmts;
    this.useBulkStmtsForInserts = b.useBulkStmtsForInserts;
    this.rewriteBatchedStatements = b.rewriteBatchedStatements;
    this.cachePrepStmts = b.cachePrepStmts;
    this.prepStmtCacheSize = b.prepStmtCacheSize;
    this.prepStmtCacheSqlLimit = b.prepStmtCacheSqlLimit;
    this.useServerPrepStmts = b.useServerPrepStmts;
    this.useCompression = b.useCompression;
    this.defaultFetchSize = b.defaultFetchSize;
    this.useReadAheadInput = b.useReadAheadInput;
    this.cacheCallableStmts = b.cacheCallableStmts;
    this.callableStmtCacheSize = b.callableStmtCacheSize;
    this.useBatchMultiSend = b.useBatchMultiSend;
    this.useBatchMultiSendNumber = b.useBatchMultiSendNumber;

    // Timeouts
    this.connectTimeout = b.connectTimeout;
    this.socketTimeout = b.socketTimeout;
    this.queryTimeout = b.queryTimeout;

    // TCP
    this.tcpKeepAlive = b.tcpKeepAlive;
    this.tcpKeepCount = b.tcpKeepCount;
    this.tcpKeepIdle = b.tcpKeepIdle;
    this.tcpKeepInterval = b.tcpKeepInterval;
    this.tcpNoDelay = b.tcpNoDelay;
    this.tcpAbortiveClose = b.tcpAbortiveClose;

    // Pool
    this.pool = b.pool;
    this.poolName = b.poolName;
    this.maxPoolSize = b.maxPoolSize;
    this.minPoolSize = b.minPoolSize;
    this.maxIdleTime = b.maxIdleTime;
    this.staticGlobal = b.staticGlobal;
    this.poolValidMinDelay = b.poolValidMinDelay;
    this.registerJmxPool = b.registerJmxPool;

    // Connection
    this.autoReconnect = b.autoReconnect;
    this.connectionAttributes = b.connectionAttributes;
    this.sessionVariables = b.sessionVariables;
    this.initSql = b.initSql;
    this.localSocket = b.localSocket;
    this.pipe = b.pipe;
    this.tinyInt1isBit = b.tinyInt1isBit;
    this.yearIsDateType = b.yearIsDateType;
    this.dumpQueriesOnException = b.dumpQueriesOnException;
    this.includeInnodbStatusInDeadlockExceptions = b.includeInnodbStatusInDeadlockExceptions;
    this.includeThreadDumpInDeadlockExceptions = b.includeThreadDumpInDeadlockExceptions;
    this.retriesAllDown = b.retriesAllDown;
    this.galeraAllowedState = b.galeraAllowedState;
    this.transactionReplay = b.transactionReplay;

    // Logging
    this.log = b.log;
    this.logSlowQueries = b.logSlowQueries;
    this.slowQueryThresholdNanos = b.slowQueryThresholdNanos;
    this.maxQuerySizeToLog = b.maxQuerySizeToLog;
    this.profileSql = b.profileSql;

    // High Availability
    this.assureReadOnly = b.assureReadOnly;
    this.validConnectionTimeout = b.validConnectionTimeout;
    this.loadBalanceBlacklistTimeout = b.loadBalanceBlacklistTimeout;
    this.failoverLoopRetries = b.failoverLoopRetries;
    this.allowMultiQueries = b.allowMultiQueries;
    this.allowLocalInfile = b.allowLocalInfile;

    // Character set
    this.collation = b.collation;
    this.useMysqlMetadata = b.useMysqlMetadata;
    this.nullCatalogMeansCurrent = b.nullCatalogMeansCurrent;
    this.blankTableNameMeta = b.blankTableNameMeta;
    this.databaseTerm = b.databaseTerm;
    this.createDatabaseIfNotExist = b.createDatabaseIfNotExist;

    // Timezone
    this.serverTimezone = b.serverTimezone;
    this.forceConnectionTimeZoneToSession = b.forceConnectionTimeZoneToSession;
    this.useLegacyDatetimeCode = b.useLegacyDatetimeCode;
    this.useTimezone = b.useTimezone;

    // Misc
    this.maxAllowedPacket = b.maxAllowedPacket;
    this.allowPublicKeyRetrieval = b.allowPublicKeyRetrieval;
    this.rsaPublicKey = b.rsaPublicKey;
    this.cachingRsaPublicKey = b.cachingRsaPublicKey;
    this.serverRsaPublicKeyFile = b.serverRsaPublicKeyFile;
    this.geometryDefaultType = b.geometryDefaultType;
    this.restrictedAuth = b.restrictedAuth;
    this.connectionCollation = b.connectionCollation;
    this.permitMysqlScheme = b.permitMysqlScheme;
    this.credentialType = b.credentialType;
    this.ensureSocketState = b.ensureSocketState;

    this.extraProperties = Map.copyOf(b.extraProperties);
  }

  /**
   * Create a new builder with required connection parameters.
   *
   * @param host database server hostname
   * @param port database server port (typically 3306)
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
    return "jdbc:mariadb://" + host + ":" + port + "/" + database;
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
    return DatabaseKind.MARIADB;
  }

  @Override
  public Map<String, String> driverProperties() {
    Map<String, String> props = new HashMap<>();

    // SSL/TLS
    if (sslMode != null) props.put("sslMode", sslMode.value());
    if (serverSslCert != null) props.put("serverSslCert", serverSslCert);
    if (keyStore != null) props.put("keyStore", keyStore);
    if (keyStorePassword != null) props.put("keyStorePassword", keyStorePassword);
    if (keyStoreType != null) props.put("keyStoreType", keyStoreType);
    if (trustStore != null) props.put("trustStore", trustStore);
    if (trustStorePassword != null) props.put("trustStorePassword", trustStorePassword);
    if (trustStoreType != null) props.put("trustStoreType", trustStoreType);
    if (enabledSslCipherSuites != null) props.put("enabledSslCipherSuites", enabledSslCipherSuites);
    if (enabledSslProtocolSuites != null)
      props.put("enabledSslProtocolSuites", enabledSslProtocolSuites);
    if (disableSslHostnameVerification != null)
      props.put("disableSslHostnameVerification", disableSslHostnameVerification.toString());

    // Performance
    if (useBulkStmts != null) props.put("useBulkStmts", useBulkStmts.toString());
    if (useBulkStmtsForInserts != null)
      props.put("useBulkStmtsForInserts", useBulkStmtsForInserts.toString());
    if (rewriteBatchedStatements != null)
      props.put("rewriteBatchedStatements", rewriteBatchedStatements.toString());
    if (cachePrepStmts != null) props.put("cachePrepStmts", cachePrepStmts.toString());
    if (prepStmtCacheSize != null) props.put("prepStmtCacheSize", prepStmtCacheSize.toString());
    if (prepStmtCacheSqlLimit != null)
      props.put("prepStmtCacheSqlLimit", prepStmtCacheSqlLimit.toString());
    if (useServerPrepStmts != null) props.put("useServerPrepStmts", useServerPrepStmts.toString());
    if (useCompression != null) props.put("useCompression", useCompression.toString());
    if (defaultFetchSize != null) props.put("defaultFetchSize", defaultFetchSize.toString());
    if (useReadAheadInput != null) props.put("useReadAheadInput", useReadAheadInput.toString());
    if (cacheCallableStmts != null) props.put("cacheCallableStmts", cacheCallableStmts.toString());
    if (callableStmtCacheSize != null)
      props.put("callableStmtCacheSize", callableStmtCacheSize.toString());
    if (useBatchMultiSend != null) props.put("useBatchMultiSend", useBatchMultiSend.toString());
    if (useBatchMultiSendNumber != null)
      props.put("useBatchMultiSendNumber", useBatchMultiSendNumber.toString());

    // Timeouts
    if (connectTimeout != null) props.put("connectTimeout", connectTimeout.toString());
    if (socketTimeout != null) props.put("socketTimeout", socketTimeout.toString());
    if (queryTimeout != null) props.put("queryTimeout", queryTimeout.toString());

    // TCP
    if (tcpKeepAlive != null) props.put("tcpKeepAlive", tcpKeepAlive.toString());
    if (tcpKeepCount != null) props.put("tcpKeepCount", tcpKeepCount.toString());
    if (tcpKeepIdle != null) props.put("tcpKeepIdle", tcpKeepIdle.toString());
    if (tcpKeepInterval != null) props.put("tcpKeepInterval", tcpKeepInterval.toString());
    if (tcpNoDelay != null) props.put("tcpNoDelay", tcpNoDelay.toString());
    if (tcpAbortiveClose != null) props.put("tcpAbortiveClose", tcpAbortiveClose.toString());

    // Pool
    if (pool != null) props.put("pool", pool.toString());
    if (poolName != null) props.put("poolName", poolName);
    if (maxPoolSize != null) props.put("maxPoolSize", maxPoolSize.toString());
    if (minPoolSize != null) props.put("minPoolSize", minPoolSize.toString());
    if (maxIdleTime != null) props.put("maxIdleTime", maxIdleTime.toString());
    if (staticGlobal != null) props.put("staticGlobal", staticGlobal.toString());
    if (poolValidMinDelay != null) props.put("poolValidMinDelay", poolValidMinDelay.toString());
    if (registerJmxPool != null) props.put("registerJmxPool", registerJmxPool.toString());

    // Connection
    if (autoReconnect != null) props.put("autoReconnect", autoReconnect.toString());
    if (connectionAttributes != null) props.put("connectionAttributes", connectionAttributes);
    if (sessionVariables != null) props.put("sessionVariables", sessionVariables);
    if (initSql != null) props.put("initSql", initSql);
    if (localSocket != null) props.put("localSocket", localSocket.toString());
    if (pipe != null) props.put("pipe", pipe);
    if (tinyInt1isBit != null) props.put("tinyInt1isBit", tinyInt1isBit.toString());
    if (yearIsDateType != null) props.put("yearIsDateType", yearIsDateType.toString());
    if (dumpQueriesOnException != null)
      props.put("dumpQueriesOnException", dumpQueriesOnException.toString());
    if (includeInnodbStatusInDeadlockExceptions != null)
      props.put(
          "includeInnodbStatusInDeadlockExceptions",
          includeInnodbStatusInDeadlockExceptions.toString());
    if (includeThreadDumpInDeadlockExceptions != null)
      props.put(
          "includeThreadDumpInDeadlockExceptions",
          includeThreadDumpInDeadlockExceptions.toString());
    if (retriesAllDown != null) props.put("retriesAllDown", retriesAllDown.toString());
    if (galeraAllowedState != null) props.put("galeraAllowedState", galeraAllowedState);
    if (transactionReplay != null) props.put("transactionReplay", transactionReplay.toString());

    // Logging
    if (log != null) props.put("log", log.toString());
    if (logSlowQueries != null) props.put("logSlowQueries", logSlowQueries);
    if (slowQueryThresholdNanos != null)
      props.put("slowQueryThresholdNanos", slowQueryThresholdNanos.toString());
    if (maxQuerySizeToLog != null) props.put("maxQuerySizeToLog", maxQuerySizeToLog.toString());
    if (profileSql != null) props.put("profileSql", profileSql.toString());

    // High Availability
    if (assureReadOnly != null) props.put("assureReadOnly", assureReadOnly.toString());
    if (validConnectionTimeout != null)
      props.put("validConnectionTimeout", validConnectionTimeout.toString());
    if (loadBalanceBlacklistTimeout != null)
      props.put("loadBalanceBlacklistTimeout", loadBalanceBlacklistTimeout.toString());
    if (failoverLoopRetries != null)
      props.put("failoverLoopRetries", failoverLoopRetries.toString());
    if (allowMultiQueries != null) props.put("allowMultiQueries", allowMultiQueries.toString());
    if (allowLocalInfile != null) props.put("allowLocalInfile", allowLocalInfile.toString());

    // Character set
    if (collation != null) props.put("collation", collation);
    if (useMysqlMetadata != null) props.put("useMysqlMetadata", useMysqlMetadata.toString());
    if (nullCatalogMeansCurrent != null)
      props.put("nullCatalogMeansCurrent", nullCatalogMeansCurrent);
    if (blankTableNameMeta != null) props.put("blankTableNameMeta", blankTableNameMeta.toString());
    if (databaseTerm != null) props.put("databaseTerm", databaseTerm.toString());
    if (createDatabaseIfNotExist != null)
      props.put("createDatabaseIfNotExist", createDatabaseIfNotExist.toString());

    // Timezone
    if (serverTimezone != null) props.put("serverTimezone", serverTimezone);
    if (forceConnectionTimeZoneToSession != null)
      props.put("forceConnectionTimeZoneToSession", forceConnectionTimeZoneToSession.toString());
    if (useLegacyDatetimeCode != null)
      props.put("useLegacyDatetimeCode", useLegacyDatetimeCode.toString());
    if (useTimezone != null) props.put("useTimezone", useTimezone.toString());

    // Misc
    if (maxAllowedPacket != null) props.put("maxAllowedPacket", maxAllowedPacket.toString());
    if (allowPublicKeyRetrieval != null)
      props.put("allowPublicKeyRetrieval", allowPublicKeyRetrieval.toString());
    if (rsaPublicKey != null) props.put("rsaPublicKey", rsaPublicKey);
    if (cachingRsaPublicKey != null)
      props.put("cachingRsaPublicKey", cachingRsaPublicKey.toString());
    if (serverRsaPublicKeyFile != null) props.put("serverRsaPublicKeyFile", serverRsaPublicKeyFile);
    if (geometryDefaultType != null) props.put("geometryDefaultType", geometryDefaultType);
    if (restrictedAuth != null) props.put("restrictedAuth", restrictedAuth.toString());
    if (connectionCollation != null) props.put("connectionCollation", connectionCollation);
    if (permitMysqlScheme != null) props.put("permitMysqlScheme", permitMysqlScheme.toString());
    if (credentialType != null) props.put("credentialType", credentialType);
    if (ensureSocketState != null) props.put("ensureSocketState", ensureSocketState.toString());

    props.putAll(extraProperties);
    return props;
  }

  /** Builder for MariaDbConfig with typed methods for all JDBC driver properties. */
  public static final class Builder {
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;

    // SSL/TLS
    private MariaSslMode sslMode;
    private String serverSslCert;
    private String keyStore;
    private String keyStorePassword;
    private String keyStoreType;
    private String trustStore;
    private String trustStorePassword;
    private String trustStoreType;
    private String enabledSslCipherSuites;
    private String enabledSslProtocolSuites;
    private Boolean disableSslHostnameVerification;

    // Performance
    private Boolean useBulkStmts;
    private Boolean useBulkStmtsForInserts;
    private Boolean rewriteBatchedStatements;
    private Boolean cachePrepStmts;
    private Integer prepStmtCacheSize;
    private Integer prepStmtCacheSqlLimit;
    private Boolean useServerPrepStmts;
    private Boolean useCompression;
    private Integer defaultFetchSize;
    private Boolean useReadAheadInput;
    private Boolean cacheCallableStmts;
    private Integer callableStmtCacheSize;
    private Boolean useBatchMultiSend;
    private Integer useBatchMultiSendNumber;

    // Timeouts
    private Integer connectTimeout;
    private Integer socketTimeout;
    private Integer queryTimeout;

    // TCP
    private Boolean tcpKeepAlive;
    private Integer tcpKeepCount;
    private Integer tcpKeepIdle;
    private Integer tcpKeepInterval;
    private Boolean tcpNoDelay;
    private Boolean tcpAbortiveClose;

    // Pool
    private Boolean pool;
    private String poolName;
    private Integer maxPoolSize;
    private Integer minPoolSize;
    private Integer maxIdleTime;
    private Boolean staticGlobal;
    private Boolean poolValidMinDelay;
    private Boolean registerJmxPool;

    // Connection
    private Boolean autoReconnect;
    private String connectionAttributes;
    private String sessionVariables;
    private String initSql;
    private Boolean localSocket;
    private String pipe;
    private Boolean tinyInt1isBit;
    private Boolean yearIsDateType;
    private Boolean dumpQueriesOnException;
    private Boolean includeInnodbStatusInDeadlockExceptions;
    private Boolean includeThreadDumpInDeadlockExceptions;
    private Integer retriesAllDown;
    private String galeraAllowedState;
    private Boolean transactionReplay;

    // Logging
    private Boolean log;
    private String logSlowQueries;
    private Long slowQueryThresholdNanos;
    private Integer maxQuerySizeToLog;
    private Boolean profileSql;

    // High Availability
    private Boolean assureReadOnly;
    private Integer validConnectionTimeout;
    private Integer loadBalanceBlacklistTimeout;
    private Integer failoverLoopRetries;
    private Boolean allowMultiQueries;
    private Boolean allowLocalInfile;

    // Character set
    private String collation;
    private Boolean useMysqlMetadata;
    private String nullCatalogMeansCurrent;
    private Boolean blankTableNameMeta;
    private Boolean databaseTerm;
    private Boolean createDatabaseIfNotExist;

    // Timezone
    private String serverTimezone;
    private Boolean forceConnectionTimeZoneToSession;
    private Boolean useLegacyDatetimeCode;
    private Boolean useTimezone;

    // Misc
    private Integer maxAllowedPacket;
    private Boolean allowPublicKeyRetrieval;
    private String rsaPublicKey;
    private Boolean cachingRsaPublicKey;
    private String serverRsaPublicKeyFile;
    private String geometryDefaultType;
    private Boolean restrictedAuth;
    private String connectionCollation;
    private Boolean permitMysqlScheme;
    private String credentialType;
    private Boolean ensureSocketState;

    private final Map<String, String> extraProperties = new HashMap<>();

    private Builder(String host, int port, String database, String username, String password) {
      this.host = host;
      this.port = port;
      this.database = database;
      this.username = username;
      this.password = password;
    }

    // ==================== SSL/TLS ====================

    /**
     * SSL mode for connection security. Driver default: disable.
     *
     * @param sslMode SSL mode
     * @return this builder
     */
    public Builder sslMode(MariaSslMode sslMode) {
      this.sslMode = sslMode;
      return this;
    }

    /**
     * Path to server SSL certificate. Driver default: null.
     *
     * @param serverSslCert path to certificate file
     * @return this builder
     */
    public Builder serverSslCert(String serverSslCert) {
      this.serverSslCert = serverSslCert;
      return this;
    }

    /**
     * Path to client key store. Driver default: null.
     *
     * @param keyStore path to key store file
     * @return this builder
     */
    public Builder keyStore(String keyStore) {
      this.keyStore = keyStore;
      return this;
    }

    /**
     * Password for key store. Driver default: null.
     *
     * @param keyStorePassword key store password
     * @return this builder
     */
    public Builder keyStorePassword(String keyStorePassword) {
      this.keyStorePassword = keyStorePassword;
      return this;
    }

    /**
     * Key store type. Driver default: JKS.
     *
     * @param keyStoreType key store type
     * @return this builder
     */
    public Builder keyStoreType(String keyStoreType) {
      this.keyStoreType = keyStoreType;
      return this;
    }

    /**
     * Path to trust store. Driver default: null.
     *
     * @param trustStore path to trust store file
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
     * @param trustStoreType trust store type
     * @return this builder
     */
    public Builder trustStoreType(String trustStoreType) {
      this.trustStoreType = trustStoreType;
      return this;
    }

    /**
     * Enabled SSL cipher suites. Driver default: null (all supported).
     *
     * @param enabledSslCipherSuites comma-separated cipher suite names
     * @return this builder
     */
    public Builder enabledSslCipherSuites(String enabledSslCipherSuites) {
      this.enabledSslCipherSuites = enabledSslCipherSuites;
      return this;
    }

    /**
     * Enabled SSL protocol versions. Driver default: null (TLSv1.2, TLSv1.3).
     *
     * @param enabledSslProtocolSuites comma-separated protocol names
     * @return this builder
     */
    public Builder enabledSslProtocolSuites(String enabledSslProtocolSuites) {
      this.enabledSslProtocolSuites = enabledSslProtocolSuites;
      return this;
    }

    /**
     * Disable SSL hostname verification. Driver default: false.
     *
     * @param disableSslHostnameVerification true to disable
     * @return this builder
     */
    public Builder disableSslHostnameVerification(boolean disableSslHostnameVerification) {
      this.disableSslHostnameVerification = disableSslHostnameVerification;
      return this;
    }

    // ==================== PERFORMANCE ====================

    /**
     * Use bulk statements for batch operations. Driver default: true.
     *
     * @param useBulkStmts true to enable
     * @return this builder
     */
    public Builder useBulkStmts(boolean useBulkStmts) {
      this.useBulkStmts = useBulkStmts;
      return this;
    }

    /**
     * Use bulk statements for INSERT only. Driver default: false.
     *
     * @param useBulkStmtsForInserts true to enable
     * @return this builder
     */
    public Builder useBulkStmtsForInserts(boolean useBulkStmtsForInserts) {
      this.useBulkStmtsForInserts = useBulkStmtsForInserts;
      return this;
    }

    /**
     * Rewrite batch statements into multi-value inserts. Driver default: false.
     *
     * @param rewriteBatchedStatements true to enable
     * @return this builder
     */
    public Builder rewriteBatchedStatements(boolean rewriteBatchedStatements) {
      this.rewriteBatchedStatements = rewriteBatchedStatements;
      return this;
    }

    /**
     * Cache prepared statements. Driver default: true.
     *
     * @param cachePrepStmts true to enable
     * @return this builder
     */
    public Builder cachePrepStmts(boolean cachePrepStmts) {
      this.cachePrepStmts = cachePrepStmts;
      return this;
    }

    /**
     * Prepared statement cache size. Driver default: 250.
     *
     * @param prepStmtCacheSize cache size
     * @return this builder
     */
    public Builder prepStmtCacheSize(int prepStmtCacheSize) {
      this.prepStmtCacheSize = prepStmtCacheSize;
      return this;
    }

    /**
     * Maximum SQL length for cached statements. Driver default: 2048.
     *
     * @param prepStmtCacheSqlLimit maximum SQL length
     * @return this builder
     */
    public Builder prepStmtCacheSqlLimit(int prepStmtCacheSqlLimit) {
      this.prepStmtCacheSqlLimit = prepStmtCacheSqlLimit;
      return this;
    }

    /**
     * Use server-side prepared statements. Driver default: false.
     *
     * @param useServerPrepStmts true to enable
     * @return this builder
     */
    public Builder useServerPrepStmts(boolean useServerPrepStmts) {
      this.useServerPrepStmts = useServerPrepStmts;
      return this;
    }

    /**
     * Enable protocol compression. Driver default: false.
     *
     * @param useCompression true to enable
     * @return this builder
     */
    public Builder useCompression(boolean useCompression) {
      this.useCompression = useCompression;
      return this;
    }

    /**
     * Default fetch size for result sets. Driver default: 0 (fetch all).
     *
     * @param defaultFetchSize fetch size
     * @return this builder
     */
    public Builder defaultFetchSize(int defaultFetchSize) {
      this.defaultFetchSize = defaultFetchSize;
      return this;
    }

    /**
     * Use read-ahead input buffering. Driver default: true.
     *
     * @param useReadAheadInput true to enable
     * @return this builder
     */
    public Builder useReadAheadInput(boolean useReadAheadInput) {
      this.useReadAheadInput = useReadAheadInput;
      return this;
    }

    /**
     * Cache callable statements. Driver default: true.
     *
     * @param cacheCallableStmts true to enable
     * @return this builder
     */
    public Builder cacheCallableStmts(boolean cacheCallableStmts) {
      this.cacheCallableStmts = cacheCallableStmts;
      return this;
    }

    /**
     * Callable statement cache size. Driver default: 150.
     *
     * @param callableStmtCacheSize cache size
     * @return this builder
     */
    public Builder callableStmtCacheSize(int callableStmtCacheSize) {
      this.callableStmtCacheSize = callableStmtCacheSize;
      return this;
    }

    /**
     * Send multiple statements in batch. Driver default: true.
     *
     * @param useBatchMultiSend true to enable
     * @return this builder
     */
    public Builder useBatchMultiSend(boolean useBatchMultiSend) {
      this.useBatchMultiSend = useBatchMultiSend;
      return this;
    }

    /**
     * Maximum statements per batch send. Driver default: 100.
     *
     * @param useBatchMultiSendNumber batch size
     * @return this builder
     */
    public Builder useBatchMultiSendNumber(int useBatchMultiSendNumber) {
      this.useBatchMultiSendNumber = useBatchMultiSendNumber;
      return this;
    }

    // ==================== TIMEOUTS ====================

    /**
     * Connection timeout in milliseconds. Driver default: 30000.
     *
     * @param connectTimeout timeout in milliseconds
     * @return this builder
     */
    public Builder connectTimeout(int connectTimeout) {
      this.connectTimeout = connectTimeout;
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
     * Query timeout in seconds. Driver default: 0 (unlimited).
     *
     * @param queryTimeout timeout in seconds
     * @return this builder
     */
    public Builder queryTimeout(int queryTimeout) {
      this.queryTimeout = queryTimeout;
      return this;
    }

    // ==================== TCP ====================

    /**
     * Enable TCP keepalive. Driver default: true.
     *
     * @param tcpKeepAlive true to enable
     * @return this builder
     */
    public Builder tcpKeepAlive(boolean tcpKeepAlive) {
      this.tcpKeepAlive = tcpKeepAlive;
      return this;
    }

    /**
     * TCP keepalive retry count. Driver default: 6.
     *
     * @param tcpKeepCount retry count
     * @return this builder
     */
    public Builder tcpKeepCount(int tcpKeepCount) {
      this.tcpKeepCount = tcpKeepCount;
      return this;
    }

    /**
     * TCP keepalive idle time in seconds. Driver default: 60.
     *
     * @param tcpKeepIdle idle time
     * @return this builder
     */
    public Builder tcpKeepIdle(int tcpKeepIdle) {
      this.tcpKeepIdle = tcpKeepIdle;
      return this;
    }

    /**
     * TCP keepalive interval in seconds. Driver default: 10.
     *
     * @param tcpKeepInterval interval
     * @return this builder
     */
    public Builder tcpKeepInterval(int tcpKeepInterval) {
      this.tcpKeepInterval = tcpKeepInterval;
      return this;
    }

    /**
     * Disable Nagle's algorithm. Driver default: true.
     *
     * @param tcpNoDelay true to disable Nagle
     * @return this builder
     */
    public Builder tcpNoDelay(boolean tcpNoDelay) {
      this.tcpNoDelay = tcpNoDelay;
      return this;
    }

    /**
     * Use abortive close. Driver default: false.
     *
     * @param tcpAbortiveClose true to enable
     * @return this builder
     */
    public Builder tcpAbortiveClose(boolean tcpAbortiveClose) {
      this.tcpAbortiveClose = tcpAbortiveClose;
      return this;
    }

    // ==================== POOL (Driver-internal) ====================

    /**
     * Enable driver-internal connection pooling. Driver default: false.
     *
     * @param pool true to enable
     * @return this builder
     */
    public Builder pool(boolean pool) {
      this.pool = pool;
      return this;
    }

    /**
     * Pool name for JMX registration. Driver default: auto-generated.
     *
     * @param poolName pool name
     * @return this builder
     */
    public Builder poolName(String poolName) {
      this.poolName = poolName;
      return this;
    }

    /**
     * Maximum pool size. Driver default: 8.
     *
     * @param maxPoolSize maximum connections
     * @return this builder
     */
    public Builder maxPoolSize(int maxPoolSize) {
      this.maxPoolSize = maxPoolSize;
      return this;
    }

    /**
     * Minimum pool size. Driver default: maxPoolSize.
     *
     * @param minPoolSize minimum connections
     * @return this builder
     */
    public Builder minPoolSize(int minPoolSize) {
      this.minPoolSize = minPoolSize;
      return this;
    }

    /**
     * Maximum idle time in seconds. Driver default: 600.
     *
     * @param maxIdleTime idle time
     * @return this builder
     */
    public Builder maxIdleTime(int maxIdleTime) {
      this.maxIdleTime = maxIdleTime;
      return this;
    }

    /**
     * Use static global pool. Driver default: false.
     *
     * @param staticGlobal true to use global pool
     * @return this builder
     */
    public Builder staticGlobal(boolean staticGlobal) {
      this.staticGlobal = staticGlobal;
      return this;
    }

    /**
     * Minimum delay between validations. Driver default: true.
     *
     * @param poolValidMinDelay true to enforce delay
     * @return this builder
     */
    public Builder poolValidMinDelay(boolean poolValidMinDelay) {
      this.poolValidMinDelay = poolValidMinDelay;
      return this;
    }

    /**
     * Register pool with JMX. Driver default: true.
     *
     * @param registerJmxPool true to register
     * @return this builder
     */
    public Builder registerJmxPool(boolean registerJmxPool) {
      this.registerJmxPool = registerJmxPool;
      return this;
    }

    // ==================== CONNECTION ====================

    /**
     * Auto-reconnect on connection loss. Driver default: false.
     *
     * @param autoReconnect true to enable
     * @return this builder
     */
    public Builder autoReconnect(boolean autoReconnect) {
      this.autoReconnect = autoReconnect;
      return this;
    }

    /**
     * Connection attributes for server. Driver default: null.
     *
     * @param connectionAttributes comma-separated key=value pairs
     * @return this builder
     */
    public Builder connectionAttributes(String connectionAttributes) {
      this.connectionAttributes = connectionAttributes;
      return this;
    }

    /**
     * Session variables to set on connect. Driver default: null.
     *
     * @param sessionVariables comma-separated var=value pairs
     * @return this builder
     */
    public Builder sessionVariables(String sessionVariables) {
      this.sessionVariables = sessionVariables;
      return this;
    }

    /**
     * SQL to execute on connect. Driver default: null.
     *
     * @param initSql initialization SQL
     * @return this builder
     */
    public Builder initSql(String initSql) {
      this.initSql = initSql;
      return this;
    }

    /**
     * Use Unix local socket. Driver default: false.
     *
     * @param localSocket true to use local socket
     * @return this builder
     */
    public Builder localSocket(boolean localSocket) {
      this.localSocket = localSocket;
      return this;
    }

    /**
     * Windows named pipe path. Driver default: null.
     *
     * @param pipe pipe path
     * @return this builder
     */
    public Builder pipe(String pipe) {
      this.pipe = pipe;
      return this;
    }

    /**
     * Map TINYINT(1) to boolean. Driver default: true.
     *
     * @param tinyInt1isBit true to map to boolean
     * @return this builder
     */
    public Builder tinyInt1isBit(boolean tinyInt1isBit) {
      this.tinyInt1isBit = tinyInt1isBit;
      return this;
    }

    /**
     * Map YEAR to Date. Driver default: true.
     *
     * @param yearIsDateType true to map to Date
     * @return this builder
     */
    public Builder yearIsDateType(boolean yearIsDateType) {
      this.yearIsDateType = yearIsDateType;
      return this;
    }

    /**
     * Dump queries in exception messages. Driver default: false.
     *
     * @param dumpQueriesOnException true to dump
     * @return this builder
     */
    public Builder dumpQueriesOnException(boolean dumpQueriesOnException) {
      this.dumpQueriesOnException = dumpQueriesOnException;
      return this;
    }

    /**
     * Include InnoDB status in deadlock exceptions. Driver default: false.
     *
     * @param includeInnodbStatusInDeadlockExceptions true to include
     * @return this builder
     */
    public Builder includeInnodbStatusInDeadlockExceptions(
        boolean includeInnodbStatusInDeadlockExceptions) {
      this.includeInnodbStatusInDeadlockExceptions = includeInnodbStatusInDeadlockExceptions;
      return this;
    }

    /**
     * Include thread dump in deadlock exceptions. Driver default: false.
     *
     * @param includeThreadDumpInDeadlockExceptions true to include
     * @return this builder
     */
    public Builder includeThreadDumpInDeadlockExceptions(
        boolean includeThreadDumpInDeadlockExceptions) {
      this.includeThreadDumpInDeadlockExceptions = includeThreadDumpInDeadlockExceptions;
      return this;
    }

    /**
     * Retries when all hosts are down. Driver default: 120.
     *
     * @param retriesAllDown retry count
     * @return this builder
     */
    public Builder retriesAllDown(int retriesAllDown) {
      this.retriesAllDown = retriesAllDown;
      return this;
    }

    /**
     * Allowed Galera cluster states. Driver default: null.
     *
     * @param galeraAllowedState comma-separated states
     * @return this builder
     */
    public Builder galeraAllowedState(String galeraAllowedState) {
      this.galeraAllowedState = galeraAllowedState;
      return this;
    }

    /**
     * Enable transaction replay on failover. Driver default: false.
     *
     * @param transactionReplay true to enable
     * @return this builder
     */
    public Builder transactionReplay(boolean transactionReplay) {
      this.transactionReplay = transactionReplay;
      return this;
    }

    // ==================== LOGGING ====================

    /**
     * Enable logging. Driver default: false.
     *
     * @param log true to enable
     * @return this builder
     */
    public Builder log(boolean log) {
      this.log = log;
      return this;
    }

    /**
     * Log slow queries. Driver default: null.
     *
     * @param logSlowQueries "true" or threshold in ms
     * @return this builder
     */
    public Builder logSlowQueries(String logSlowQueries) {
      this.logSlowQueries = logSlowQueries;
      return this;
    }

    /**
     * Slow query threshold in nanoseconds. Driver default: null.
     *
     * @param slowQueryThresholdNanos threshold in nanoseconds
     * @return this builder
     */
    public Builder slowQueryThresholdNanos(long slowQueryThresholdNanos) {
      this.slowQueryThresholdNanos = slowQueryThresholdNanos;
      return this;
    }

    /**
     * Maximum query size to log. Driver default: 1024.
     *
     * @param maxQuerySizeToLog max characters
     * @return this builder
     */
    public Builder maxQuerySizeToLog(int maxQuerySizeToLog) {
      this.maxQuerySizeToLog = maxQuerySizeToLog;
      return this;
    }

    /**
     * Enable SQL profiling. Driver default: false.
     *
     * @param profileSql true to enable
     * @return this builder
     */
    public Builder profileSql(boolean profileSql) {
      this.profileSql = profileSql;
      return this;
    }

    // ==================== HIGH AVAILABILITY ====================

    /**
     * Ensure read-only on replica. Driver default: false.
     *
     * @param assureReadOnly true to ensure
     * @return this builder
     */
    public Builder assureReadOnly(boolean assureReadOnly) {
      this.assureReadOnly = assureReadOnly;
      return this;
    }

    /**
     * Valid connection timeout in seconds. Driver default: 0.
     *
     * @param validConnectionTimeout timeout
     * @return this builder
     */
    public Builder validConnectionTimeout(int validConnectionTimeout) {
      this.validConnectionTimeout = validConnectionTimeout;
      return this;
    }

    /**
     * Load balance blacklist timeout in seconds. Driver default: 50.
     *
     * @param loadBalanceBlacklistTimeout timeout
     * @return this builder
     */
    public Builder loadBalanceBlacklistTimeout(int loadBalanceBlacklistTimeout) {
      this.loadBalanceBlacklistTimeout = loadBalanceBlacklistTimeout;
      return this;
    }

    /**
     * Failover loop retries. Driver default: 120.
     *
     * @param failoverLoopRetries retry count
     * @return this builder
     */
    public Builder failoverLoopRetries(int failoverLoopRetries) {
      this.failoverLoopRetries = failoverLoopRetries;
      return this;
    }

    /**
     * Allow multiple statements per query. Driver default: false.
     *
     * @param allowMultiQueries true to allow
     * @return this builder
     */
    public Builder allowMultiQueries(boolean allowMultiQueries) {
      this.allowMultiQueries = allowMultiQueries;
      return this;
    }

    /**
     * Allow LOAD DATA LOCAL INFILE. Driver default: false.
     *
     * @param allowLocalInfile true to allow
     * @return this builder
     */
    public Builder allowLocalInfile(boolean allowLocalInfile) {
      this.allowLocalInfile = allowLocalInfile;
      return this;
    }

    // ==================== CHARACTER SET ====================

    /**
     * Connection collation. Driver default: null.
     *
     * @param collation collation name
     * @return this builder
     */
    public Builder collation(String collation) {
      this.collation = collation;
      return this;
    }

    /**
     * Use MySQL metadata mode. Driver default: false.
     *
     * @param useMysqlMetadata true to use MySQL mode
     * @return this builder
     */
    public Builder useMysqlMetadata(boolean useMysqlMetadata) {
      this.useMysqlMetadata = useMysqlMetadata;
      return this;
    }

    /**
     * Null catalog means current database. Driver default: null.
     *
     * @param nullCatalogMeansCurrent "true" or "false"
     * @return this builder
     */
    public Builder nullCatalogMeansCurrent(String nullCatalogMeansCurrent) {
      this.nullCatalogMeansCurrent = nullCatalogMeansCurrent;
      return this;
    }

    /**
     * Blank table name in metadata. Driver default: false.
     *
     * @param blankTableNameMeta true to blank
     * @return this builder
     */
    public Builder blankTableNameMeta(boolean blankTableNameMeta) {
      this.blankTableNameMeta = blankTableNameMeta;
      return this;
    }

    /**
     * Database term handling. Driver default: false.
     *
     * @param databaseTerm true for database term mode
     * @return this builder
     */
    public Builder databaseTerm(boolean databaseTerm) {
      this.databaseTerm = databaseTerm;
      return this;
    }

    /**
     * Create database if not exists. Driver default: false.
     *
     * @param createDatabaseIfNotExist true to create
     * @return this builder
     */
    public Builder createDatabaseIfNotExist(boolean createDatabaseIfNotExist) {
      this.createDatabaseIfNotExist = createDatabaseIfNotExist;
      return this;
    }

    // ==================== TIMEZONE ====================

    /**
     * Server timezone. Driver default: null (auto-detect).
     *
     * @param serverTimezone timezone ID
     * @return this builder
     */
    public Builder serverTimezone(String serverTimezone) {
      this.serverTimezone = serverTimezone;
      return this;
    }

    /**
     * Force connection timezone to session. Driver default: false.
     *
     * @param forceConnectionTimeZoneToSession true to force
     * @return this builder
     */
    public Builder forceConnectionTimeZoneToSession(boolean forceConnectionTimeZoneToSession) {
      this.forceConnectionTimeZoneToSession = forceConnectionTimeZoneToSession;
      return this;
    }

    /**
     * Use legacy datetime code. Driver default: false.
     *
     * @param useLegacyDatetimeCode true to use legacy
     * @return this builder
     */
    public Builder useLegacyDatetimeCode(boolean useLegacyDatetimeCode) {
      this.useLegacyDatetimeCode = useLegacyDatetimeCode;
      return this;
    }

    /**
     * Use timezone in date conversions. Driver default: false.
     *
     * @param useTimezone true to use timezone
     * @return this builder
     */
    public Builder useTimezone(boolean useTimezone) {
      this.useTimezone = useTimezone;
      return this;
    }

    // ==================== MISC ====================

    /**
     * Maximum allowed packet size. Driver default: null.
     *
     * @param maxAllowedPacket packet size in bytes
     * @return this builder
     */
    public Builder maxAllowedPacket(int maxAllowedPacket) {
      this.maxAllowedPacket = maxAllowedPacket;
      return this;
    }

    /**
     * Allow public key retrieval for caching_sha2_password. Driver default: false.
     *
     * @param allowPublicKeyRetrieval true to allow
     * @return this builder
     */
    public Builder allowPublicKeyRetrieval(boolean allowPublicKeyRetrieval) {
      this.allowPublicKeyRetrieval = allowPublicKeyRetrieval;
      return this;
    }

    /**
     * RSA public key for authentication. Driver default: null.
     *
     * @param rsaPublicKey public key content
     * @return this builder
     */
    public Builder rsaPublicKey(String rsaPublicKey) {
      this.rsaPublicKey = rsaPublicKey;
      return this;
    }

    /**
     * Use caching RSA public key. Driver default: false.
     *
     * @param cachingRsaPublicKey true to cache
     * @return this builder
     */
    public Builder cachingRsaPublicKey(boolean cachingRsaPublicKey) {
      this.cachingRsaPublicKey = cachingRsaPublicKey;
      return this;
    }

    /**
     * Path to server RSA public key file. Driver default: null.
     *
     * @param serverRsaPublicKeyFile path to file
     * @return this builder
     */
    public Builder serverRsaPublicKeyFile(String serverRsaPublicKeyFile) {
      this.serverRsaPublicKeyFile = serverRsaPublicKeyFile;
      return this;
    }

    /**
     * Default geometry type class. Driver default: null.
     *
     * @param geometryDefaultType class name
     * @return this builder
     */
    public Builder geometryDefaultType(String geometryDefaultType) {
      this.geometryDefaultType = geometryDefaultType;
      return this;
    }

    /**
     * Restrict authentication methods. Driver default: false.
     *
     * @param restrictedAuth true to restrict
     * @return this builder
     */
    public Builder restrictedAuth(boolean restrictedAuth) {
      this.restrictedAuth = restrictedAuth;
      return this;
    }

    /**
     * Connection collation. Driver default: null.
     *
     * @param connectionCollation collation name
     * @return this builder
     */
    public Builder connectionCollation(String connectionCollation) {
      this.connectionCollation = connectionCollation;
      return this;
    }

    /**
     * Permit MySQL scheme in URL. Driver default: false.
     *
     * @param permitMysqlScheme true to permit
     * @return this builder
     */
    public Builder permitMysqlScheme(boolean permitMysqlScheme) {
      this.permitMysqlScheme = permitMysqlScheme;
      return this;
    }

    /**
     * Credential type for authentication. Driver default: null.
     *
     * @param credentialType credential type
     * @return this builder
     */
    public Builder credentialType(String credentialType) {
      this.credentialType = credentialType;
      return this;
    }

    /**
     * Ensure socket state before operations. Driver default: false.
     *
     * @param ensureSocketState true to ensure
     * @return this builder
     */
    public Builder ensureSocketState(boolean ensureSocketState) {
      this.ensureSocketState = ensureSocketState;
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
     * Build the MariaDbConfig.
     *
     * @return immutable MariaDbConfig
     */
    public MariaDbConfig build() {
      return new MariaDbConfig(this);
    }
  }
}
