package dev.typr.foundations.connect.oracle;

import dev.typr.foundations.connect.DatabaseConfig;
import dev.typr.foundations.connect.DatabaseKind;
import java.util.HashMap;
import java.util.Map;

/**
 * Oracle database configuration with typed builder methods for all documented JDBC driver
 * properties.
 *
 * <p>Properties are based on the Oracle JDBC driver documentation.
 *
 * @see <a href="https://docs.oracle.com/en/database/oracle/oracle-database/21/jjdbc/">Oracle JDBC
 *     Documentation</a>
 */
public final class OracleConfig implements DatabaseConfig {

  private final String host;
  private final int port;
  private final String database;
  private final String username;
  private final String password;

  // Connection type properties
  private final String serviceName;
  private final Boolean useSid;
  private final String tnsAdmin;
  private final String tnsAlias;

  // Performance properties
  private final Integer defaultRowPrefetch;
  private final Integer defaultExecuteBatch;
  private final Integer implicitStatementCacheSize;
  private final Integer maxStatements;
  private final Boolean implicitCachingEnabled;
  private final Boolean explicitCachingEnabled;
  private final Integer readTimeout;
  private final Integer connectTimeout;
  private final Integer maxCachedBufferSize;
  private final Boolean processEscapes;
  private final Integer statementCacheSize;

  // LOB properties
  private final Boolean useFetchSizeWithLongColumn;
  private final Integer lobPrefetchSize;
  private final Boolean prefetchLOBs;
  private final String lobStreamPosStandard;
  private final Boolean tempBlobCleanUp;
  private final Boolean tempClobCleanUp;

  // Batch properties
  private final Integer batchSize;
  private final Boolean useBatchMultiRetrieve;
  private final Boolean enableBatchUpdates;

  // Metadata properties
  private final Boolean remarksReporting;
  private final Boolean includeSynonyms;
  private final Boolean restrictGetTables;
  private final Boolean accumulateBatchResult;
  private final String defaultNChar;

  // Network properties
  private final Integer tcpNoDelay;
  private final Integer keepAlive;
  private final Integer sendBufferSize;
  private final Integer receiveBufferSize;
  private final Boolean thinNetAllowPM;
  private final String networkProtocol;

  // SSL/TLS properties
  private final String sslServerCertDN;
  private final String trustStore;
  private final String trustStorePassword;
  private final String trustStoreType;
  private final String keyStore;
  private final String keyStorePassword;
  private final String keyStoreType;
  private final String walletLocation;
  private final String walletPassword;
  private final Boolean sslServerDNMatch;

  // High Availability properties
  private final Boolean fastConnectionFailover;
  private final Integer onsConfiguration;
  private final Integer retryCount;
  private final Integer retryDelay;
  private final Boolean implicitConnectionTimeout;
  private final String fanEnabled;
  private final String onsWalletLocation;
  private final String onsWalletPassword;
  private final Boolean onsNodes;

  // Timezone properties
  private final String sessionTimeZone;
  private final Boolean convertNCharLiterals;

  // Tracing/Debugging properties
  private final Boolean traceLevel;
  private final String traceFile;
  private final Integer traceFileSize;
  private final Integer traceMaxFiles;
  private final Boolean logTraceEnabled;

  // Proxy authentication properties
  private final String proxyClientName;
  private final String proxyClientDN;
  private final String proxyRoles;
  private final String proxyPassword;

  // Java properties
  private final Integer threadPoolSize;
  private final String javaObjectTypeClass;
  private final Boolean disableDefineColumnType;
  private final Boolean allowNCharLiteral;
  private final Boolean createDescriptorUseCurrentSchemaForSchemaName;

  // Statement properties
  private final Boolean defaultStatementModeIsNonBlocking;
  private final Boolean defaultExecuteAsync;
  private final Boolean streamChunkSize;

  // Oracle-specific features
  private final Boolean restrictedList;
  private final Boolean sqlCl;
  private final Boolean reportRemarks;
  private final Boolean getPlSqlErrorFromServerOnProcedureCall;
  private final Boolean plsqlCompilerWarnings;
  private final String editionName;
  private final Boolean internalLogon;
  private final String connectionClassName;
  private final Boolean enableScrollableResultSet;
  private final Boolean enableReadOnlyResultSet;
  private final Boolean enableCancelQueryOnClose;

  // Sharding properties
  private final String shardingKey;
  private final String superShardingKey;

  // XA properties
  private final Boolean xaRecoveryEnabled;
  private final Boolean xaTightlyCouple;

  // Connection validation properties
  private final Boolean checkConnectionOnBorrow;
  private final String validationSQL;
  private final Integer validationTimeout;
  private final Integer secondsToTrustIdleConnection;
  private final Integer inactivityTimeout;
  private final Integer abandonedConnectionTimeout;
  private final Integer timeToLiveConnectionTimeout;

  // Escape hatch
  private final Map<String, String> extraProperties;

  private OracleConfig(Builder b) {
    this.host = b.host;
    this.port = b.port;
    this.database = b.database;
    this.username = b.username;
    this.password = b.password;

    // Connection type
    this.serviceName = b.serviceName;
    this.useSid = b.useSid;
    this.tnsAdmin = b.tnsAdmin;
    this.tnsAlias = b.tnsAlias;

    // Performance
    this.defaultRowPrefetch = b.defaultRowPrefetch;
    this.defaultExecuteBatch = b.defaultExecuteBatch;
    this.implicitStatementCacheSize = b.implicitStatementCacheSize;
    this.maxStatements = b.maxStatements;
    this.implicitCachingEnabled = b.implicitCachingEnabled;
    this.explicitCachingEnabled = b.explicitCachingEnabled;
    this.readTimeout = b.readTimeout;
    this.connectTimeout = b.connectTimeout;
    this.maxCachedBufferSize = b.maxCachedBufferSize;
    this.processEscapes = b.processEscapes;
    this.statementCacheSize = b.statementCacheSize;

    // LOB
    this.useFetchSizeWithLongColumn = b.useFetchSizeWithLongColumn;
    this.lobPrefetchSize = b.lobPrefetchSize;
    this.prefetchLOBs = b.prefetchLOBs;
    this.lobStreamPosStandard = b.lobStreamPosStandard;
    this.tempBlobCleanUp = b.tempBlobCleanUp;
    this.tempClobCleanUp = b.tempClobCleanUp;

    // Batch
    this.batchSize = b.batchSize;
    this.useBatchMultiRetrieve = b.useBatchMultiRetrieve;
    this.enableBatchUpdates = b.enableBatchUpdates;

    // Metadata
    this.remarksReporting = b.remarksReporting;
    this.includeSynonyms = b.includeSynonyms;
    this.restrictGetTables = b.restrictGetTables;
    this.accumulateBatchResult = b.accumulateBatchResult;
    this.defaultNChar = b.defaultNChar;

    // Network
    this.tcpNoDelay = b.tcpNoDelay;
    this.keepAlive = b.keepAlive;
    this.sendBufferSize = b.sendBufferSize;
    this.receiveBufferSize = b.receiveBufferSize;
    this.thinNetAllowPM = b.thinNetAllowPM;
    this.networkProtocol = b.networkProtocol;

    // SSL/TLS
    this.sslServerCertDN = b.sslServerCertDN;
    this.trustStore = b.trustStore;
    this.trustStorePassword = b.trustStorePassword;
    this.trustStoreType = b.trustStoreType;
    this.keyStore = b.keyStore;
    this.keyStorePassword = b.keyStorePassword;
    this.keyStoreType = b.keyStoreType;
    this.walletLocation = b.walletLocation;
    this.walletPassword = b.walletPassword;
    this.sslServerDNMatch = b.sslServerDNMatch;

    // High Availability
    this.fastConnectionFailover = b.fastConnectionFailover;
    this.onsConfiguration = b.onsConfiguration;
    this.retryCount = b.retryCount;
    this.retryDelay = b.retryDelay;
    this.implicitConnectionTimeout = b.implicitConnectionTimeout;
    this.fanEnabled = b.fanEnabled;
    this.onsWalletLocation = b.onsWalletLocation;
    this.onsWalletPassword = b.onsWalletPassword;
    this.onsNodes = b.onsNodes;

    // Timezone
    this.sessionTimeZone = b.sessionTimeZone;
    this.convertNCharLiterals = b.convertNCharLiterals;

    // Tracing
    this.traceLevel = b.traceLevel;
    this.traceFile = b.traceFile;
    this.traceFileSize = b.traceFileSize;
    this.traceMaxFiles = b.traceMaxFiles;
    this.logTraceEnabled = b.logTraceEnabled;

    // Proxy
    this.proxyClientName = b.proxyClientName;
    this.proxyClientDN = b.proxyClientDN;
    this.proxyRoles = b.proxyRoles;
    this.proxyPassword = b.proxyPassword;

    // Java
    this.threadPoolSize = b.threadPoolSize;
    this.javaObjectTypeClass = b.javaObjectTypeClass;
    this.disableDefineColumnType = b.disableDefineColumnType;
    this.allowNCharLiteral = b.allowNCharLiteral;
    this.createDescriptorUseCurrentSchemaForSchemaName =
        b.createDescriptorUseCurrentSchemaForSchemaName;

    // Statement
    this.defaultStatementModeIsNonBlocking = b.defaultStatementModeIsNonBlocking;
    this.defaultExecuteAsync = b.defaultExecuteAsync;
    this.streamChunkSize = b.streamChunkSize;

    // Oracle-specific
    this.restrictedList = b.restrictedList;
    this.sqlCl = b.sqlCl;
    this.reportRemarks = b.reportRemarks;
    this.getPlSqlErrorFromServerOnProcedureCall = b.getPlSqlErrorFromServerOnProcedureCall;
    this.plsqlCompilerWarnings = b.plsqlCompilerWarnings;
    this.editionName = b.editionName;
    this.internalLogon = b.internalLogon;
    this.connectionClassName = b.connectionClassName;
    this.enableScrollableResultSet = b.enableScrollableResultSet;
    this.enableReadOnlyResultSet = b.enableReadOnlyResultSet;
    this.enableCancelQueryOnClose = b.enableCancelQueryOnClose;

    // Sharding
    this.shardingKey = b.shardingKey;
    this.superShardingKey = b.superShardingKey;

    // XA
    this.xaRecoveryEnabled = b.xaRecoveryEnabled;
    this.xaTightlyCouple = b.xaTightlyCouple;

    // Connection validation
    this.checkConnectionOnBorrow = b.checkConnectionOnBorrow;
    this.validationSQL = b.validationSQL;
    this.validationTimeout = b.validationTimeout;
    this.secondsToTrustIdleConnection = b.secondsToTrustIdleConnection;
    this.inactivityTimeout = b.inactivityTimeout;
    this.abandonedConnectionTimeout = b.abandonedConnectionTimeout;
    this.timeToLiveConnectionTimeout = b.timeToLiveConnectionTimeout;

    this.extraProperties = Map.copyOf(b.extraProperties);
  }

  /**
   * Create a new builder with required connection parameters using SID.
   *
   * @param host Oracle server hostname
   * @param port Oracle server port (typically 1521)
   * @param database SID or service name
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
    if (tnsAlias != null) {
      return "jdbc:oracle:thin:@" + tnsAlias;
    } else if (serviceName != null) {
      return "jdbc:oracle:thin:@//" + host + ":" + port + "/" + serviceName;
    } else {
      return "jdbc:oracle:thin:@" + host + ":" + port + ":" + database;
    }
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
    return DatabaseKind.ORACLE;
  }

  @Override
  public Map<String, String> driverProperties() {
    Map<String, String> props = new HashMap<>();

    // Performance
    if (defaultRowPrefetch != null)
      props.put("oracle.jdbc.defaultRowPrefetch", defaultRowPrefetch.toString());
    if (defaultExecuteBatch != null)
      props.put("oracle.jdbc.defaultExecuteBatch", defaultExecuteBatch.toString());
    if (implicitStatementCacheSize != null)
      props.put("oracle.jdbc.implicitStatementCacheSize", implicitStatementCacheSize.toString());
    if (maxStatements != null) props.put("oracle.jdbc.maxStatements", maxStatements.toString());
    if (implicitCachingEnabled != null)
      props.put("oracle.jdbc.implicitCachingEnabled", implicitCachingEnabled.toString());
    if (explicitCachingEnabled != null)
      props.put("oracle.jdbc.explicitCachingEnabled", explicitCachingEnabled.toString());
    if (readTimeout != null)
      props.put("oracle.net.READ_TIMEOUT", String.valueOf(readTimeout * 1000));
    if (connectTimeout != null)
      props.put("oracle.net.CONNECT_TIMEOUT", String.valueOf(connectTimeout * 1000));
    if (maxCachedBufferSize != null)
      props.put("oracle.jdbc.maxCachedBufferSize", maxCachedBufferSize.toString());
    if (processEscapes != null) props.put("oracle.jdbc.processEscapes", processEscapes.toString());
    if (statementCacheSize != null)
      props.put("oracle.jdbc.statementCacheSize", statementCacheSize.toString());

    // LOB
    if (useFetchSizeWithLongColumn != null)
      props.put("oracle.jdbc.useFetchSizeWithLongColumn", useFetchSizeWithLongColumn.toString());
    if (lobPrefetchSize != null)
      props.put("oracle.jdbc.lobPrefetchSize", lobPrefetchSize.toString());
    if (prefetchLOBs != null) props.put("oracle.jdbc.prefetchLOBs", prefetchLOBs.toString());
    if (lobStreamPosStandard != null)
      props.put("oracle.jdbc.lobStreamPosStandard", lobStreamPosStandard);
    if (tempBlobCleanUp != null)
      props.put("oracle.jdbc.tempBlobCleanUp", tempBlobCleanUp.toString());
    if (tempClobCleanUp != null)
      props.put("oracle.jdbc.tempClobCleanUp", tempClobCleanUp.toString());

    // Batch
    if (batchSize != null) props.put("oracle.jdbc.batchSize", batchSize.toString());
    if (useBatchMultiRetrieve != null)
      props.put("oracle.jdbc.useBatchMultiRetrieve", useBatchMultiRetrieve.toString());
    if (enableBatchUpdates != null)
      props.put("oracle.jdbc.enableBatchUpdates", enableBatchUpdates.toString());

    // Metadata
    if (remarksReporting != null)
      props.put("oracle.jdbc.remarksReporting", remarksReporting.toString());
    if (includeSynonyms != null)
      props.put("oracle.jdbc.includeSynonyms", includeSynonyms.toString());
    if (restrictGetTables != null)
      props.put("oracle.jdbc.restrictGetTables", restrictGetTables.toString());
    if (accumulateBatchResult != null)
      props.put("oracle.jdbc.accumulateBatchResult", accumulateBatchResult.toString());
    if (defaultNChar != null) props.put("oracle.jdbc.defaultNChar", defaultNChar);

    // Network
    if (tcpNoDelay != null) props.put("oracle.net.tcp_nodelay", tcpNoDelay.toString());
    if (keepAlive != null) props.put("oracle.net.keepAlive", keepAlive.toString());
    if (sendBufferSize != null) props.put("oracle.net.sendBufferSize", sendBufferSize.toString());
    if (receiveBufferSize != null)
      props.put("oracle.net.receiveBufferSize", receiveBufferSize.toString());
    if (thinNetAllowPM != null) props.put("oracle.jdbc.thinNetAllowPM", thinNetAllowPM.toString());
    if (networkProtocol != null) props.put("oracle.jdbc.networkProtocol", networkProtocol);

    // SSL/TLS
    if (sslServerCertDN != null) props.put("oracle.net.ssl_server_cert_dn", sslServerCertDN);
    if (trustStore != null) props.put("javax.net.ssl.trustStore", trustStore);
    if (trustStorePassword != null)
      props.put("javax.net.ssl.trustStorePassword", trustStorePassword);
    if (trustStoreType != null) props.put("javax.net.ssl.trustStoreType", trustStoreType);
    if (keyStore != null) props.put("javax.net.ssl.keyStore", keyStore);
    if (keyStorePassword != null) props.put("javax.net.ssl.keyStorePassword", keyStorePassword);
    if (keyStoreType != null) props.put("javax.net.ssl.keyStoreType", keyStoreType);
    if (walletLocation != null) props.put("oracle.net.wallet_location", walletLocation);
    if (walletPassword != null) props.put("oracle.net.wallet_password", walletPassword);
    if (sslServerDNMatch != null)
      props.put("oracle.net.ssl_server_dn_match", sslServerDNMatch.toString());

    // High Availability
    if (fastConnectionFailover != null)
      props.put("oracle.jdbc.fastConnectionFailover", fastConnectionFailover.toString());
    if (onsConfiguration != null)
      props.put("oracle.ons.configuration", onsConfiguration.toString());
    if (retryCount != null) props.put("oracle.jdbc.retryCount", retryCount.toString());
    if (retryDelay != null) props.put("oracle.jdbc.retryDelay", retryDelay.toString());
    if (implicitConnectionTimeout != null)
      props.put("oracle.jdbc.implicitConnectionTimeout", implicitConnectionTimeout.toString());
    if (fanEnabled != null) props.put("oracle.jdbc.fanEnabled", fanEnabled);
    if (onsWalletLocation != null) props.put("oracle.ons.walletLocation", onsWalletLocation);
    if (onsWalletPassword != null) props.put("oracle.ons.walletPassword", onsWalletPassword);
    if (onsNodes != null) props.put("oracle.ons.nodes", onsNodes.toString());

    // Timezone
    if (sessionTimeZone != null) props.put("oracle.jdbc.sessionTimeZone", sessionTimeZone);
    if (convertNCharLiterals != null)
      props.put("oracle.jdbc.convertNCharLiterals", convertNCharLiterals.toString());

    // Tracing
    if (traceLevel != null) props.put("oracle.jdbc.traceLevel", traceLevel.toString());
    if (traceFile != null) props.put("oracle.jdbc.traceFile", traceFile);
    if (traceFileSize != null) props.put("oracle.jdbc.traceFileSize", traceFileSize.toString());
    if (traceMaxFiles != null) props.put("oracle.jdbc.traceMaxFiles", traceMaxFiles.toString());
    if (logTraceEnabled != null)
      props.put("oracle.jdbc.logTraceEnabled", logTraceEnabled.toString());

    // Proxy
    if (proxyClientName != null) props.put("oracle.jdbc.proxyClientName", proxyClientName);
    if (proxyClientDN != null) props.put("oracle.jdbc.proxyClientDN", proxyClientDN);
    if (proxyRoles != null) props.put("oracle.jdbc.proxyRoles", proxyRoles);
    if (proxyPassword != null) props.put("oracle.jdbc.proxyPassword", proxyPassword);

    // Java
    if (threadPoolSize != null) props.put("oracle.jdbc.threadPoolSize", threadPoolSize.toString());
    if (javaObjectTypeClass != null)
      props.put("oracle.jdbc.javaObjectTypeClass", javaObjectTypeClass);
    if (disableDefineColumnType != null)
      props.put("oracle.jdbc.disableDefineColumnType", disableDefineColumnType.toString());
    if (allowNCharLiteral != null)
      props.put("oracle.jdbc.allowNCharLiteral", allowNCharLiteral.toString());
    if (createDescriptorUseCurrentSchemaForSchemaName != null)
      props.put(
          "oracle.jdbc.createDescriptorUseCurrentSchemaForSchemaName",
          createDescriptorUseCurrentSchemaForSchemaName.toString());

    // Statement
    if (defaultStatementModeIsNonBlocking != null)
      props.put(
          "oracle.jdbc.defaultStatementModeIsNonBlocking",
          defaultStatementModeIsNonBlocking.toString());
    if (defaultExecuteAsync != null)
      props.put("oracle.jdbc.defaultExecuteAsync", defaultExecuteAsync.toString());
    if (streamChunkSize != null)
      props.put("oracle.jdbc.streamChunkSize", streamChunkSize.toString());

    // Oracle-specific
    if (restrictedList != null) props.put("oracle.jdbc.restrictedList", restrictedList.toString());
    if (sqlCl != null) props.put("oracle.jdbc.sqlCl", sqlCl.toString());
    if (reportRemarks != null) props.put("oracle.jdbc.reportRemarks", reportRemarks.toString());
    if (getPlSqlErrorFromServerOnProcedureCall != null)
      props.put(
          "oracle.jdbc.getPlSqlErrorFromServerOnProcedureCall",
          getPlSqlErrorFromServerOnProcedureCall.toString());
    if (plsqlCompilerWarnings != null)
      props.put("oracle.jdbc.plsqlCompilerWarnings", plsqlCompilerWarnings.toString());
    if (editionName != null) props.put("oracle.jdbc.editionName", editionName);
    if (internalLogon != null) props.put("internal_logon", internalLogon.toString());
    if (connectionClassName != null)
      props.put("oracle.jdbc.connectionClassName", connectionClassName);
    if (enableScrollableResultSet != null)
      props.put("oracle.jdbc.enableScrollableResultSet", enableScrollableResultSet.toString());
    if (enableReadOnlyResultSet != null)
      props.put("oracle.jdbc.enableReadOnlyResultSet", enableReadOnlyResultSet.toString());
    if (enableCancelQueryOnClose != null)
      props.put("oracle.jdbc.enableCancelQueryOnClose", enableCancelQueryOnClose.toString());

    // Sharding
    if (shardingKey != null) props.put("oracle.jdbc.shardingKey", shardingKey);
    if (superShardingKey != null) props.put("oracle.jdbc.superShardingKey", superShardingKey);

    // XA
    if (xaRecoveryEnabled != null)
      props.put("oracle.jdbc.xaRecoveryEnabled", xaRecoveryEnabled.toString());
    if (xaTightlyCouple != null)
      props.put("oracle.jdbc.xaTightlyCouple", xaTightlyCouple.toString());

    // Connection validation
    if (checkConnectionOnBorrow != null)
      props.put("oracle.jdbc.checkConnectionOnBorrow", checkConnectionOnBorrow.toString());
    if (validationSQL != null) props.put("oracle.jdbc.validationSQL", validationSQL);
    if (validationTimeout != null)
      props.put("oracle.jdbc.validationTimeout", validationTimeout.toString());
    if (secondsToTrustIdleConnection != null)
      props.put(
          "oracle.jdbc.secondsToTrustIdleConnection", secondsToTrustIdleConnection.toString());
    if (inactivityTimeout != null)
      props.put("oracle.jdbc.inactivityTimeout", inactivityTimeout.toString());
    if (abandonedConnectionTimeout != null)
      props.put("oracle.jdbc.abandonedConnectionTimeout", abandonedConnectionTimeout.toString());
    if (timeToLiveConnectionTimeout != null)
      props.put("oracle.jdbc.timeToLiveConnectionTimeout", timeToLiveConnectionTimeout.toString());

    props.putAll(extraProperties);
    return props;
  }

  /** Builder for OracleConfig with typed methods for all JDBC driver properties. */
  public static final class Builder {
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;

    // Connection type
    private String serviceName;
    private Boolean useSid;
    private String tnsAdmin;
    private String tnsAlias;

    // Performance
    private Integer defaultRowPrefetch;
    private Integer defaultExecuteBatch;
    private Integer implicitStatementCacheSize;
    private Integer maxStatements;
    private Boolean implicitCachingEnabled;
    private Boolean explicitCachingEnabled;
    private Integer readTimeout;
    private Integer connectTimeout;
    private Integer maxCachedBufferSize;
    private Boolean processEscapes;
    private Integer statementCacheSize;

    // LOB
    private Boolean useFetchSizeWithLongColumn;
    private Integer lobPrefetchSize;
    private Boolean prefetchLOBs;
    private String lobStreamPosStandard;
    private Boolean tempBlobCleanUp;
    private Boolean tempClobCleanUp;

    // Batch
    private Integer batchSize;
    private Boolean useBatchMultiRetrieve;
    private Boolean enableBatchUpdates;

    // Metadata
    private Boolean remarksReporting;
    private Boolean includeSynonyms;
    private Boolean restrictGetTables;
    private Boolean accumulateBatchResult;
    private String defaultNChar;

    // Network
    private Integer tcpNoDelay;
    private Integer keepAlive;
    private Integer sendBufferSize;
    private Integer receiveBufferSize;
    private Boolean thinNetAllowPM;
    private String networkProtocol;

    // SSL/TLS
    private String sslServerCertDN;
    private String trustStore;
    private String trustStorePassword;
    private String trustStoreType;
    private String keyStore;
    private String keyStorePassword;
    private String keyStoreType;
    private String walletLocation;
    private String walletPassword;
    private Boolean sslServerDNMatch;

    // High Availability
    private Boolean fastConnectionFailover;
    private Integer onsConfiguration;
    private Integer retryCount;
    private Integer retryDelay;
    private Boolean implicitConnectionTimeout;
    private String fanEnabled;
    private String onsWalletLocation;
    private String onsWalletPassword;
    private Boolean onsNodes;

    // Timezone
    private String sessionTimeZone;
    private Boolean convertNCharLiterals;

    // Tracing
    private Boolean traceLevel;
    private String traceFile;
    private Integer traceFileSize;
    private Integer traceMaxFiles;
    private Boolean logTraceEnabled;

    // Proxy
    private String proxyClientName;
    private String proxyClientDN;
    private String proxyRoles;
    private String proxyPassword;

    // Java
    private Integer threadPoolSize;
    private String javaObjectTypeClass;
    private Boolean disableDefineColumnType;
    private Boolean allowNCharLiteral;
    private Boolean createDescriptorUseCurrentSchemaForSchemaName;

    // Statement
    private Boolean defaultStatementModeIsNonBlocking;
    private Boolean defaultExecuteAsync;
    private Boolean streamChunkSize;

    // Oracle-specific
    private Boolean restrictedList;
    private Boolean sqlCl;
    private Boolean reportRemarks;
    private Boolean getPlSqlErrorFromServerOnProcedureCall;
    private Boolean plsqlCompilerWarnings;
    private String editionName;
    private Boolean internalLogon;
    private String connectionClassName;
    private Boolean enableScrollableResultSet;
    private Boolean enableReadOnlyResultSet;
    private Boolean enableCancelQueryOnClose;

    // Sharding
    private String shardingKey;
    private String superShardingKey;

    // XA
    private Boolean xaRecoveryEnabled;
    private Boolean xaTightlyCouple;

    // Connection validation
    private Boolean checkConnectionOnBorrow;
    private String validationSQL;
    private Integer validationTimeout;
    private Integer secondsToTrustIdleConnection;
    private Integer inactivityTimeout;
    private Integer abandonedConnectionTimeout;
    private Integer timeToLiveConnectionTimeout;

    private final Map<String, String> extraProperties = new HashMap<>();

    private Builder(String host, int port, String database, String username, String password) {
      this.host = host;
      this.port = port;
      this.database = database;
      this.username = username;
      this.password = password;

      // OUR DEFAULTS (better than driver defaults)
      this.defaultRowPrefetch = 100; // Driver default is 10, which is too low
    }

    // ==================== CONNECTION TYPE ====================

    /**
     * Use service name instead of SID. Driver default: false (use SID).
     *
     * @param serviceName Oracle service name
     * @return this builder
     */
    public Builder serviceName(String serviceName) {
      this.serviceName = serviceName;
      return this;
    }

    /**
     * Explicitly use SID connection format. Driver default: true.
     *
     * @param useSid true to use SID format
     * @return this builder
     */
    public Builder useSid(boolean useSid) {
      this.useSid = useSid;
      return this;
    }

    /**
     * TNS admin directory for tnsnames.ora. Driver default: null.
     *
     * @param tnsAdmin path to TNS admin directory
     * @return this builder
     */
    public Builder tnsAdmin(String tnsAdmin) {
      this.tnsAdmin = tnsAdmin;
      return this;
    }

    /**
     * TNS alias from tnsnames.ora. Driver default: null.
     *
     * @param tnsAlias alias name
     * @return this builder
     */
    public Builder tnsAlias(String tnsAlias) {
      this.tnsAlias = tnsAlias;
      return this;
    }

    // ==================== PERFORMANCE ====================

    /**
     * Default row prefetch size. Driver default: 10. OUR DEFAULT: 100 (better for most use cases).
     *
     * @param defaultRowPrefetch prefetch size
     * @return this builder
     */
    public Builder defaultRowPrefetch(int defaultRowPrefetch) {
      this.defaultRowPrefetch = defaultRowPrefetch;
      return this;
    }

    /**
     * Default batch size for execute. Driver default: 1.
     *
     * @param defaultExecuteBatch batch size
     * @return this builder
     */
    public Builder defaultExecuteBatch(int defaultExecuteBatch) {
      this.defaultExecuteBatch = defaultExecuteBatch;
      return this;
    }

    /**
     * Implicit statement cache size. Driver default: 0.
     *
     * @param implicitStatementCacheSize cache size
     * @return this builder
     */
    public Builder implicitStatementCacheSize(int implicitStatementCacheSize) {
      this.implicitStatementCacheSize = implicitStatementCacheSize;
      return this;
    }

    /**
     * Maximum cached statements. Driver default: 0.
     *
     * @param maxStatements max statements
     * @return this builder
     */
    public Builder maxStatements(int maxStatements) {
      this.maxStatements = maxStatements;
      return this;
    }

    /**
     * Enable implicit statement caching. Driver default: false.
     *
     * @param implicitCachingEnabled true to enable
     * @return this builder
     */
    public Builder implicitCachingEnabled(boolean implicitCachingEnabled) {
      this.implicitCachingEnabled = implicitCachingEnabled;
      return this;
    }

    /**
     * Enable explicit statement caching. Driver default: false.
     *
     * @param explicitCachingEnabled true to enable
     * @return this builder
     */
    public Builder explicitCachingEnabled(boolean explicitCachingEnabled) {
      this.explicitCachingEnabled = explicitCachingEnabled;
      return this;
    }

    /**
     * Read timeout in seconds. Driver default: 0 (infinite).
     *
     * @param readTimeout timeout in seconds
     * @return this builder
     */
    public Builder readTimeout(int readTimeout) {
      this.readTimeout = readTimeout;
      return this;
    }

    /**
     * Connect timeout in seconds. Driver default: 0 (infinite).
     *
     * @param connectTimeout timeout in seconds
     * @return this builder
     */
    public Builder connectTimeout(int connectTimeout) {
      this.connectTimeout = connectTimeout;
      return this;
    }

    /**
     * Maximum cached buffer size. Driver default: null.
     *
     * @param maxCachedBufferSize max size
     * @return this builder
     */
    public Builder maxCachedBufferSize(int maxCachedBufferSize) {
      this.maxCachedBufferSize = maxCachedBufferSize;
      return this;
    }

    /**
     * Process JDBC escape sequences. Driver default: true.
     *
     * @param processEscapes true to process
     * @return this builder
     */
    public Builder processEscapes(boolean processEscapes) {
      this.processEscapes = processEscapes;
      return this;
    }

    /**
     * Statement cache size. Driver default: 0.
     *
     * @param statementCacheSize cache size
     * @return this builder
     */
    public Builder statementCacheSize(int statementCacheSize) {
      this.statementCacheSize = statementCacheSize;
      return this;
    }

    // ==================== LOB ====================

    /**
     * Use fetch size with LONG columns. Driver default: false.
     *
     * @param useFetchSizeWithLongColumn true to use
     * @return this builder
     */
    public Builder useFetchSizeWithLongColumn(boolean useFetchSizeWithLongColumn) {
      this.useFetchSizeWithLongColumn = useFetchSizeWithLongColumn;
      return this;
    }

    /**
     * LOB prefetch size. Driver default: 4000.
     *
     * @param lobPrefetchSize prefetch size
     * @return this builder
     */
    public Builder lobPrefetchSize(int lobPrefetchSize) {
      this.lobPrefetchSize = lobPrefetchSize;
      return this;
    }

    /**
     * Prefetch LOBs with row data. Driver default: false.
     *
     * @param prefetchLOBs true to prefetch
     * @return this builder
     */
    public Builder prefetchLOBs(boolean prefetchLOBs) {
      this.prefetchLOBs = prefetchLOBs;
      return this;
    }

    /**
     * LOB stream position standard compliance. Driver default: null.
     *
     * @param lobStreamPosStandard standard mode
     * @return this builder
     */
    public Builder lobStreamPosStandard(String lobStreamPosStandard) {
      this.lobStreamPosStandard = lobStreamPosStandard;
      return this;
    }

    /**
     * Clean up temporary BLOBs. Driver default: true.
     *
     * @param tempBlobCleanUp true to clean
     * @return this builder
     */
    public Builder tempBlobCleanUp(boolean tempBlobCleanUp) {
      this.tempBlobCleanUp = tempBlobCleanUp;
      return this;
    }

    /**
     * Clean up temporary CLOBs. Driver default: true.
     *
     * @param tempClobCleanUp true to clean
     * @return this builder
     */
    public Builder tempClobCleanUp(boolean tempClobCleanUp) {
      this.tempClobCleanUp = tempClobCleanUp;
      return this;
    }

    // ==================== BATCH ====================

    /**
     * Batch size. Driver default: 1.
     *
     * @param batchSize batch size
     * @return this builder
     */
    public Builder batchSize(int batchSize) {
      this.batchSize = batchSize;
      return this;
    }

    /**
     * Use batch multi-retrieve. Driver default: false.
     *
     * @param useBatchMultiRetrieve true to enable
     * @return this builder
     */
    public Builder useBatchMultiRetrieve(boolean useBatchMultiRetrieve) {
      this.useBatchMultiRetrieve = useBatchMultiRetrieve;
      return this;
    }

    /**
     * Enable batch updates. Driver default: false.
     *
     * @param enableBatchUpdates true to enable
     * @return this builder
     */
    public Builder enableBatchUpdates(boolean enableBatchUpdates) {
      this.enableBatchUpdates = enableBatchUpdates;
      return this;
    }

    // ==================== METADATA ====================

    /**
     * Include remarks in metadata. Driver default: false.
     *
     * @param remarksReporting true to include
     * @return this builder
     */
    public Builder remarksReporting(boolean remarksReporting) {
      this.remarksReporting = remarksReporting;
      return this;
    }

    /**
     * Include synonyms in metadata. Driver default: false.
     *
     * @param includeSynonyms true to include
     * @return this builder
     */
    public Builder includeSynonyms(boolean includeSynonyms) {
      this.includeSynonyms = includeSynonyms;
      return this;
    }

    /**
     * Restrict getTables metadata. Driver default: false.
     *
     * @param restrictGetTables true to restrict
     * @return this builder
     */
    public Builder restrictGetTables(boolean restrictGetTables) {
      this.restrictGetTables = restrictGetTables;
      return this;
    }

    /**
     * Accumulate batch results. Driver default: false.
     *
     * @param accumulateBatchResult true to accumulate
     * @return this builder
     */
    public Builder accumulateBatchResult(boolean accumulateBatchResult) {
      this.accumulateBatchResult = accumulateBatchResult;
      return this;
    }

    /**
     * Default NChar mode. Driver default: null.
     *
     * @param defaultNChar NChar mode
     * @return this builder
     */
    public Builder defaultNChar(String defaultNChar) {
      this.defaultNChar = defaultNChar;
      return this;
    }

    // ==================== NETWORK ====================

    /**
     * TCP no delay. Driver default: null.
     *
     * @param tcpNoDelay 1 to enable
     * @return this builder
     */
    public Builder tcpNoDelay(int tcpNoDelay) {
      this.tcpNoDelay = tcpNoDelay;
      return this;
    }

    /**
     * TCP keepalive. Driver default: null.
     *
     * @param keepAlive 1 to enable
     * @return this builder
     */
    public Builder keepAlive(int keepAlive) {
      this.keepAlive = keepAlive;
      return this;
    }

    /**
     * Send buffer size. Driver default: null.
     *
     * @param sendBufferSize buffer size
     * @return this builder
     */
    public Builder sendBufferSize(int sendBufferSize) {
      this.sendBufferSize = sendBufferSize;
      return this;
    }

    /**
     * Receive buffer size. Driver default: null.
     *
     * @param receiveBufferSize buffer size
     * @return this builder
     */
    public Builder receiveBufferSize(int receiveBufferSize) {
      this.receiveBufferSize = receiveBufferSize;
      return this;
    }

    /**
     * Allow thin network PM. Driver default: null.
     *
     * @param thinNetAllowPM true to allow
     * @return this builder
     */
    public Builder thinNetAllowPM(boolean thinNetAllowPM) {
      this.thinNetAllowPM = thinNetAllowPM;
      return this;
    }

    /**
     * Network protocol. Driver default: tcp.
     *
     * @param networkProtocol protocol name
     * @return this builder
     */
    public Builder networkProtocol(String networkProtocol) {
      this.networkProtocol = networkProtocol;
      return this;
    }

    // ==================== SSL/TLS ====================

    /**
     * SSL server certificate DN. Driver default: null.
     *
     * @param sslServerCertDN certificate DN
     * @return this builder
     */
    public Builder sslServerCertDN(String sslServerCertDN) {
      this.sslServerCertDN = sslServerCertDN;
      return this;
    }

    /**
     * Trust store path. Driver default: null.
     *
     * @param trustStore path to trust store
     * @return this builder
     */
    public Builder trustStore(String trustStore) {
      this.trustStore = trustStore;
      return this;
    }

    /**
     * Trust store password. Driver default: null.
     *
     * @param trustStorePassword password
     * @return this builder
     */
    public Builder trustStorePassword(String trustStorePassword) {
      this.trustStorePassword = trustStorePassword;
      return this;
    }

    /**
     * Trust store type. Driver default: JKS.
     *
     * @param trustStoreType store type
     * @return this builder
     */
    public Builder trustStoreType(String trustStoreType) {
      this.trustStoreType = trustStoreType;
      return this;
    }

    /**
     * Key store path. Driver default: null.
     *
     * @param keyStore path to key store
     * @return this builder
     */
    public Builder keyStore(String keyStore) {
      this.keyStore = keyStore;
      return this;
    }

    /**
     * Key store password. Driver default: null.
     *
     * @param keyStorePassword password
     * @return this builder
     */
    public Builder keyStorePassword(String keyStorePassword) {
      this.keyStorePassword = keyStorePassword;
      return this;
    }

    /**
     * Key store type. Driver default: JKS.
     *
     * @param keyStoreType store type
     * @return this builder
     */
    public Builder keyStoreType(String keyStoreType) {
      this.keyStoreType = keyStoreType;
      return this;
    }

    /**
     * Oracle wallet location. Driver default: null.
     *
     * @param walletLocation path to wallet
     * @return this builder
     */
    public Builder walletLocation(String walletLocation) {
      this.walletLocation = walletLocation;
      return this;
    }

    /**
     * Oracle wallet password. Driver default: null.
     *
     * @param walletPassword wallet password
     * @return this builder
     */
    public Builder walletPassword(String walletPassword) {
      this.walletPassword = walletPassword;
      return this;
    }

    /**
     * Match SSL server DN. Driver default: false.
     *
     * @param sslServerDNMatch true to match
     * @return this builder
     */
    public Builder sslServerDNMatch(boolean sslServerDNMatch) {
      this.sslServerDNMatch = sslServerDNMatch;
      return this;
    }

    // ==================== HIGH AVAILABILITY ====================

    /**
     * Enable Fast Connection Failover (FCF). Driver default: false.
     *
     * @param fastConnectionFailover true to enable
     * @return this builder
     */
    public Builder fastConnectionFailover(boolean fastConnectionFailover) {
      this.fastConnectionFailover = fastConnectionFailover;
      return this;
    }

    /**
     * ONS configuration. Driver default: null.
     *
     * @param onsConfiguration ONS config
     * @return this builder
     */
    public Builder onsConfiguration(int onsConfiguration) {
      this.onsConfiguration = onsConfiguration;
      return this;
    }

    /**
     * Connection retry count. Driver default: 0.
     *
     * @param retryCount retry count
     * @return this builder
     */
    public Builder retryCount(int retryCount) {
      this.retryCount = retryCount;
      return this;
    }

    /**
     * Connection retry delay in seconds. Driver default: 0.
     *
     * @param retryDelay delay in seconds
     * @return this builder
     */
    public Builder retryDelay(int retryDelay) {
      this.retryDelay = retryDelay;
      return this;
    }

    /**
     * Implicit connection timeout. Driver default: false.
     *
     * @param implicitConnectionTimeout true to enable
     * @return this builder
     */
    public Builder implicitConnectionTimeout(boolean implicitConnectionTimeout) {
      this.implicitConnectionTimeout = implicitConnectionTimeout;
      return this;
    }

    /**
     * Enable FAN. Driver default: null.
     *
     * @param fanEnabled "true" or "false"
     * @return this builder
     */
    public Builder fanEnabled(String fanEnabled) {
      this.fanEnabled = fanEnabled;
      return this;
    }

    /**
     * ONS wallet location. Driver default: null.
     *
     * @param onsWalletLocation wallet path
     * @return this builder
     */
    public Builder onsWalletLocation(String onsWalletLocation) {
      this.onsWalletLocation = onsWalletLocation;
      return this;
    }

    /**
     * ONS wallet password. Driver default: null.
     *
     * @param onsWalletPassword wallet password
     * @return this builder
     */
    public Builder onsWalletPassword(String onsWalletPassword) {
      this.onsWalletPassword = onsWalletPassword;
      return this;
    }

    /**
     * ONS nodes. Driver default: null.
     *
     * @param onsNodes true to enable
     * @return this builder
     */
    public Builder onsNodes(boolean onsNodes) {
      this.onsNodes = onsNodes;
      return this;
    }

    // ==================== TIMEZONE ====================

    /**
     * Session time zone. Driver default: null.
     *
     * @param sessionTimeZone timezone ID
     * @return this builder
     */
    public Builder sessionTimeZone(String sessionTimeZone) {
      this.sessionTimeZone = sessionTimeZone;
      return this;
    }

    /**
     * Convert NChar literals. Driver default: false.
     *
     * @param convertNCharLiterals true to convert
     * @return this builder
     */
    public Builder convertNCharLiterals(boolean convertNCharLiterals) {
      this.convertNCharLiterals = convertNCharLiterals;
      return this;
    }

    // ==================== TRACING ====================

    /**
     * Enable trace level. Driver default: false.
     *
     * @param traceLevel true to enable
     * @return this builder
     */
    public Builder traceLevel(boolean traceLevel) {
      this.traceLevel = traceLevel;
      return this;
    }

    /**
     * Trace file path. Driver default: null.
     *
     * @param traceFile file path
     * @return this builder
     */
    public Builder traceFile(String traceFile) {
      this.traceFile = traceFile;
      return this;
    }

    /**
     * Maximum trace file size. Driver default: null.
     *
     * @param traceFileSize file size
     * @return this builder
     */
    public Builder traceFileSize(int traceFileSize) {
      this.traceFileSize = traceFileSize;
      return this;
    }

    /**
     * Maximum trace files. Driver default: null.
     *
     * @param traceMaxFiles max files
     * @return this builder
     */
    public Builder traceMaxFiles(int traceMaxFiles) {
      this.traceMaxFiles = traceMaxFiles;
      return this;
    }

    /**
     * Enable log tracing. Driver default: false.
     *
     * @param logTraceEnabled true to enable
     * @return this builder
     */
    public Builder logTraceEnabled(boolean logTraceEnabled) {
      this.logTraceEnabled = logTraceEnabled;
      return this;
    }

    // ==================== PROXY ====================

    /**
     * Proxy client name. Driver default: null.
     *
     * @param proxyClientName client name
     * @return this builder
     */
    public Builder proxyClientName(String proxyClientName) {
      this.proxyClientName = proxyClientName;
      return this;
    }

    /**
     * Proxy client DN. Driver default: null.
     *
     * @param proxyClientDN client DN
     * @return this builder
     */
    public Builder proxyClientDN(String proxyClientDN) {
      this.proxyClientDN = proxyClientDN;
      return this;
    }

    /**
     * Proxy roles. Driver default: null.
     *
     * @param proxyRoles comma-separated roles
     * @return this builder
     */
    public Builder proxyRoles(String proxyRoles) {
      this.proxyRoles = proxyRoles;
      return this;
    }

    /**
     * Proxy password. Driver default: null.
     *
     * @param proxyPassword password
     * @return this builder
     */
    public Builder proxyPassword(String proxyPassword) {
      this.proxyPassword = proxyPassword;
      return this;
    }

    // ==================== JAVA ====================

    /**
     * Thread pool size. Driver default: null.
     *
     * @param threadPoolSize pool size
     * @return this builder
     */
    public Builder threadPoolSize(int threadPoolSize) {
      this.threadPoolSize = threadPoolSize;
      return this;
    }

    /**
     * Java object type class. Driver default: null.
     *
     * @param javaObjectTypeClass class name
     * @return this builder
     */
    public Builder javaObjectTypeClass(String javaObjectTypeClass) {
      this.javaObjectTypeClass = javaObjectTypeClass;
      return this;
    }

    /**
     * Disable define column type. Driver default: false.
     *
     * @param disableDefineColumnType true to disable
     * @return this builder
     */
    public Builder disableDefineColumnType(boolean disableDefineColumnType) {
      this.disableDefineColumnType = disableDefineColumnType;
      return this;
    }

    /**
     * Allow NChar literal. Driver default: false.
     *
     * @param allowNCharLiteral true to allow
     * @return this builder
     */
    public Builder allowNCharLiteral(boolean allowNCharLiteral) {
      this.allowNCharLiteral = allowNCharLiteral;
      return this;
    }

    /**
     * Create descriptor using current schema. Driver default: false.
     *
     * @param createDescriptorUseCurrentSchemaForSchemaName true to use
     * @return this builder
     */
    public Builder createDescriptorUseCurrentSchemaForSchemaName(
        boolean createDescriptorUseCurrentSchemaForSchemaName) {
      this.createDescriptorUseCurrentSchemaForSchemaName =
          createDescriptorUseCurrentSchemaForSchemaName;
      return this;
    }

    // ==================== STATEMENT ====================

    /**
     * Default statement mode is non-blocking. Driver default: false.
     *
     * @param defaultStatementModeIsNonBlocking true for non-blocking
     * @return this builder
     */
    public Builder defaultStatementModeIsNonBlocking(boolean defaultStatementModeIsNonBlocking) {
      this.defaultStatementModeIsNonBlocking = defaultStatementModeIsNonBlocking;
      return this;
    }

    /**
     * Default execute async. Driver default: false.
     *
     * @param defaultExecuteAsync true for async
     * @return this builder
     */
    public Builder defaultExecuteAsync(boolean defaultExecuteAsync) {
      this.defaultExecuteAsync = defaultExecuteAsync;
      return this;
    }

    /**
     * Stream chunk size. Driver default: false.
     *
     * @param streamChunkSize true to enable
     * @return this builder
     */
    public Builder streamChunkSize(boolean streamChunkSize) {
      this.streamChunkSize = streamChunkSize;
      return this;
    }

    // ==================== ORACLE-SPECIFIC ====================

    /**
     * Restricted list. Driver default: false.
     *
     * @param restrictedList true to restrict
     * @return this builder
     */
    public Builder restrictedList(boolean restrictedList) {
      this.restrictedList = restrictedList;
      return this;
    }

    /**
     * SQL*CL mode. Driver default: false.
     *
     * @param sqlCl true for SQL*CL mode
     * @return this builder
     */
    public Builder sqlCl(boolean sqlCl) {
      this.sqlCl = sqlCl;
      return this;
    }

    /**
     * Report remarks. Driver default: false.
     *
     * @param reportRemarks true to report
     * @return this builder
     */
    public Builder reportRemarks(boolean reportRemarks) {
      this.reportRemarks = reportRemarks;
      return this;
    }

    /**
     * Get PL/SQL errors from server. Driver default: false.
     *
     * @param getPlSqlErrorFromServerOnProcedureCall true to get errors
     * @return this builder
     */
    public Builder getPlSqlErrorFromServerOnProcedureCall(
        boolean getPlSqlErrorFromServerOnProcedureCall) {
      this.getPlSqlErrorFromServerOnProcedureCall = getPlSqlErrorFromServerOnProcedureCall;
      return this;
    }

    /**
     * PL/SQL compiler warnings. Driver default: false.
     *
     * @param plsqlCompilerWarnings true to enable
     * @return this builder
     */
    public Builder plsqlCompilerWarnings(boolean plsqlCompilerWarnings) {
      this.plsqlCompilerWarnings = plsqlCompilerWarnings;
      return this;
    }

    /**
     * Edition name. Driver default: null.
     *
     * @param editionName edition
     * @return this builder
     */
    public Builder editionName(String editionName) {
      this.editionName = editionName;
      return this;
    }

    /**
     * Internal logon (SYSDBA/SYSOPER). Driver default: false.
     *
     * @param internalLogon true for internal
     * @return this builder
     */
    public Builder internalLogon(boolean internalLogon) {
      this.internalLogon = internalLogon;
      return this;
    }

    /**
     * Connection class name. Driver default: null.
     *
     * @param connectionClassName class name
     * @return this builder
     */
    public Builder connectionClassName(String connectionClassName) {
      this.connectionClassName = connectionClassName;
      return this;
    }

    /**
     * Enable scrollable result sets. Driver default: true.
     *
     * @param enableScrollableResultSet true to enable
     * @return this builder
     */
    public Builder enableScrollableResultSet(boolean enableScrollableResultSet) {
      this.enableScrollableResultSet = enableScrollableResultSet;
      return this;
    }

    /**
     * Enable read-only result sets. Driver default: true.
     *
     * @param enableReadOnlyResultSet true to enable
     * @return this builder
     */
    public Builder enableReadOnlyResultSet(boolean enableReadOnlyResultSet) {
      this.enableReadOnlyResultSet = enableReadOnlyResultSet;
      return this;
    }

    /**
     * Cancel query on close. Driver default: true.
     *
     * @param enableCancelQueryOnClose true to enable
     * @return this builder
     */
    public Builder enableCancelQueryOnClose(boolean enableCancelQueryOnClose) {
      this.enableCancelQueryOnClose = enableCancelQueryOnClose;
      return this;
    }

    // ==================== SHARDING ====================

    /**
     * Sharding key. Driver default: null.
     *
     * @param shardingKey sharding key value
     * @return this builder
     */
    public Builder shardingKey(String shardingKey) {
      this.shardingKey = shardingKey;
      return this;
    }

    /**
     * Super sharding key. Driver default: null.
     *
     * @param superShardingKey super sharding key value
     * @return this builder
     */
    public Builder superShardingKey(String superShardingKey) {
      this.superShardingKey = superShardingKey;
      return this;
    }

    // ==================== XA ====================

    /**
     * Enable XA recovery. Driver default: false.
     *
     * @param xaRecoveryEnabled true to enable
     * @return this builder
     */
    public Builder xaRecoveryEnabled(boolean xaRecoveryEnabled) {
      this.xaRecoveryEnabled = xaRecoveryEnabled;
      return this;
    }

    /**
     * XA tightly coupled. Driver default: false.
     *
     * @param xaTightlyCouple true for tight coupling
     * @return this builder
     */
    public Builder xaTightlyCouple(boolean xaTightlyCouple) {
      this.xaTightlyCouple = xaTightlyCouple;
      return this;
    }

    // ==================== CONNECTION VALIDATION ====================

    /**
     * Check connection on borrow. Driver default: false.
     *
     * @param checkConnectionOnBorrow true to check
     * @return this builder
     */
    public Builder checkConnectionOnBorrow(boolean checkConnectionOnBorrow) {
      this.checkConnectionOnBorrow = checkConnectionOnBorrow;
      return this;
    }

    /**
     * Validation SQL. Driver default: null.
     *
     * @param validationSQL SQL to execute
     * @return this builder
     */
    public Builder validationSQL(String validationSQL) {
      this.validationSQL = validationSQL;
      return this;
    }

    /**
     * Validation timeout in seconds. Driver default: null.
     *
     * @param validationTimeout timeout
     * @return this builder
     */
    public Builder validationTimeout(int validationTimeout) {
      this.validationTimeout = validationTimeout;
      return this;
    }

    /**
     * Seconds to trust idle connection. Driver default: 0.
     *
     * @param secondsToTrustIdleConnection seconds
     * @return this builder
     */
    public Builder secondsToTrustIdleConnection(int secondsToTrustIdleConnection) {
      this.secondsToTrustIdleConnection = secondsToTrustIdleConnection;
      return this;
    }

    /**
     * Inactivity timeout in seconds. Driver default: 0.
     *
     * @param inactivityTimeout timeout
     * @return this builder
     */
    public Builder inactivityTimeout(int inactivityTimeout) {
      this.inactivityTimeout = inactivityTimeout;
      return this;
    }

    /**
     * Abandoned connection timeout in seconds. Driver default: 0.
     *
     * @param abandonedConnectionTimeout timeout
     * @return this builder
     */
    public Builder abandonedConnectionTimeout(int abandonedConnectionTimeout) {
      this.abandonedConnectionTimeout = abandonedConnectionTimeout;
      return this;
    }

    /**
     * Time to live connection timeout in seconds. Driver default: 0.
     *
     * @param timeToLiveConnectionTimeout timeout
     * @return this builder
     */
    public Builder timeToLiveConnectionTimeout(int timeToLiveConnectionTimeout) {
      this.timeToLiveConnectionTimeout = timeToLiveConnectionTimeout;
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
     * Build the OracleConfig.
     *
     * @return immutable OracleConfig
     */
    public OracleConfig build() {
      return new OracleConfig(this);
    }
  }
}
