package dev.typr.foundations;

import dev.typr.foundations.connect.DatabaseConfig;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Consumer;

/**
 * A thin wrapper around a source of database connections and a strategy for managing transactions.
 *
 * <p>Inspired by doobie's Transactor, this class provides a clean way to manage database
 * connections with configurable lifecycle hooks for transaction management.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * var transactor = new Transactor(dataSource::getConnection, Transactor.defaultStrategy());
 * String result = transactor.execute(conn -> {
 *     // use connection
 *     return "result";
 * });
 * }</pre>
 */
public record Transactor(SqlSupplier<Connection> connect, Strategy strategy) {

  /**
   * Create a transactor from a DatabaseConfig.
   *
   * @param config the database configuration
   * @param strategy the transaction strategy
   */
  public Transactor(DatabaseConfig config, Strategy strategy) {
    this(config::connect, strategy);
  }

  /**
   * Execute an operation with full strategy lifecycle.
   *
   * @param <T> the result type
   * @param operation the operation to execute with a connection
   * @return the operation result
   * @throws SQLException if a database error occurs
   */
  public <T> T execute(SqlFunction<Connection, T> operation) throws SQLException {
    Connection conn = connect.get();
    try {
      strategy.before().apply(conn);
      T result = operation.apply(conn);
      strategy.after().apply(conn);
      return result;
    } catch (SQLException | RuntimeException e) {
      strategy.oops().accept(e);
      throw e;
    } finally {
      strategy.always().apply(conn);
    }
  }

  /**
   * Execute an Operation with full strategy lifecycle.
   *
   * @param <T> the result type
   * @param op the Operation to execute
   * @return the operation result
   * @throws SQLException if a database error occurs
   */
  public <T> T execute(Operation<T> op) throws SQLException {
    return execute(op::run);
  }

  /**
   * Execute a void operation with full strategy lifecycle.
   *
   * @param operation the operation to execute with a connection
   * @throws SQLException if a database error occurs
   */
  public void executeVoid(SqlConsumer<Connection> operation) throws SQLException {
    execute(
        conn -> {
          operation.apply(conn);
          return null;
        });
  }

  /**
   * Default strategy: manual transactions with commit on success, close always.
   *
   * <p>Behavior:
   *
   * <ul>
   *   <li>before: setAutoCommit(false)
   *   <li>after: commit()
   *   <li>oops: no-op (caller handles exceptions)
   *   <li>always: close()
   * </ul>
   *
   * @return a strategy for manual transaction management
   */
  public static Strategy defaultStrategy() {
    return new Strategy(
        conn -> conn.setAutoCommit(false), Connection::commit, t -> {}, Connection::close);
  }

  /**
   * Strategy for auto-commit mode (no manual transactions).
   *
   * <p>Behavior:
   *
   * <ul>
   *   <li>before: no-op
   *   <li>after: no-op
   *   <li>oops: no-op
   *   <li>always: close()
   * </ul>
   *
   * @return a strategy for auto-commit mode
   */
  public static Strategy autoCommitStrategy() {
    return new Strategy(conn -> {}, conn -> {}, t -> {}, Connection::close);
  }

  /**
   * Strategy with rollback on error.
   *
   * <p>Behavior:
   *
   * <ul>
   *   <li>before: setAutoCommit(false)
   *   <li>after: commit()
   *   <li>oops: rollback() (silently ignores rollback failures)
   *   <li>always: close()
   * </ul>
   *
   * @return a strategy that rolls back on error
   */
  public static Strategy rollbackOnErrorStrategy() {
    return new Strategy(
        conn -> conn.setAutoCommit(false),
        Connection::commit,
        t -> {},
        conn -> {
          try {
            if (!conn.getAutoCommit() && !conn.isClosed()) {
              conn.rollback();
            }
          } catch (SQLException ignored) {
          }
          conn.close();
        });
  }

  /**
   * Strategy for testing: always rollback instead of commit.
   *
   * <p>Behavior:
   *
   * <ul>
   *   <li>before: setAutoCommit(false)
   *   <li>after: rollback() (instead of commit, to keep test data isolated)
   *   <li>oops: no-op (caller handles exceptions)
   *   <li>always: close()
   * </ul>
   *
   * @return a strategy for testing that always rolls back
   */
  public static Strategy testStrategy() {
    return new Strategy(
        conn -> conn.setAutoCommit(false), Connection::rollback, t -> {}, Connection::close);
  }

  /**
   * Data type representing the common setup, error-handling, and cleanup strategy associated with
   * an SQL transaction. A `Transactor` uses a `Strategy` to wrap programs prior to execution.
   *
   * @param before a program to prepare the connection for use
   * @param after a program to run on success
   * @param oops a program to run on failure (catch)
   * @param always a program to run in all cases (finally)
   */
  public record Strategy(
      SqlConsumer<Connection> before,
      SqlConsumer<Connection> after,
      Consumer<Throwable> oops,
      SqlConsumer<Connection> always) {}
}
