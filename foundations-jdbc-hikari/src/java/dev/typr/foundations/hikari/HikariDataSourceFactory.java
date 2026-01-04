package dev.typr.foundations.hikari;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.typr.foundations.connect.DatabaseConfig;

/**
 * Factory for creating PooledDataSource instances from DatabaseConfig and PoolConfig.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Minimal - all defaults
 * var ds = HikariDataSourceFactory.create(
 *     PostgresConfig.builder("localhost", 5432, "mydb", "user", "pass").build());
 *
 * // Use the transactor
 * var tx = ds.transactor();
 * tx.execute(conn -> repo.selectAll(conn));
 *
 * // With pool configuration
 * var ds = HikariDataSourceFactory.create(
 *     PostgresConfig.builder("localhost", 5432, "mydb", "user", "pass")
 *         .sslmode(PgSslMode.REQUIRE)
 *         .build(),
 *     PoolConfig.builder()
 *         .maximumPoolSize(20)
 *         .connectionTimeout(Duration.ofSeconds(10))
 *         .build());
 * }</pre>
 */
public final class HikariDataSourceFactory {

  private HikariDataSourceFactory() {}

  /**
   * Create a PooledDataSource from DatabaseConfig with default pool settings.
   *
   * @param config database configuration
   * @return configured PooledDataSource with transactor() method
   */
  public static PooledDataSource create(DatabaseConfig config) {
    return create(config, PoolConfig.defaults());
  }

  /**
   * Create a PooledDataSource from DatabaseConfig and PoolConfig.
   *
   * @param config database configuration
   * @param pool pool configuration
   * @return configured PooledDataSource with transactor() method
   */
  public static PooledDataSource create(DatabaseConfig config, PoolConfig pool) {
    HikariConfig hikari = new HikariConfig();

    // Connection settings from DatabaseConfig
    hikari.setJdbcUrl(config.jdbcUrl());
    hikari.setUsername(config.username());
    hikari.setPassword(config.password());

    // Driver properties from DatabaseConfig
    config.driverProperties().forEach(hikari::addDataSourceProperty);

    // Pool sizing
    hikari.setMaximumPoolSize(pool.maximumPoolSize());
    hikari.setMinimumIdle(pool.minimumIdle());

    // Timeouts
    hikari.setConnectionTimeout(pool.connectionTimeout().toMillis());
    hikari.setValidationTimeout(pool.validationTimeout().toMillis());
    hikari.setIdleTimeout(pool.idleTimeout().toMillis());
    hikari.setMaxLifetime(pool.maxLifetime().toMillis());
    hikari.setKeepaliveTime(pool.keepaliveTime().toMillis());
    hikari.setLeakDetectionThreshold(pool.leakDetectionThreshold().toMillis());

    // Connection defaults
    if (pool.transactionIsolation() != null) {
      hikari.setTransactionIsolation(pool.transactionIsolation().jdbcName());
    }
    if (pool.autoCommit() != null) {
      hikari.setAutoCommit(pool.autoCommit());
    }
    if (pool.readOnly() != null) {
      hikari.setReadOnly(pool.readOnly());
    }
    if (pool.catalog() != null) {
      hikari.setCatalog(pool.catalog());
    }
    if (pool.schema() != null) {
      hikari.setSchema(pool.schema());
    }
    if (pool.connectionInitSql() != null) {
      hikari.setConnectionInitSql(pool.connectionInitSql());
    }
    if (pool.connectionTestQuery() != null) {
      hikari.setConnectionTestQuery(pool.connectionTestQuery());
    }

    // Pool naming
    if (pool.poolName() != null) {
      hikari.setPoolName(pool.poolName());
    }

    // Advanced
    if (pool.registerMbeans() != null) {
      hikari.setRegisterMbeans(pool.registerMbeans());
    }
    if (pool.allowPoolSuspension() != null) {
      hikari.setAllowPoolSuspension(pool.allowPoolSuspension());
    }
    if (pool.isolateInternalQueries() != null) {
      hikari.setIsolateInternalQueries(pool.isolateInternalQueries());
    }

    // Extra properties
    pool.extraProperties().forEach(hikari::addDataSourceProperty);

    return new PooledDataSource(new HikariDataSource(hikari));
  }
}
