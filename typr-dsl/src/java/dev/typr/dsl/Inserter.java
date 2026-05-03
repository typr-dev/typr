package dev.typr.dsl;

import dev.typr.foundations.Connection;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

/**
 * Fluent builder for inserting rows with optional customization, taking a {@link
 * dev.typr.foundations.Connection}.
 *
 * <p>typr-side counterpart of {@link dev.typr.foundations.Inserter}, used by generated test-insert
 * helpers so callers stay on the foundations Connection abstraction.
 *
 * @param <U> The unsaved row type
 * @param <R> The return type after insertion
 */
public interface Inserter<U, R> {
  /** Insert the row using the given foundations connection. */
  R insert(Connection c);

  /** Transform the unsaved row before insertion. */
  Inserter<U, R> with(UnaryOperator<U> transformer);

  /**
   * Build an Inserter from an unsaved row and an insert function taking a foundations Connection.
   */
  static <U, R> Inserter<U, R> of(U row, BiFunction<U, Connection, R> insertFn) {
    return new Impl<>(row, insertFn);
  }

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
