package dev.typr.foundations.hikari;

import com.zaxxer.hikari.HikariDataSource;
import dev.typr.foundations.Transactor;
import dev.typr.foundations.Transactor.Strategy;
import java.io.Closeable;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;

/**
 * A wrapper around HikariDataSource that provides convenient access to Transactor.
 *
 * <p>This class wraps a HikariDataSource and provides methods for creating Transactors with
 * different strategies. It also implements Closeable for proper resource management.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * var ds = HikariDataSourceFactory.create(
 *     PostgresConfig.builder("localhost", 5432, "mydb", "user", "pass").build());
 *
 * // Get a transactor with default strategy (manual transactions)
 * var tx = ds.transactor();
 * tx.execute(conn -> repo.selectAll(conn));
 *
 * // Or with a custom strategy
 * var tx = ds.transactor(Transactor.autoCommitStrategy());
 * }</pre>
 */
public final class PooledDataSource implements Closeable {

  private final HikariDataSource dataSource;

  PooledDataSource(HikariDataSource dataSource) {
    this.dataSource = dataSource;
  }

  /**
   * Get the underlying HikariDataSource.
   *
   * @return the wrapped HikariDataSource
   */
  public HikariDataSource unwrap() {
    return dataSource;
  }

  /**
   * Get this as a standard JDBC DataSource.
   *
   * @return this as DataSource
   */
  public DataSource asDataSource() {
    return dataSource;
  }

  /**
   * Get a connection from the pool.
   *
   * @return a pooled connection
   * @throws SQLException if unable to get a connection
   */
  public Connection getConnection() throws SQLException {
    return dataSource.getConnection();
  }

  /**
   * Create a Transactor with the default strategy (manual transactions with commit on success).
   *
   * <p>The default strategy:
   *
   * <ul>
   *   <li>before: setAutoCommit(false)
   *   <li>after: commit()
   *   <li>oops: no-op (caller handles exceptions)
   *   <li>always: close()
   * </ul>
   *
   * @return a Transactor configured for manual transaction management
   */
  public Transactor transactor() {
    return new Transactor(this::getConnectionOrThrow, Transactor.defaultStrategy());
  }

  /**
   * Create a Transactor with a custom strategy.
   *
   * @param strategy the transaction management strategy
   * @return a Transactor configured with the provided strategy
   */
  public Transactor transactor(Strategy strategy) {
    return new Transactor(this::getConnectionOrThrow, strategy);
  }

  private Connection getConnectionOrThrow() {
    try {
      return dataSource.getConnection();
    } catch (SQLException e) {
      throw new RuntimeException("Failed to get connection from pool", e);
    }
  }

  /**
   * Close the underlying connection pool.
   *
   * <p>This will close all connections in the pool and release resources.
   */
  @Override
  public void close() {
    dataSource.close();
  }

  /**
   * Check if the pool is closed.
   *
   * @return true if the pool has been closed
   */
  public boolean isClosed() {
    return dataSource.isClosed();
  }

  /**
   * Check if the pool is running (not suspended or closed).
   *
   * @return true if the pool is running
   */
  public boolean isRunning() {
    return dataSource.isRunning();
  }
}
