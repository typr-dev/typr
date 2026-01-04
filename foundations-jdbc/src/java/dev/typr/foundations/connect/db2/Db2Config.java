package dev.typr.foundations.connect.db2;

import dev.typr.foundations.connect.DatabaseConfig;
import dev.typr.foundations.connect.DatabaseKind;
import java.util.HashMap;
import java.util.Map;

/**
 * DB2 database configuration with typed builder methods for all documented JDBC driver properties.
 *
 * <p>Properties are based on the IBM Data Server Driver for JDBC documentation.
 *
 * @see <a
 *     href="https://www.ibm.com/docs/en/db2/11.5?topic=information-properties-data-server-driver-jdbc-sqlj">IBM
 *     JDBC Documentation</a>
 */
public final class Db2Config implements DatabaseConfig {

  private final String host;
  private final int port;
  private final String database;
  private final String username;
  private final String password;

  // Connection properties
  private final String currentSchema;
  private final String currentSQLID;
  private final Integer loginTimeout;
  private final Integer commandTimeout;
  private final String clientApplicationInformation;
  private final String clientAccountingInformation;
  private final String clientProgramId;
  private final String clientUser;
  private final String clientWorkstation;

  // Performance properties
  private final Integer blockingReadConnectionTimeout;
  private final Boolean fullyMaterializeLobData;
  private final Boolean fullyMaterializeInputStreams;
  private final Boolean progressiveStreaming;
  private final Integer fetchSize;
  private final Integer queryDataSize;
  private final Boolean deferPrepares;
  private final Boolean enableNamedParameterMarkers;
  private final Boolean enableSeamlessFailover;
  private final Integer keepDynamic;
  private final Boolean resultSetHoldability;
  private final Integer queryTimeoutInterruptProcessingMode;
  private final Boolean useJDBC4ColumnNameAndLabelSemantics;

  // Error handling properties
  private final Boolean retrieveMessagesFromServerOnGetMessage;
  private final Integer readTimeout;
  private final Boolean atomicMultiRowInsert;
  private final Boolean returnAlias;

  // SSL/TLS properties
  private final String sslConnection;
  private final String sslTrustStoreLocation;
  private final String sslTrustStorePassword;
  private final String sslKeyStoreLocation;
  private final String sslKeyStorePassword;
  private final String sslCipherSuites;

  // Security properties
  private final Integer securityMechanism;
  private final String kerberosServerPrincipal;
  private final String gssCredential;
  private final Boolean encryptionAlgorithm;
  private final String pkList;
  private final String pluginName;
  private final Boolean sendDataAsIs;

  // LOB properties
  private final Integer streamBufferSize;
  private final Boolean fullyMaterializeBlobData;
  private final Boolean fullyMaterializeClobData;
  private final Integer maxRetriesForClientReroute;
  private final Integer retryIntervalForClientReroute;

  // Statement properties
  private final Boolean allowNextOnExhaustedResultSet;
  private final String cursorSensitivity;
  private final Integer cursorHold;
  private final Boolean emulateParameterMetaDataForZCalls;
  private final Integer resultSetHoldabilityForCatalogQueries;
  private final Integer queryCloseImplicit;
  private final Boolean sendCharInputsUTF8;
  private final String timestampFormat;
  private final String timestampOutputType;
  private final String dateFormat;
  private final String timeFormat;

  // Tracing/Logging properties
  private final Boolean traceFile;
  private final Integer traceLevel;
  private final Boolean traceDirectory;
  private final Boolean logWriter;

  // XA properties
  private final Integer xaNetworkOptimization;
  private final Boolean downgradeHoldCursorsUnderXa;

  // Compatibility properties
  private final Boolean jdbcCollection;
  private final String currentPackagePath;
  private final String currentPackageSet;
  private final Boolean enableClientAffinitiesList;
  private final String clientRerouteAlternateServerName;
  private final String clientRerouteAlternatePortNumber;

  // Advanced connection properties
  private final Integer memberConnectTimeout;
  private final String sysSchema;
  private final Boolean affinityFailbackInterval;
  private final Boolean enableSysplexWLB;
  private final Integer maxTransportObjects;
  private final String databaseName;
  private final Boolean decimalSeparator;
  private final Integer decimalStringFormat;
  private final Integer clientDebugInfo;

  // Escape hatch
  private final Map<String, String> extraProperties;

  private Db2Config(Builder b) {
    this.host = b.host;
    this.port = b.port;
    this.database = b.database;
    this.username = b.username;
    this.password = b.password;

    // Connection
    this.currentSchema = b.currentSchema;
    this.currentSQLID = b.currentSQLID;
    this.loginTimeout = b.loginTimeout;
    this.commandTimeout = b.commandTimeout;
    this.clientApplicationInformation = b.clientApplicationInformation;
    this.clientAccountingInformation = b.clientAccountingInformation;
    this.clientProgramId = b.clientProgramId;
    this.clientUser = b.clientUser;
    this.clientWorkstation = b.clientWorkstation;

    // Performance
    this.blockingReadConnectionTimeout = b.blockingReadConnectionTimeout;
    this.fullyMaterializeLobData = b.fullyMaterializeLobData;
    this.fullyMaterializeInputStreams = b.fullyMaterializeInputStreams;
    this.progressiveStreaming = b.progressiveStreaming;
    this.fetchSize = b.fetchSize;
    this.queryDataSize = b.queryDataSize;
    this.deferPrepares = b.deferPrepares;
    this.enableNamedParameterMarkers = b.enableNamedParameterMarkers;
    this.enableSeamlessFailover = b.enableSeamlessFailover;
    this.keepDynamic = b.keepDynamic;
    this.resultSetHoldability = b.resultSetHoldability;
    this.queryTimeoutInterruptProcessingMode = b.queryTimeoutInterruptProcessingMode;
    this.useJDBC4ColumnNameAndLabelSemantics = b.useJDBC4ColumnNameAndLabelSemantics;

    // Error handling
    this.retrieveMessagesFromServerOnGetMessage = b.retrieveMessagesFromServerOnGetMessage;
    this.readTimeout = b.readTimeout;
    this.atomicMultiRowInsert = b.atomicMultiRowInsert;
    this.returnAlias = b.returnAlias;

    // SSL/TLS
    this.sslConnection = b.sslConnection;
    this.sslTrustStoreLocation = b.sslTrustStoreLocation;
    this.sslTrustStorePassword = b.sslTrustStorePassword;
    this.sslKeyStoreLocation = b.sslKeyStoreLocation;
    this.sslKeyStorePassword = b.sslKeyStorePassword;
    this.sslCipherSuites = b.sslCipherSuites;

    // Security
    this.securityMechanism = b.securityMechanism;
    this.kerberosServerPrincipal = b.kerberosServerPrincipal;
    this.gssCredential = b.gssCredential;
    this.encryptionAlgorithm = b.encryptionAlgorithm;
    this.pkList = b.pkList;
    this.pluginName = b.pluginName;
    this.sendDataAsIs = b.sendDataAsIs;

    // LOB
    this.streamBufferSize = b.streamBufferSize;
    this.fullyMaterializeBlobData = b.fullyMaterializeBlobData;
    this.fullyMaterializeClobData = b.fullyMaterializeClobData;
    this.maxRetriesForClientReroute = b.maxRetriesForClientReroute;
    this.retryIntervalForClientReroute = b.retryIntervalForClientReroute;

    // Statement
    this.allowNextOnExhaustedResultSet = b.allowNextOnExhaustedResultSet;
    this.cursorSensitivity = b.cursorSensitivity;
    this.cursorHold = b.cursorHold;
    this.emulateParameterMetaDataForZCalls = b.emulateParameterMetaDataForZCalls;
    this.resultSetHoldabilityForCatalogQueries = b.resultSetHoldabilityForCatalogQueries;
    this.queryCloseImplicit = b.queryCloseImplicit;
    this.sendCharInputsUTF8 = b.sendCharInputsUTF8;
    this.timestampFormat = b.timestampFormat;
    this.timestampOutputType = b.timestampOutputType;
    this.dateFormat = b.dateFormat;
    this.timeFormat = b.timeFormat;

    // Tracing
    this.traceFile = b.traceFile;
    this.traceLevel = b.traceLevel;
    this.traceDirectory = b.traceDirectory;
    this.logWriter = b.logWriter;

    // XA
    this.xaNetworkOptimization = b.xaNetworkOptimization;
    this.downgradeHoldCursorsUnderXa = b.downgradeHoldCursorsUnderXa;

    // Compatibility
    this.jdbcCollection = b.jdbcCollection;
    this.currentPackagePath = b.currentPackagePath;
    this.currentPackageSet = b.currentPackageSet;
    this.enableClientAffinitiesList = b.enableClientAffinitiesList;
    this.clientRerouteAlternateServerName = b.clientRerouteAlternateServerName;
    this.clientRerouteAlternatePortNumber = b.clientRerouteAlternatePortNumber;

    // Advanced connection
    this.memberConnectTimeout = b.memberConnectTimeout;
    this.sysSchema = b.sysSchema;
    this.affinityFailbackInterval = b.affinityFailbackInterval;
    this.enableSysplexWLB = b.enableSysplexWLB;
    this.maxTransportObjects = b.maxTransportObjects;
    this.databaseName = b.databaseName;
    this.decimalSeparator = b.decimalSeparator;
    this.decimalStringFormat = b.decimalStringFormat;
    this.clientDebugInfo = b.clientDebugInfo;

    this.extraProperties = Map.copyOf(b.extraProperties);
  }

  /**
   * Create a new builder with required connection parameters.
   *
   * @param host DB2 server hostname
   * @param port DB2 server port (typically 50000)
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
    return "jdbc:db2://" + host + ":" + port + "/" + database;
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
    return DatabaseKind.DB2;
  }

  @Override
  public Map<String, String> driverProperties() {
    Map<String, String> props = new HashMap<>();

    // Connection
    if (currentSchema != null) props.put("currentSchema", currentSchema);
    if (currentSQLID != null) props.put("currentSQLID", currentSQLID);
    if (loginTimeout != null) props.put("loginTimeout", loginTimeout.toString());
    if (commandTimeout != null) props.put("commandTimeout", commandTimeout.toString());
    if (clientApplicationInformation != null)
      props.put("clientApplicationInformation", clientApplicationInformation);
    if (clientAccountingInformation != null)
      props.put("clientAccountingInformation", clientAccountingInformation);
    if (clientProgramId != null) props.put("clientProgramId", clientProgramId);
    if (clientUser != null) props.put("clientUser", clientUser);
    if (clientWorkstation != null) props.put("clientWorkstation", clientWorkstation);

    // Performance
    if (blockingReadConnectionTimeout != null)
      props.put("blockingReadConnectionTimeout", blockingReadConnectionTimeout.toString());
    if (fullyMaterializeLobData != null)
      props.put("fullyMaterializeLobData", fullyMaterializeLobData.toString());
    if (fullyMaterializeInputStreams != null)
      props.put("fullyMaterializeInputStreams", fullyMaterializeInputStreams.toString());
    if (progressiveStreaming != null)
      props.put("progressiveStreaming", progressiveStreaming.toString());
    if (fetchSize != null) props.put("fetchSize", fetchSize.toString());
    if (queryDataSize != null) props.put("queryDataSize", queryDataSize.toString());
    if (deferPrepares != null) props.put("deferPrepares", deferPrepares.toString());
    if (enableNamedParameterMarkers != null)
      props.put("enableNamedParameterMarkers", enableNamedParameterMarkers.toString());
    if (enableSeamlessFailover != null)
      props.put("enableSeamlessFailover", enableSeamlessFailover.toString());
    if (keepDynamic != null) props.put("keepDynamic", keepDynamic.toString());
    if (resultSetHoldability != null)
      props.put("resultSetHoldability", resultSetHoldability.toString());
    if (queryTimeoutInterruptProcessingMode != null)
      props.put(
          "queryTimeoutInterruptProcessingMode", queryTimeoutInterruptProcessingMode.toString());
    if (useJDBC4ColumnNameAndLabelSemantics != null)
      props.put(
          "useJDBC4ColumnNameAndLabelSemantics", useJDBC4ColumnNameAndLabelSemantics.toString());

    // Error handling
    if (retrieveMessagesFromServerOnGetMessage != null)
      props.put(
          "retrieveMessagesFromServerOnGetMessage",
          retrieveMessagesFromServerOnGetMessage.toString());
    if (readTimeout != null) props.put("readTimeout", readTimeout.toString());
    if (atomicMultiRowInsert != null)
      props.put("atomicMultiRowInsert", atomicMultiRowInsert.toString());
    if (returnAlias != null) props.put("returnAlias", returnAlias.toString());

    // SSL/TLS
    if (sslConnection != null) props.put("sslConnection", sslConnection);
    if (sslTrustStoreLocation != null) props.put("sslTrustStoreLocation", sslTrustStoreLocation);
    if (sslTrustStorePassword != null) props.put("sslTrustStorePassword", sslTrustStorePassword);
    if (sslKeyStoreLocation != null) props.put("sslKeyStoreLocation", sslKeyStoreLocation);
    if (sslKeyStorePassword != null) props.put("sslKeyStorePassword", sslKeyStorePassword);
    if (sslCipherSuites != null) props.put("sslCipherSuites", sslCipherSuites);

    // Security
    if (securityMechanism != null) props.put("securityMechanism", securityMechanism.toString());
    if (kerberosServerPrincipal != null)
      props.put("kerberosServerPrincipal", kerberosServerPrincipal);
    if (gssCredential != null) props.put("gssCredential", gssCredential);
    if (encryptionAlgorithm != null)
      props.put("encryptionAlgorithm", encryptionAlgorithm.toString());
    if (pkList != null) props.put("pkList", pkList);
    if (pluginName != null) props.put("pluginName", pluginName);
    if (sendDataAsIs != null) props.put("sendDataAsIs", sendDataAsIs.toString());

    // LOB
    if (streamBufferSize != null) props.put("streamBufferSize", streamBufferSize.toString());
    if (fullyMaterializeBlobData != null)
      props.put("fullyMaterializeBlobData", fullyMaterializeBlobData.toString());
    if (fullyMaterializeClobData != null)
      props.put("fullyMaterializeClobData", fullyMaterializeClobData.toString());
    if (maxRetriesForClientReroute != null)
      props.put("maxRetriesForClientReroute", maxRetriesForClientReroute.toString());
    if (retryIntervalForClientReroute != null)
      props.put("retryIntervalForClientReroute", retryIntervalForClientReroute.toString());

    // Statement
    if (allowNextOnExhaustedResultSet != null)
      props.put("allowNextOnExhaustedResultSet", allowNextOnExhaustedResultSet.toString());
    if (cursorSensitivity != null) props.put("cursorSensitivity", cursorSensitivity);
    if (cursorHold != null) props.put("cursorHold", cursorHold.toString());
    if (emulateParameterMetaDataForZCalls != null)
      props.put("emulateParameterMetaDataForZCalls", emulateParameterMetaDataForZCalls.toString());
    if (resultSetHoldabilityForCatalogQueries != null)
      props.put(
          "resultSetHoldabilityForCatalogQueries",
          resultSetHoldabilityForCatalogQueries.toString());
    if (queryCloseImplicit != null) props.put("queryCloseImplicit", queryCloseImplicit.toString());
    if (sendCharInputsUTF8 != null) props.put("sendCharInputsUTF8", sendCharInputsUTF8.toString());
    if (timestampFormat != null) props.put("timestampFormat", timestampFormat);
    if (timestampOutputType != null) props.put("timestampOutputType", timestampOutputType);
    if (dateFormat != null) props.put("dateFormat", dateFormat);
    if (timeFormat != null) props.put("timeFormat", timeFormat);

    // Tracing
    if (traceFile != null) props.put("traceFile", traceFile.toString());
    if (traceLevel != null) props.put("traceLevel", traceLevel.toString());
    if (traceDirectory != null) props.put("traceDirectory", traceDirectory.toString());
    if (logWriter != null) props.put("logWriter", logWriter.toString());

    // XA
    if (xaNetworkOptimization != null)
      props.put("xaNetworkOptimization", xaNetworkOptimization.toString());
    if (downgradeHoldCursorsUnderXa != null)
      props.put("downgradeHoldCursorsUnderXa", downgradeHoldCursorsUnderXa.toString());

    // Compatibility
    if (jdbcCollection != null) props.put("jdbcCollection", jdbcCollection.toString());
    if (currentPackagePath != null) props.put("currentPackagePath", currentPackagePath);
    if (currentPackageSet != null) props.put("currentPackageSet", currentPackageSet);
    if (enableClientAffinitiesList != null)
      props.put("enableClientAffinitiesList", enableClientAffinitiesList.toString());
    if (clientRerouteAlternateServerName != null)
      props.put("clientRerouteAlternateServerName", clientRerouteAlternateServerName);
    if (clientRerouteAlternatePortNumber != null)
      props.put("clientRerouteAlternatePortNumber", clientRerouteAlternatePortNumber);

    // Advanced connection
    if (memberConnectTimeout != null)
      props.put("memberConnectTimeout", memberConnectTimeout.toString());
    if (sysSchema != null) props.put("sysSchema", sysSchema);
    if (affinityFailbackInterval != null)
      props.put("affinityFailbackInterval", affinityFailbackInterval.toString());
    if (enableSysplexWLB != null) props.put("enableSysplexWLB", enableSysplexWLB.toString());
    if (maxTransportObjects != null)
      props.put("maxTransportObjects", maxTransportObjects.toString());
    if (databaseName != null) props.put("databaseName", databaseName);
    if (decimalSeparator != null) props.put("decimalSeparator", decimalSeparator.toString());
    if (decimalStringFormat != null)
      props.put("decimalStringFormat", decimalStringFormat.toString());
    if (clientDebugInfo != null) props.put("clientDebugInfo", clientDebugInfo.toString());

    props.putAll(extraProperties);
    return props;
  }

  /** Builder for Db2Config with typed methods for all JDBC driver properties. */
  public static final class Builder {
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;

    // Connection
    private String currentSchema;
    private String currentSQLID;
    private Integer loginTimeout;
    private Integer commandTimeout;
    private String clientApplicationInformation;
    private String clientAccountingInformation;
    private String clientProgramId;
    private String clientUser;
    private String clientWorkstation;

    // Performance
    private Integer blockingReadConnectionTimeout;
    private Boolean fullyMaterializeLobData;
    private Boolean fullyMaterializeInputStreams;
    private Boolean progressiveStreaming;
    private Integer fetchSize;
    private Integer queryDataSize;
    private Boolean deferPrepares;
    private Boolean enableNamedParameterMarkers;
    private Boolean enableSeamlessFailover;
    private Integer keepDynamic;
    private Boolean resultSetHoldability;
    private Integer queryTimeoutInterruptProcessingMode;
    private Boolean useJDBC4ColumnNameAndLabelSemantics;

    // Error handling
    private Boolean retrieveMessagesFromServerOnGetMessage;
    private Integer readTimeout;
    private Boolean atomicMultiRowInsert;
    private Boolean returnAlias;

    // SSL/TLS
    private String sslConnection;
    private String sslTrustStoreLocation;
    private String sslTrustStorePassword;
    private String sslKeyStoreLocation;
    private String sslKeyStorePassword;
    private String sslCipherSuites;

    // Security
    private Integer securityMechanism;
    private String kerberosServerPrincipal;
    private String gssCredential;
    private Boolean encryptionAlgorithm;
    private String pkList;
    private String pluginName;
    private Boolean sendDataAsIs;

    // LOB
    private Integer streamBufferSize;
    private Boolean fullyMaterializeBlobData;
    private Boolean fullyMaterializeClobData;
    private Integer maxRetriesForClientReroute;
    private Integer retryIntervalForClientReroute;

    // Statement
    private Boolean allowNextOnExhaustedResultSet;
    private String cursorSensitivity;
    private Integer cursorHold;
    private Boolean emulateParameterMetaDataForZCalls;
    private Integer resultSetHoldabilityForCatalogQueries;
    private Integer queryCloseImplicit;
    private Boolean sendCharInputsUTF8;
    private String timestampFormat;
    private String timestampOutputType;
    private String dateFormat;
    private String timeFormat;

    // Tracing
    private Boolean traceFile;
    private Integer traceLevel;
    private Boolean traceDirectory;
    private Boolean logWriter;

    // XA
    private Integer xaNetworkOptimization;
    private Boolean downgradeHoldCursorsUnderXa;

    // Compatibility
    private Boolean jdbcCollection;
    private String currentPackagePath;
    private String currentPackageSet;
    private Boolean enableClientAffinitiesList;
    private String clientRerouteAlternateServerName;
    private String clientRerouteAlternatePortNumber;

    // Advanced connection
    private Integer memberConnectTimeout;
    private String sysSchema;
    private Boolean affinityFailbackInterval;
    private Boolean enableSysplexWLB;
    private Integer maxTransportObjects;
    private String databaseName;
    private Boolean decimalSeparator;
    private Integer decimalStringFormat;
    private Integer clientDebugInfo;

    private final Map<String, String> extraProperties = new HashMap<>();

    private Builder(String host, int port, String database, String username, String password) {
      this.host = host;
      this.port = port;
      this.database = database;
      this.username = username;
      this.password = password;

      // OUR DEFAULTS (better than driver defaults)
      this.retrieveMessagesFromServerOnGetMessage = true; // Gets full error messages from server
    }

    // ==================== CONNECTION ====================

    /**
     * Current schema for unqualified names. Driver default: null.
     *
     * @param currentSchema schema name
     * @return this builder
     */
    public Builder currentSchema(String currentSchema) {
      this.currentSchema = currentSchema;
      return this;
    }

    /**
     * Current SQL ID for z/OS. Driver default: null.
     *
     * @param currentSQLID SQL ID
     * @return this builder
     */
    public Builder currentSQLID(String currentSQLID) {
      this.currentSQLID = currentSQLID;
      return this;
    }

    /**
     * Login timeout in seconds. Driver default: 0 (no timeout).
     *
     * @param loginTimeout timeout in seconds
     * @return this builder
     */
    public Builder loginTimeout(int loginTimeout) {
      this.loginTimeout = loginTimeout;
      return this;
    }

    /**
     * Command timeout in seconds. Driver default: 0 (no timeout).
     *
     * @param commandTimeout timeout in seconds
     * @return this builder
     */
    public Builder commandTimeout(int commandTimeout) {
      this.commandTimeout = commandTimeout;
      return this;
    }

    /**
     * Client application information for monitoring. Driver default: null.
     *
     * @param clientApplicationInformation application info
     * @return this builder
     */
    public Builder clientApplicationInformation(String clientApplicationInformation) {
      this.clientApplicationInformation = clientApplicationInformation;
      return this;
    }

    /**
     * Client accounting information. Driver default: null.
     *
     * @param clientAccountingInformation accounting info
     * @return this builder
     */
    public Builder clientAccountingInformation(String clientAccountingInformation) {
      this.clientAccountingInformation = clientAccountingInformation;
      return this;
    }

    /**
     * Client program ID. Driver default: null.
     *
     * @param clientProgramId program ID
     * @return this builder
     */
    public Builder clientProgramId(String clientProgramId) {
      this.clientProgramId = clientProgramId;
      return this;
    }

    /**
     * Client user for monitoring. Driver default: null.
     *
     * @param clientUser user name
     * @return this builder
     */
    public Builder clientUser(String clientUser) {
      this.clientUser = clientUser;
      return this;
    }

    /**
     * Client workstation for monitoring. Driver default: null.
     *
     * @param clientWorkstation workstation name
     * @return this builder
     */
    public Builder clientWorkstation(String clientWorkstation) {
      this.clientWorkstation = clientWorkstation;
      return this;
    }

    // ==================== PERFORMANCE ====================

    /**
     * Blocking read timeout in seconds. Driver default: 0 (no timeout).
     *
     * @param blockingReadConnectionTimeout timeout in seconds
     * @return this builder
     */
    public Builder blockingReadConnectionTimeout(int blockingReadConnectionTimeout) {
      this.blockingReadConnectionTimeout = blockingReadConnectionTimeout;
      return this;
    }

    /**
     * Fully materialize LOB data. Driver default: true.
     *
     * @param fullyMaterializeLobData true to materialize
     * @return this builder
     */
    public Builder fullyMaterializeLobData(boolean fullyMaterializeLobData) {
      this.fullyMaterializeLobData = fullyMaterializeLobData;
      return this;
    }

    /**
     * Fully materialize input streams. Driver default: true.
     *
     * @param fullyMaterializeInputStreams true to materialize
     * @return this builder
     */
    public Builder fullyMaterializeInputStreams(boolean fullyMaterializeInputStreams) {
      this.fullyMaterializeInputStreams = fullyMaterializeInputStreams;
      return this;
    }

    /**
     * Enable progressive streaming for LOBs. Driver default: true.
     *
     * @param progressiveStreaming true to enable
     * @return this builder
     */
    public Builder progressiveStreaming(boolean progressiveStreaming) {
      this.progressiveStreaming = progressiveStreaming;
      return this;
    }

    /**
     * Default fetch size. Driver default: 64.
     *
     * @param fetchSize fetch size
     * @return this builder
     */
    public Builder fetchSize(int fetchSize) {
      this.fetchSize = fetchSize;
      return this;
    }

    /**
     * Query data size in bytes. Driver default: 32767.
     *
     * @param queryDataSize data size
     * @return this builder
     */
    public Builder queryDataSize(int queryDataSize) {
      this.queryDataSize = queryDataSize;
      return this;
    }

    /**
     * Defer statement preparation. Driver default: true.
     *
     * @param deferPrepares true to defer
     * @return this builder
     */
    public Builder deferPrepares(boolean deferPrepares) {
      this.deferPrepares = deferPrepares;
      return this;
    }

    /**
     * Enable named parameter markers. Driver default: false.
     *
     * @param enableNamedParameterMarkers true to enable
     * @return this builder
     */
    public Builder enableNamedParameterMarkers(boolean enableNamedParameterMarkers) {
      this.enableNamedParameterMarkers = enableNamedParameterMarkers;
      return this;
    }

    /**
     * Enable seamless failover. Driver default: false.
     *
     * @param enableSeamlessFailover true to enable
     * @return this builder
     */
    public Builder enableSeamlessFailover(boolean enableSeamlessFailover) {
      this.enableSeamlessFailover = enableSeamlessFailover;
      return this;
    }

    /**
     * Keep dynamic SQL cached. Driver default: 0.
     *
     * @param keepDynamic 1 to keep
     * @return this builder
     */
    public Builder keepDynamic(int keepDynamic) {
      this.keepDynamic = keepDynamic;
      return this;
    }

    /**
     * Result set holdability. Driver default: true.
     *
     * @param resultSetHoldability true for holdable cursors
     * @return this builder
     */
    public Builder resultSetHoldability(boolean resultSetHoldability) {
      this.resultSetHoldability = resultSetHoldability;
      return this;
    }

    /**
     * Query timeout interrupt processing mode. Driver default: 1.
     *
     * @param queryTimeoutInterruptProcessingMode mode value
     * @return this builder
     */
    public Builder queryTimeoutInterruptProcessingMode(int queryTimeoutInterruptProcessingMode) {
      this.queryTimeoutInterruptProcessingMode = queryTimeoutInterruptProcessingMode;
      return this;
    }

    /**
     * Use JDBC4 column name/label semantics. Driver default: true.
     *
     * @param useJDBC4ColumnNameAndLabelSemantics true to use
     * @return this builder
     */
    public Builder useJDBC4ColumnNameAndLabelSemantics(
        boolean useJDBC4ColumnNameAndLabelSemantics) {
      this.useJDBC4ColumnNameAndLabelSemantics = useJDBC4ColumnNameAndLabelSemantics;
      return this;
    }

    // ==================== ERROR HANDLING ====================

    /**
     * Retrieve full error messages from server. Driver default: false. OUR DEFAULT: true (much
     * better error messages).
     *
     * @param retrieveMessagesFromServerOnGetMessage true to retrieve
     * @return this builder
     */
    public Builder retrieveMessagesFromServerOnGetMessage(
        boolean retrieveMessagesFromServerOnGetMessage) {
      this.retrieveMessagesFromServerOnGetMessage = retrieveMessagesFromServerOnGetMessage;
      return this;
    }

    /**
     * Read timeout in seconds. Driver default: 0 (no timeout).
     *
     * @param readTimeout timeout in seconds
     * @return this builder
     */
    public Builder readTimeout(int readTimeout) {
      this.readTimeout = readTimeout;
      return this;
    }

    /**
     * Atomic multi-row insert. Driver default: true.
     *
     * @param atomicMultiRowInsert true for atomic
     * @return this builder
     */
    public Builder atomicMultiRowInsert(boolean atomicMultiRowInsert) {
      this.atomicMultiRowInsert = atomicMultiRowInsert;
      return this;
    }

    /**
     * Return alias column names. Driver default: false.
     *
     * @param returnAlias true to return aliases
     * @return this builder
     */
    public Builder returnAlias(boolean returnAlias) {
      this.returnAlias = returnAlias;
      return this;
    }

    // ==================== SSL/TLS ====================

    /**
     * SSL connection type. Driver default: false.
     *
     * @param sslConnection "true" or SSL mode
     * @return this builder
     */
    public Builder sslConnection(String sslConnection) {
      this.sslConnection = sslConnection;
      return this;
    }

    /**
     * SSL trust store location. Driver default: null.
     *
     * @param sslTrustStoreLocation path to trust store
     * @return this builder
     */
    public Builder sslTrustStoreLocation(String sslTrustStoreLocation) {
      this.sslTrustStoreLocation = sslTrustStoreLocation;
      return this;
    }

    /**
     * SSL trust store password. Driver default: null.
     *
     * @param sslTrustStorePassword trust store password
     * @return this builder
     */
    public Builder sslTrustStorePassword(String sslTrustStorePassword) {
      this.sslTrustStorePassword = sslTrustStorePassword;
      return this;
    }

    /**
     * SSL key store location. Driver default: null.
     *
     * @param sslKeyStoreLocation path to key store
     * @return this builder
     */
    public Builder sslKeyStoreLocation(String sslKeyStoreLocation) {
      this.sslKeyStoreLocation = sslKeyStoreLocation;
      return this;
    }

    /**
     * SSL key store password. Driver default: null.
     *
     * @param sslKeyStorePassword key store password
     * @return this builder
     */
    public Builder sslKeyStorePassword(String sslKeyStorePassword) {
      this.sslKeyStorePassword = sslKeyStorePassword;
      return this;
    }

    /**
     * SSL cipher suites. Driver default: null.
     *
     * @param sslCipherSuites comma-separated cipher suites
     * @return this builder
     */
    public Builder sslCipherSuites(String sslCipherSuites) {
      this.sslCipherSuites = sslCipherSuites;
      return this;
    }

    // ==================== SECURITY ====================

    /**
     * Security mechanism. Driver default: 3 (clear text password).
     *
     * @param securityMechanism mechanism code (3=clear, 4=user only, 7=encrypted, etc.)
     * @return this builder
     */
    public Builder securityMechanism(int securityMechanism) {
      this.securityMechanism = securityMechanism;
      return this;
    }

    /**
     * Kerberos server principal. Driver default: null.
     *
     * @param kerberosServerPrincipal server principal
     * @return this builder
     */
    public Builder kerberosServerPrincipal(String kerberosServerPrincipal) {
      this.kerberosServerPrincipal = kerberosServerPrincipal;
      return this;
    }

    /**
     * GSS credential for Kerberos. Driver default: null.
     *
     * @param gssCredential credential object name
     * @return this builder
     */
    public Builder gssCredential(String gssCredential) {
      this.gssCredential = gssCredential;
      return this;
    }

    /**
     * Encryption algorithm enabled. Driver default: false.
     *
     * @param encryptionAlgorithm true to enable
     * @return this builder
     */
    public Builder encryptionAlgorithm(boolean encryptionAlgorithm) {
      this.encryptionAlgorithm = encryptionAlgorithm;
      return this;
    }

    /**
     * Public key list. Driver default: null.
     *
     * @param pkList public key list
     * @return this builder
     */
    public Builder pkList(String pkList) {
      this.pkList = pkList;
      return this;
    }

    /**
     * Security plugin name. Driver default: null.
     *
     * @param pluginName plugin name
     * @return this builder
     */
    public Builder pluginName(String pluginName) {
      this.pluginName = pluginName;
      return this;
    }

    /**
     * Send data as is without conversion. Driver default: false.
     *
     * @param sendDataAsIs true to send as is
     * @return this builder
     */
    public Builder sendDataAsIs(boolean sendDataAsIs) {
      this.sendDataAsIs = sendDataAsIs;
      return this;
    }

    // ==================== LOB ====================

    /**
     * Stream buffer size. Driver default: 1048576.
     *
     * @param streamBufferSize buffer size in bytes
     * @return this builder
     */
    public Builder streamBufferSize(int streamBufferSize) {
      this.streamBufferSize = streamBufferSize;
      return this;
    }

    /**
     * Fully materialize BLOB data. Driver default: true.
     *
     * @param fullyMaterializeBlobData true to materialize
     * @return this builder
     */
    public Builder fullyMaterializeBlobData(boolean fullyMaterializeBlobData) {
      this.fullyMaterializeBlobData = fullyMaterializeBlobData;
      return this;
    }

    /**
     * Fully materialize CLOB data. Driver default: true.
     *
     * @param fullyMaterializeClobData true to materialize
     * @return this builder
     */
    public Builder fullyMaterializeClobData(boolean fullyMaterializeClobData) {
      this.fullyMaterializeClobData = fullyMaterializeClobData;
      return this;
    }

    /**
     * Max retries for client reroute. Driver default: 3.
     *
     * @param maxRetriesForClientReroute retry count
     * @return this builder
     */
    public Builder maxRetriesForClientReroute(int maxRetriesForClientReroute) {
      this.maxRetriesForClientReroute = maxRetriesForClientReroute;
      return this;
    }

    /**
     * Retry interval for client reroute in seconds. Driver default: 0.
     *
     * @param retryIntervalForClientReroute interval in seconds
     * @return this builder
     */
    public Builder retryIntervalForClientReroute(int retryIntervalForClientReroute) {
      this.retryIntervalForClientReroute = retryIntervalForClientReroute;
      return this;
    }

    // ==================== STATEMENT ====================

    /**
     * Allow next on exhausted result set. Driver default: false.
     *
     * @param allowNextOnExhaustedResultSet true to allow
     * @return this builder
     */
    public Builder allowNextOnExhaustedResultSet(boolean allowNextOnExhaustedResultSet) {
      this.allowNextOnExhaustedResultSet = allowNextOnExhaustedResultSet;
      return this;
    }

    /**
     * Cursor sensitivity. Driver default: TYPE_SCROLL_INSENSITIVE.
     *
     * @param cursorSensitivity sensitivity type
     * @return this builder
     */
    public Builder cursorSensitivity(String cursorSensitivity) {
      this.cursorSensitivity = cursorSensitivity;
      return this;
    }

    /**
     * Cursor hold (0=close, 1=hold). Driver default: 1.
     *
     * @param cursorHold hold value
     * @return this builder
     */
    public Builder cursorHold(int cursorHold) {
      this.cursorHold = cursorHold;
      return this;
    }

    /**
     * Emulate parameter metadata for z/OS. Driver default: false.
     *
     * @param emulateParameterMetaDataForZCalls true to emulate
     * @return this builder
     */
    public Builder emulateParameterMetaDataForZCalls(boolean emulateParameterMetaDataForZCalls) {
      this.emulateParameterMetaDataForZCalls = emulateParameterMetaDataForZCalls;
      return this;
    }

    /**
     * Result set holdability for catalog queries. Driver default: 1.
     *
     * @param resultSetHoldabilityForCatalogQueries holdability value
     * @return this builder
     */
    public Builder resultSetHoldabilityForCatalogQueries(
        int resultSetHoldabilityForCatalogQueries) {
      this.resultSetHoldabilityForCatalogQueries = resultSetHoldabilityForCatalogQueries;
      return this;
    }

    /**
     * Query close implicit mode. Driver default: 0.
     *
     * @param queryCloseImplicit close mode
     * @return this builder
     */
    public Builder queryCloseImplicit(int queryCloseImplicit) {
      this.queryCloseImplicit = queryCloseImplicit;
      return this;
    }

    /**
     * Send char inputs as UTF-8. Driver default: false.
     *
     * @param sendCharInputsUTF8 true to send as UTF-8
     * @return this builder
     */
    public Builder sendCharInputsUTF8(boolean sendCharInputsUTF8) {
      this.sendCharInputsUTF8 = sendCharInputsUTF8;
      return this;
    }

    /**
     * Timestamp format. Driver default: null.
     *
     * @param timestampFormat format string
     * @return this builder
     */
    public Builder timestampFormat(String timestampFormat) {
      this.timestampFormat = timestampFormat;
      return this;
    }

    /**
     * Timestamp output type. Driver default: null.
     *
     * @param timestampOutputType output type
     * @return this builder
     */
    public Builder timestampOutputType(String timestampOutputType) {
      this.timestampOutputType = timestampOutputType;
      return this;
    }

    /**
     * Date format. Driver default: null.
     *
     * @param dateFormat format string
     * @return this builder
     */
    public Builder dateFormat(String dateFormat) {
      this.dateFormat = dateFormat;
      return this;
    }

    /**
     * Time format. Driver default: null.
     *
     * @param timeFormat format string
     * @return this builder
     */
    public Builder timeFormat(String timeFormat) {
      this.timeFormat = timeFormat;
      return this;
    }

    // ==================== TRACING ====================

    /**
     * Enable trace file. Driver default: false.
     *
     * @param traceFile true to enable
     * @return this builder
     */
    public Builder traceFile(boolean traceFile) {
      this.traceFile = traceFile;
      return this;
    }

    /**
     * Trace level. Driver default: 0.
     *
     * @param traceLevel level value
     * @return this builder
     */
    public Builder traceLevel(int traceLevel) {
      this.traceLevel = traceLevel;
      return this;
    }

    /**
     * Enable trace directory. Driver default: false.
     *
     * @param traceDirectory true to enable
     * @return this builder
     */
    public Builder traceDirectory(boolean traceDirectory) {
      this.traceDirectory = traceDirectory;
      return this;
    }

    /**
     * Enable log writer. Driver default: false.
     *
     * @param logWriter true to enable
     * @return this builder
     */
    public Builder logWriter(boolean logWriter) {
      this.logWriter = logWriter;
      return this;
    }

    // ==================== XA ====================

    /**
     * XA network optimization. Driver default: 0.
     *
     * @param xaNetworkOptimization optimization mode
     * @return this builder
     */
    public Builder xaNetworkOptimization(int xaNetworkOptimization) {
      this.xaNetworkOptimization = xaNetworkOptimization;
      return this;
    }

    /**
     * Downgrade hold cursors under XA. Driver default: false.
     *
     * @param downgradeHoldCursorsUnderXa true to downgrade
     * @return this builder
     */
    public Builder downgradeHoldCursorsUnderXa(boolean downgradeHoldCursorsUnderXa) {
      this.downgradeHoldCursorsUnderXa = downgradeHoldCursorsUnderXa;
      return this;
    }

    // ==================== COMPATIBILITY ====================

    /**
     * JDBC collection for packages. Driver default: false.
     *
     * @param jdbcCollection true to use
     * @return this builder
     */
    public Builder jdbcCollection(boolean jdbcCollection) {
      this.jdbcCollection = jdbcCollection;
      return this;
    }

    /**
     * Current package path. Driver default: null.
     *
     * @param currentPackagePath package path
     * @return this builder
     */
    public Builder currentPackagePath(String currentPackagePath) {
      this.currentPackagePath = currentPackagePath;
      return this;
    }

    /**
     * Current package set. Driver default: null.
     *
     * @param currentPackageSet package set
     * @return this builder
     */
    public Builder currentPackageSet(String currentPackageSet) {
      this.currentPackageSet = currentPackageSet;
      return this;
    }

    /**
     * Enable client affinities list. Driver default: false.
     *
     * @param enableClientAffinitiesList true to enable
     * @return this builder
     */
    public Builder enableClientAffinitiesList(boolean enableClientAffinitiesList) {
      this.enableClientAffinitiesList = enableClientAffinitiesList;
      return this;
    }

    /**
     * Alternate server name for client reroute. Driver default: null.
     *
     * @param clientRerouteAlternateServerName server name
     * @return this builder
     */
    public Builder clientRerouteAlternateServerName(String clientRerouteAlternateServerName) {
      this.clientRerouteAlternateServerName = clientRerouteAlternateServerName;
      return this;
    }

    /**
     * Alternate port for client reroute. Driver default: null.
     *
     * @param clientRerouteAlternatePortNumber port number
     * @return this builder
     */
    public Builder clientRerouteAlternatePortNumber(String clientRerouteAlternatePortNumber) {
      this.clientRerouteAlternatePortNumber = clientRerouteAlternatePortNumber;
      return this;
    }

    // ==================== ADVANCED CONNECTION ====================

    /**
     * Member connect timeout. Driver default: 0.
     *
     * @param memberConnectTimeout timeout in seconds
     * @return this builder
     */
    public Builder memberConnectTimeout(int memberConnectTimeout) {
      this.memberConnectTimeout = memberConnectTimeout;
      return this;
    }

    /**
     * System schema. Driver default: null.
     *
     * @param sysSchema schema name
     * @return this builder
     */
    public Builder sysSchema(String sysSchema) {
      this.sysSchema = sysSchema;
      return this;
    }

    /**
     * Affinity failback interval. Driver default: false.
     *
     * @param affinityFailbackInterval true to enable
     * @return this builder
     */
    public Builder affinityFailbackInterval(boolean affinityFailbackInterval) {
      this.affinityFailbackInterval = affinityFailbackInterval;
      return this;
    }

    /**
     * Enable Sysplex workload balancing. Driver default: false.
     *
     * @param enableSysplexWLB true to enable
     * @return this builder
     */
    public Builder enableSysplexWLB(boolean enableSysplexWLB) {
      this.enableSysplexWLB = enableSysplexWLB;
      return this;
    }

    /**
     * Max transport objects. Driver default: 0.
     *
     * @param maxTransportObjects max count
     * @return this builder
     */
    public Builder maxTransportObjects(int maxTransportObjects) {
      this.maxTransportObjects = maxTransportObjects;
      return this;
    }

    /**
     * Database name (alternative to URL path). Driver default: null.
     *
     * @param databaseName database name
     * @return this builder
     */
    public Builder databaseName(String databaseName) {
      this.databaseName = databaseName;
      return this;
    }

    /**
     * Decimal separator. Driver default: false.
     *
     * @param decimalSeparator true to enable
     * @return this builder
     */
    public Builder decimalSeparator(boolean decimalSeparator) {
      this.decimalSeparator = decimalSeparator;
      return this;
    }

    /**
     * Decimal string format. Driver default: 0.
     *
     * @param decimalStringFormat format value
     * @return this builder
     */
    public Builder decimalStringFormat(int decimalStringFormat) {
      this.decimalStringFormat = decimalStringFormat;
      return this;
    }

    /**
     * Client debug info. Driver default: 0.
     *
     * @param clientDebugInfo debug level
     * @return this builder
     */
    public Builder clientDebugInfo(int clientDebugInfo) {
      this.clientDebugInfo = clientDebugInfo;
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
     * Build the Db2Config.
     *
     * @return immutable Db2Config
     */
    public Db2Config build() {
      return new Db2Config(this);
    }
  }
}
