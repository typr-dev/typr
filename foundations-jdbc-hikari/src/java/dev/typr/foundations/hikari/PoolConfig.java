package dev.typr.foundations.hikari;

import dev.typr.foundations.connect.TransactionIsolation;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * HikariCP connection pool configuration with typed builder methods for all documented properties.
 *
 * @see <a href="https://github.com/brettwooldridge/HikariCP#configuration-knobs-baby">HikariCP
 *     Documentation</a>
 */
public final class PoolConfig {

  // Pool sizing
  private final int maximumPoolSize;
  private final int minimumIdle;

  // Timeouts
  private final Duration connectionTimeout;
  private final Duration validationTimeout;
  private final Duration idleTimeout;
  private final Duration maxLifetime;
  private final Duration keepaliveTime;
  private final Duration leakDetectionThreshold;

  // Connection defaults
  private final TransactionIsolation transactionIsolation;
  private final Boolean autoCommit;
  private final Boolean readOnly;
  private final String catalog;
  private final String schema;
  private final String connectionInitSql;
  private final String connectionTestQuery;

  // Pool naming
  private final String poolName;

  // Advanced
  private final Boolean registerMbeans;
  private final Boolean allowPoolSuspension;
  private final Boolean isolateInternalQueries;

  // Escape hatch
  private final Map<String, String> extraProperties;

  private PoolConfig(Builder b) {
    this.maximumPoolSize = b.maximumPoolSize;
    this.minimumIdle = b.minimumIdle;

    this.connectionTimeout = b.connectionTimeout;
    this.validationTimeout = b.validationTimeout;
    this.idleTimeout = b.idleTimeout;
    this.maxLifetime = b.maxLifetime;
    this.keepaliveTime = b.keepaliveTime;
    this.leakDetectionThreshold = b.leakDetectionThreshold;

    this.transactionIsolation = b.transactionIsolation;
    this.autoCommit = b.autoCommit;
    this.readOnly = b.readOnly;
    this.catalog = b.catalog;
    this.schema = b.schema;
    this.connectionInitSql = b.connectionInitSql;
    this.connectionTestQuery = b.connectionTestQuery;

    this.poolName = b.poolName;

    this.registerMbeans = b.registerMbeans;
    this.allowPoolSuspension = b.allowPoolSuspension;
    this.isolateInternalQueries = b.isolateInternalQueries;

    this.extraProperties = Map.copyOf(b.extraProperties);
  }

  /**
   * Create a new builder with sensible defaults.
   *
   * @return a new builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /** Create a PoolConfig with all default values. */
  public static PoolConfig defaults() {
    return new Builder().build();
  }

  // Getters for HikariDataSourceFactory

  public int maximumPoolSize() {
    return maximumPoolSize;
  }

  public int minimumIdle() {
    return minimumIdle;
  }

  public Duration connectionTimeout() {
    return connectionTimeout;
  }

  public Duration validationTimeout() {
    return validationTimeout;
  }

  public Duration idleTimeout() {
    return idleTimeout;
  }

  public Duration maxLifetime() {
    return maxLifetime;
  }

  public Duration keepaliveTime() {
    return keepaliveTime;
  }

  public Duration leakDetectionThreshold() {
    return leakDetectionThreshold;
  }

  public TransactionIsolation transactionIsolation() {
    return transactionIsolation;
  }

  public Boolean autoCommit() {
    return autoCommit;
  }

  public Boolean readOnly() {
    return readOnly;
  }

  public String catalog() {
    return catalog;
  }

  public String schema() {
    return schema;
  }

  public String connectionInitSql() {
    return connectionInitSql;
  }

  public String connectionTestQuery() {
    return connectionTestQuery;
  }

  public String poolName() {
    return poolName;
  }

  public Boolean registerMbeans() {
    return registerMbeans;
  }

  public Boolean allowPoolSuspension() {
    return allowPoolSuspension;
  }

  public Boolean isolateInternalQueries() {
    return isolateInternalQueries;
  }

  public Map<String, String> extraProperties() {
    return extraProperties;
  }

  /** Builder for PoolConfig with typed methods for all HikariCP properties. */
  public static final class Builder {
    // Pool sizing - defaults from HikariCP
    private int maximumPoolSize = 10;
    private int minimumIdle = 10;

    // Timeouts - defaults from HikariCP
    private Duration connectionTimeout = Duration.ofSeconds(30);
    private Duration validationTimeout = Duration.ofSeconds(5);
    private Duration idleTimeout = Duration.ofMinutes(10);
    private Duration maxLifetime = Duration.ofMinutes(30);
    private Duration keepaliveTime = Duration.ZERO;
    private Duration leakDetectionThreshold = Duration.ZERO;

    // Connection defaults
    private TransactionIsolation transactionIsolation = null;
    private Boolean autoCommit = null;
    private Boolean readOnly = null;
    private String catalog = null;
    private String schema = null;
    private String connectionInitSql = null;
    private String connectionTestQuery = null;

    // Pool naming
    private String poolName = null;

    // Advanced
    private Boolean registerMbeans = null;
    private Boolean allowPoolSuspension = null;
    private Boolean isolateInternalQueries = null;

    private final Map<String, String> extraProperties = new HashMap<>();

    private Builder() {}

    // ==================== POOL SIZING ====================

    /**
     * Maximum number of connections in the pool. Default: 10.
     *
     * @param maximumPoolSize max connections
     * @return this builder
     */
    public Builder maximumPoolSize(int maximumPoolSize) {
      this.maximumPoolSize = maximumPoolSize;
      return this;
    }

    /**
     * Minimum number of idle connections to maintain. Default: same as maximumPoolSize.
     *
     * @param minimumIdle min idle connections
     * @return this builder
     */
    public Builder minimumIdle(int minimumIdle) {
      this.minimumIdle = minimumIdle;
      return this;
    }

    // ==================== TIMEOUTS ====================

    /**
     * Maximum time to wait for a connection from the pool. Default: 30 seconds.
     *
     * @param connectionTimeout timeout duration
     * @return this builder
     */
    public Builder connectionTimeout(Duration connectionTimeout) {
      this.connectionTimeout = connectionTimeout;
      return this;
    }

    /**
     * Maximum time to wait for connection validation. Default: 5 seconds.
     *
     * @param validationTimeout timeout duration
     * @return this builder
     */
    public Builder validationTimeout(Duration validationTimeout) {
      this.validationTimeout = validationTimeout;
      return this;
    }

    /**
     * Maximum time a connection can sit idle before being evicted. Default: 10 minutes.
     *
     * @param idleTimeout timeout duration
     * @return this builder
     */
    public Builder idleTimeout(Duration idleTimeout) {
      this.idleTimeout = idleTimeout;
      return this;
    }

    /**
     * Maximum lifetime of a connection in the pool. Default: 30 minutes.
     *
     * @param maxLifetime maximum lifetime
     * @return this builder
     */
    public Builder maxLifetime(Duration maxLifetime) {
      this.maxLifetime = maxLifetime;
      return this;
    }

    /**
     * Interval for connection keepalive queries. Default: 0 (disabled).
     *
     * @param keepaliveTime keepalive interval
     * @return this builder
     */
    public Builder keepaliveTime(Duration keepaliveTime) {
      this.keepaliveTime = keepaliveTime;
      return this;
    }

    /**
     * Threshold for connection leak detection. Default: 0 (disabled).
     *
     * @param leakDetectionThreshold detection threshold
     * @return this builder
     */
    public Builder leakDetectionThreshold(Duration leakDetectionThreshold) {
      this.leakDetectionThreshold = leakDetectionThreshold;
      return this;
    }

    // ==================== CONNECTION DEFAULTS ====================

    /**
     * Default transaction isolation level for connections. Default: null (driver default).
     *
     * @param transactionIsolation isolation level
     * @return this builder
     */
    public Builder transactionIsolation(TransactionIsolation transactionIsolation) {
      this.transactionIsolation = transactionIsolation;
      return this;
    }

    /**
     * Default auto-commit mode for connections. Default: null (driver default, usually true).
     *
     * @param autoCommit auto-commit mode
     * @return this builder
     */
    public Builder autoCommit(boolean autoCommit) {
      this.autoCommit = autoCommit;
      return this;
    }

    /**
     * Default read-only mode for connections. Default: null (driver default, usually false).
     *
     * @param readOnly read-only mode
     * @return this builder
     */
    public Builder readOnly(boolean readOnly) {
      this.readOnly = readOnly;
      return this;
    }

    /**
     * Default catalog for connections. Default: null (driver default).
     *
     * @param catalog catalog name
     * @return this builder
     */
    public Builder catalog(String catalog) {
      this.catalog = catalog;
      return this;
    }

    /**
     * Default schema for connections. Default: null (driver default).
     *
     * @param schema schema name
     * @return this builder
     */
    public Builder schema(String schema) {
      this.schema = schema;
      return this;
    }

    /**
     * SQL to execute when a connection is created. Default: null.
     *
     * @param connectionInitSql initialization SQL
     * @return this builder
     */
    public Builder connectionInitSql(String connectionInitSql) {
      this.connectionInitSql = connectionInitSql;
      return this;
    }

    /**
     * SQL to execute for connection validation (prefer isValid() when possible). Default: null.
     *
     * @param connectionTestQuery test query
     * @return this builder
     */
    public Builder connectionTestQuery(String connectionTestQuery) {
      this.connectionTestQuery = connectionTestQuery;
      return this;
    }

    // ==================== POOL NAMING ====================

    /**
     * Name for the connection pool (for JMX and logging). Default: auto-generated.
     *
     * @param poolName pool name
     * @return this builder
     */
    public Builder poolName(String poolName) {
      this.poolName = poolName;
      return this;
    }

    // ==================== ADVANCED ====================

    /**
     * Register pool with JMX. Default: false.
     *
     * @param registerMbeans true to register
     * @return this builder
     */
    public Builder registerMbeans(boolean registerMbeans) {
      this.registerMbeans = registerMbeans;
      return this;
    }

    /**
     * Allow pool suspension for maintenance. Default: false.
     *
     * @param allowPoolSuspension true to allow
     * @return this builder
     */
    public Builder allowPoolSuspension(boolean allowPoolSuspension) {
      this.allowPoolSuspension = allowPoolSuspension;
      return this;
    }

    /**
     * Isolate internal HikariCP queries. Default: false.
     *
     * @param isolateInternalQueries true to isolate
     * @return this builder
     */
    public Builder isolateInternalQueries(boolean isolateInternalQueries) {
      this.isolateInternalQueries = isolateInternalQueries;
      return this;
    }

    /**
     * Set an arbitrary HikariCP property.
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
     * Build the PoolConfig.
     *
     * @return immutable PoolConfig
     */
    public PoolConfig build() {
      return new PoolConfig(this);
    }
  }
}
