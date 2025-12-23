package typr.dsl;

import java.util.function.Function;
import typr.runtime.DbType;
import typr.runtime.PgTypes;

public record SqlFunction1<T1, O>(String name, Function<T1, O> eval, DbType<O> outputType) {

  public static <T> SqlFunction1<T, T> lower(Bijection<T, String> bijection, DbType<T> dbType) {
    return new SqlFunction1<>(
        "lower", value -> bijection.from(bijection.underlying(value).toLowerCase()), dbType);
  }

  public static <T> SqlFunction1<T, T> upper(Bijection<T, String> bijection, DbType<T> dbType) {
    return new SqlFunction1<>(
        "upper", value -> bijection.from(bijection.underlying(value).toUpperCase()), dbType);
  }

  public static <T> SqlFunction1<T, T> reverse(Bijection<T, String> bijection, DbType<T> dbType) {
    return new SqlFunction1<>(
        "reverse",
        value ->
            bijection.from(new StringBuilder(bijection.underlying(value)).reverse().toString()),
        dbType);
  }

  public static <T> SqlFunction1<T, Integer> length(Bijection<T, String> bijection) {
    return new SqlFunction1<>(
        "length", value -> bijection.underlying(value).length(), PgTypes.int4);
  }

  public static <T> SqlFunction1<T[], Integer> arrayLength() {
    return new SqlFunction1<>("array_length", array -> array.length, PgTypes.int4);
  }
}
