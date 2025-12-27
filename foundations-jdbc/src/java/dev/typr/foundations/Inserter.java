package dev.typr.foundations;

import java.sql.Connection;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

/**
 * Fluent builder for inserting rows with optional customization.
 *
 * <p>Enables a fluent API for test data insertion:
 *
 * <pre>
 * testInsert.customers("email@example.com", "password")
 *     .with(row -> row.withPhone(Defaulted.Provided("+1234567890")))
 *     .with(row -> row.withStatus(Defaulted.Provided(status)))
 *     .insert(connection);
 * </pre>
 *
 * @param <U> The unsaved row type (e.g., CustomersRowUnsaved)
 * @param <R> The return type after insertion (e.g., CustomersRow or CustomersId)
 */
public interface Inserter<U, R> {
  /**
   * Insert the row and return the result.
   *
   * @param c The database connection
   * @return The inserted row or its ID
   */
  R insert(Connection c);

  /**
   * Transform the unsaved row before insertion using withers.
   *
   * @param transformer Function to transform the unsaved row (typically using wither methods)
   * @return A new Inserter with the transformed row
   */
  Inserter<U, R> with(UnaryOperator<U> transformer);

  /**
   * Create an Inserter from an unsaved row and insert function.
   *
   * @param row The unsaved row
   * @param insertFn Function that inserts the row and returns the result
   * @return An Inserter for the row
   */
  static <U, R> Inserter<U, R> of(U row, BiFunction<U, Connection, R> insertFn) {
    return new Impl<>(row, insertFn);
  }

  /** Implementation that holds the row and insert function. */
  record Impl<U, R>(U row, BiFunction<U, Connection, R> insertFn) implements Inserter<U, R> {
    @Override
    public R insert(Connection c) {
      return insertFn.apply(row, c);
    }

    @Override
    public Inserter<U, R> with(UnaryOperator<U> transformer) {
      return new Impl<>(transformer.apply(row), insertFn);
    }
  }
}
