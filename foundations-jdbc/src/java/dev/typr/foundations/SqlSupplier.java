package dev.typr.foundations;

import java.sql.SQLException;

@FunctionalInterface
public interface SqlSupplier<T> {
  T get() throws SQLException;
}
