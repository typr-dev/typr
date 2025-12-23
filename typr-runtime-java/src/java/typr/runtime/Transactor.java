package typr.runtime;

import java.sql.Connection;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A thin wrapper around a source of database connections and a strategy for managing transactions.
 */
public record Transactor(Supplier<Connection> connect, Strategy strategy) {
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
