package dev.typr.foundations.connect.duckdb;

import dev.typr.foundations.connect.DatabaseConfig;
import dev.typr.foundations.connect.DatabaseKind;
import java.util.HashMap;
import java.util.Map;

/**
 * DuckDB database configuration with typed builder methods for all documented JDBC driver
 * properties.
 *
 * <p>DuckDB is an embedded analytical database with minimal configuration options.
 *
 * @see <a href="https://duckdb.org/docs/stable/clients/java">DuckDB Java Documentation</a>
 */
public final class DuckDbConfig implements DatabaseConfig {

  private final String path;

  // Mode properties
  private final Boolean readOnly;

  // Performance properties
  private final Boolean jdbcStreamResults;
  private final String tempDirectory;
  private final Integer threads;
  private final String memoryLimit;
  private final Integer defaultNullOrder;
  private final Integer defaultOrder;

  // Extension properties
  private final Boolean autoloadKnownExtensions;
  private final Boolean autoinstallKnownExtensions;
  private final String customExtensionRepository;

  // Behavior properties
  private final Boolean enableExternalAccess;
  private final Boolean allowUnsignedExtensions;
  private final Boolean enableObjectCache;
  private final Integer maximumMemory;
  private final Boolean preserveInsertionOrder;

  // Escape hatch
  private final Map<String, String> extraProperties;

  private DuckDbConfig(Builder b) {
    this.path = b.path;

    // Mode
    this.readOnly = b.readOnly;

    // Performance
    this.jdbcStreamResults = b.jdbcStreamResults;
    this.tempDirectory = b.tempDirectory;
    this.threads = b.threads;
    this.memoryLimit = b.memoryLimit;
    this.defaultNullOrder = b.defaultNullOrder;
    this.defaultOrder = b.defaultOrder;

    // Extension
    this.autoloadKnownExtensions = b.autoloadKnownExtensions;
    this.autoinstallKnownExtensions = b.autoinstallKnownExtensions;
    this.customExtensionRepository = b.customExtensionRepository;

    // Behavior
    this.enableExternalAccess = b.enableExternalAccess;
    this.allowUnsignedExtensions = b.allowUnsignedExtensions;
    this.enableObjectCache = b.enableObjectCache;
    this.maximumMemory = b.maximumMemory;
    this.preserveInsertionOrder = b.preserveInsertionOrder;

    this.extraProperties = Map.copyOf(b.extraProperties);
  }

  /**
   * Create a new builder for an in-memory database.
   *
   * @return a new builder for :memory: database
   */
  public static Builder inMemory() {
    return new Builder(":memory:");
  }

  /**
   * Create a new builder with a file path.
   *
   * @param path file path or :memory: for in-memory database
   * @return a new builder
   */
  public static Builder builder(String path) {
    return new Builder(path);
  }

  @Override
  public String jdbcUrl() {
    return "jdbc:duckdb:" + path;
  }

  @Override
  public String username() {
    return "";
  }

  @Override
  public String password() {
    return "";
  }

  @Override
  public DatabaseKind kind() {
    return DatabaseKind.DUCKDB;
  }

  @Override
  public Map<String, String> driverProperties() {
    Map<String, String> props = new HashMap<>();

    // Mode
    if (readOnly != null) props.put("duckdb.read_only", readOnly.toString());

    // Performance
    if (jdbcStreamResults != null) props.put("jdbc_stream_results", jdbcStreamResults.toString());
    if (tempDirectory != null) props.put("temp_directory", tempDirectory);
    if (threads != null) props.put("threads", threads.toString());
    if (memoryLimit != null) props.put("memory_limit", memoryLimit);
    if (defaultNullOrder != null) props.put("default_null_order", defaultNullOrder.toString());
    if (defaultOrder != null) props.put("default_order", defaultOrder.toString());

    // Extension
    if (autoloadKnownExtensions != null)
      props.put("autoload_known_extensions", autoloadKnownExtensions.toString());
    if (autoinstallKnownExtensions != null)
      props.put("autoinstall_known_extensions", autoinstallKnownExtensions.toString());
    if (customExtensionRepository != null)
      props.put("custom_extension_repository", customExtensionRepository);

    // Behavior
    if (enableExternalAccess != null)
      props.put("enable_external_access", enableExternalAccess.toString());
    if (allowUnsignedExtensions != null)
      props.put("allow_unsigned_extensions", allowUnsignedExtensions.toString());
    if (enableObjectCache != null) props.put("enable_object_cache", enableObjectCache.toString());
    if (maximumMemory != null) props.put("max_memory", maximumMemory.toString());
    if (preserveInsertionOrder != null)
      props.put("preserve_insertion_order", preserveInsertionOrder.toString());

    props.putAll(extraProperties);
    return props;
  }

  /** Builder for DuckDbConfig with typed methods for all JDBC driver properties. */
  public static final class Builder {
    private final String path;

    // Mode
    private Boolean readOnly;

    // Performance
    private Boolean jdbcStreamResults;
    private String tempDirectory;
    private Integer threads;
    private String memoryLimit;
    private Integer defaultNullOrder;
    private Integer defaultOrder;

    // Extension
    private Boolean autoloadKnownExtensions;
    private Boolean autoinstallKnownExtensions;
    private String customExtensionRepository;

    // Behavior
    private Boolean enableExternalAccess;
    private Boolean allowUnsignedExtensions;
    private Boolean enableObjectCache;
    private Integer maximumMemory;
    private Boolean preserveInsertionOrder;

    private final Map<String, String> extraProperties = new HashMap<>();

    private Builder(String path) {
      this.path = path;
    }

    // ==================== MODE ====================

    /**
     * Open database in read-only mode. Driver default: false.
     *
     * @param readOnly true for read-only
     * @return this builder
     */
    public Builder readOnly(boolean readOnly) {
      this.readOnly = readOnly;
      return this;
    }

    // ==================== PERFORMANCE ====================

    /**
     * Stream results instead of materializing. Driver default: false.
     *
     * @param jdbcStreamResults true to stream
     * @return this builder
     */
    public Builder jdbcStreamResults(boolean jdbcStreamResults) {
      this.jdbcStreamResults = jdbcStreamResults;
      return this;
    }

    /**
     * Temporary directory for spilling. Driver default: system temp.
     *
     * @param tempDirectory path to temp directory
     * @return this builder
     */
    public Builder tempDirectory(String tempDirectory) {
      this.tempDirectory = tempDirectory;
      return this;
    }

    /**
     * Number of threads to use. Driver default: all available cores.
     *
     * @param threads thread count
     * @return this builder
     */
    public Builder threads(int threads) {
      this.threads = threads;
      return this;
    }

    /**
     * Memory limit (e.g., "4GB", "512MB"). Driver default: 80% of RAM.
     *
     * @param memoryLimit memory limit with unit
     * @return this builder
     */
    public Builder memoryLimit(String memoryLimit) {
      this.memoryLimit = memoryLimit;
      return this;
    }

    /**
     * Default null ordering (0=nulls first, 1=nulls last). Driver default: 1.
     *
     * @param defaultNullOrder null order
     * @return this builder
     */
    public Builder defaultNullOrder(int defaultNullOrder) {
      this.defaultNullOrder = defaultNullOrder;
      return this;
    }

    /**
     * Default sort order (0=ASC, 1=DESC). Driver default: 0.
     *
     * @param defaultOrder sort order
     * @return this builder
     */
    public Builder defaultOrder(int defaultOrder) {
      this.defaultOrder = defaultOrder;
      return this;
    }

    // ==================== EXTENSION ====================

    /**
     * Automatically load known extensions. Driver default: true.
     *
     * @param autoloadKnownExtensions true to auto-load
     * @return this builder
     */
    public Builder autoloadKnownExtensions(boolean autoloadKnownExtensions) {
      this.autoloadKnownExtensions = autoloadKnownExtensions;
      return this;
    }

    /**
     * Automatically install known extensions. Driver default: true.
     *
     * @param autoinstallKnownExtensions true to auto-install
     * @return this builder
     */
    public Builder autoinstallKnownExtensions(boolean autoinstallKnownExtensions) {
      this.autoinstallKnownExtensions = autoinstallKnownExtensions;
      return this;
    }

    /**
     * Custom extension repository URL. Driver default: null.
     *
     * @param customExtensionRepository repository URL
     * @return this builder
     */
    public Builder customExtensionRepository(String customExtensionRepository) {
      this.customExtensionRepository = customExtensionRepository;
      return this;
    }

    // ==================== BEHAVIOR ====================

    /**
     * Enable external access (files, HTTP). Driver default: true.
     *
     * @param enableExternalAccess true to enable
     * @return this builder
     */
    public Builder enableExternalAccess(boolean enableExternalAccess) {
      this.enableExternalAccess = enableExternalAccess;
      return this;
    }

    /**
     * Allow unsigned extensions. Driver default: false.
     *
     * @param allowUnsignedExtensions true to allow
     * @return this builder
     */
    public Builder allowUnsignedExtensions(boolean allowUnsignedExtensions) {
      this.allowUnsignedExtensions = allowUnsignedExtensions;
      return this;
    }

    /**
     * Enable object cache for Parquet files. Driver default: false.
     *
     * @param enableObjectCache true to enable
     * @return this builder
     */
    public Builder enableObjectCache(boolean enableObjectCache) {
      this.enableObjectCache = enableObjectCache;
      return this;
    }

    /**
     * Maximum memory usage in bytes. Driver default: 80% of RAM.
     *
     * @param maximumMemory memory in bytes
     * @return this builder
     */
    public Builder maximumMemory(int maximumMemory) {
      this.maximumMemory = maximumMemory;
      return this;
    }

    /**
     * Preserve insertion order in results. Driver default: true.
     *
     * @param preserveInsertionOrder true to preserve
     * @return this builder
     */
    public Builder preserveInsertionOrder(boolean preserveInsertionOrder) {
      this.preserveInsertionOrder = preserveInsertionOrder;
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
     * Build the DuckDbConfig.
     *
     * @return immutable DuckDbConfig
     */
    public DuckDbConfig build() {
      return new DuckDbConfig(this);
    }
  }
}
