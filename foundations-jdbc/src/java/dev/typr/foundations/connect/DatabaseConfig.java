package dev.typr.foundations.connect;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;

/**
 * Configuration interface for database connections. Implemented by database-specific config classes
 * (PostgresConfig, MariaDbConfig, etc.).
 *
 * <p>Each implementation provides typed builder methods for ALL documented JDBC driver properties,
 * with sensible defaults where the driver defaults aren't optimal.
 *
 * <p>This interface can be used standalone with DriverManager (no pooling) or combined with
 * HikariCP via the foundations-jdbc-hikari module.
 */
public interface DatabaseConfig {

  /** Get the JDBC URL for this database configuration. */
  String jdbcUrl();

  /** Get the username for authentication. */
  String username();

  /** Get the password for authentication. */
  String password();

  /** Get the database kind (POSTGRESQL, MARIADB, etc.). */
  DatabaseKind kind();

  /**
   * Get all driver-specific properties (excluding user/password which are handled separately).
   * These are passed to the JDBC driver via DataSource properties or connection URL parameters.
   */
  Map<String, String> driverProperties();

  /**
   * Create a new connection using DriverManager. This is a simple non-pooled connection suitable
   * for scripts, tests, or low-volume use cases.
   *
   * <p>For production use with connection pooling, use HikariDataSourceFactory from the
   * foundations-jdbc-hikari module.
   *
   * @return a new database connection
   * @throws SQLException if connection fails
   */
  default Connection connect() throws SQLException {
    Properties props = new Properties();
    props.setProperty("user", username());
    props.setProperty("password", password());
    driverProperties().forEach(props::setProperty);
    return DriverManager.getConnection(jdbcUrl(), props);
  }
}
