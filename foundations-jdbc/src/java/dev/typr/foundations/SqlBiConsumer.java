package dev.typr.foundations;

import java.sql.SQLException;

public interface SqlBiConsumer<T1, T2> {
  void apply(T1 t1, T2 t2) throws SQLException;
}
