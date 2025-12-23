package typr.dsl;

import typr.runtime.DbType;

public record SqlFunction3<T1, T2, T3, O>(
    String name, TriFunction<T1, T2, T3, O> eval, DbType<O> outputType) {

  public static <T> SqlFunction3<T, Integer, Integer, T> substring(
      Bijection<T, String> bijection, DbType<T> dbType) {
    return new SqlFunction3<>(
        "substring",
        (stringish, from0, count) -> {
          String str = bijection.underlying(stringish);
          int from = from0 - 1; // PostgreSQL uses 1-based indexing
          int endIdx = Math.min(str.length(), from + count);
          return bijection.from(str.substring(Math.max(0, from), endIdx));
        },
        dbType);
  }
}
