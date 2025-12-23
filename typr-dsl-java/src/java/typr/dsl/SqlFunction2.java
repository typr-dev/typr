package typr.dsl;

import java.util.function.BiFunction;
import typr.runtime.DbType;
import typr.runtime.PgTypes;

public record SqlFunction2<T1, T2, O>(
    String name, BiFunction<T1, T2, O> eval, DbType<O> outputType) {

  public static <T> SqlFunction2<T, String, Integer> strpos(Bijection<T, String> bijection) {
    return new SqlFunction2<>(
        "strpos",
        (str, substring) -> {
          int pos = bijection.underlying(str).indexOf(substring);
          return pos == -1 ? 0 : pos + 1; // PostgreSQL uses 1-based indexing
        },
        PgTypes.int4);
  }

  public static <T> SqlFunction2<T, T, T> coalesce(DbType<T> dbType) {
    return new SqlFunction2<>("coalesce", (a, b) -> a != null ? a : b, dbType);
  }
}
