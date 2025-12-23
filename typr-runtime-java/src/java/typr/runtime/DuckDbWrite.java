package typr.runtime;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.*;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Describes how to write a value to a {@link PreparedStatement} for DuckDB. DuckDB's JDBC driver
 * handles most types through setObject, but some types need special handling: - UUID: use setString
 * to avoid byte ordering bug in setObject - TIME: use setString to avoid timezone issues with
 * java.sql.Time - INTERVAL: use setString with duration format
 */
public sealed interface DuckDbWrite<A> extends DbWrite<A> permits DuckDbWrite.Instance {
  void set(PreparedStatement ps, int idx, A a) throws SQLException;

  DuckDbWrite<Optional<A>> opt(DuckDbTypename<A> typename);

  <B> DuckDbWrite<B> contramap(Function<B, A> f);

  @FunctionalInterface
  interface RawWriter<A> {
    void set(PreparedStatement ps, int index, A a) throws SQLException;
  }

  record Instance<A, U>(RawWriter<U> rawWriter, Function<A, U> f) implements DuckDbWrite<A> {
    @Override
    public void set(PreparedStatement ps, int index, A a) throws SQLException {
      rawWriter.set(ps, index, f.apply(a));
    }

    @Override
    public DuckDbWrite<Optional<A>> opt(DuckDbTypename<A> typename) {
      return new Instance<>(
          (ps, index, u) -> {
            if (u == null) ps.setNull(index, java.sql.Types.NULL);
            else set(ps, index, u);
          },
          a -> a.orElse(null));
    }

    @Override
    public <B> DuckDbWrite<B> contramap(Function<B, A> f) {
      return new Instance<>(rawWriter, f.andThen(this.f));
    }
  }

  static <A> DuckDbWrite<A> primitive(RawWriter<A> rawWriter) {
    return new Instance<>(rawWriter, Function.identity());
  }

  static <A> DuckDbWrite<A> passObjectToJdbc() {
    return primitive(PreparedStatement::setObject);
  }

  // Basic type writers
  DuckDbWrite<String> writeString = primitive(PreparedStatement::setString);
  DuckDbWrite<Boolean> writeBoolean = primitive(PreparedStatement::setBoolean);
  DuckDbWrite<Byte> writeByte = primitive(PreparedStatement::setByte);
  DuckDbWrite<Short> writeShort = primitive(PreparedStatement::setShort);
  DuckDbWrite<Integer> writeInteger = primitive(PreparedStatement::setInt);
  DuckDbWrite<Long> writeLong = primitive(PreparedStatement::setLong);
  DuckDbWrite<Float> writeFloat = primitive(PreparedStatement::setFloat);
  DuckDbWrite<Double> writeDouble = primitive(PreparedStatement::setDouble);
  DuckDbWrite<BigDecimal> writeBigDecimal = primitive(PreparedStatement::setBigDecimal);
  // Use setString for BigInteger to handle the full 128-bit HUGEINT/UHUGEINT range
  // setBigDecimal(new BigDecimal(hugeint)) fails for values at the 128-bit boundary
  DuckDbWrite<BigInteger> writeBigInteger = writeString.contramap(BigInteger::toString);
  DuckDbWrite<byte[]> writeByteArray = primitive(PreparedStatement::setBytes);

  // UUID - use setString to avoid DuckDB's byte ordering bug with setObject(UUID)
  DuckDbWrite<UUID> writeUuid = writeString.contramap(UUID::toString);

  // TIME - use setString to avoid timezone issues with java.sql.Time
  DuckDbWrite<LocalTime> writeLocalTime = writeString.contramap(LocalTime::toString);

  // INTERVAL/Duration - use setString with duration format (HH:MM:SS)
  DuckDbWrite<Duration> writeDuration =
      writeString.contramap(
          d -> {
            long hours = d.toHours();
            long minutes = d.toMinutesPart();
            long seconds = d.toSecondsPart();
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
          });

  // ==================== Nested Types ====================
  // DuckDB JDBC supports setObject with DuckDBUserArray for arrays

  /**
   * Write a LIST/Array by converting to DuckDBUserArray. DuckDB JDBC natively supports
   * DuckDBUserArray via setObject().
   *
   * @param typeName the DuckDB type name for the elements (e.g., "INTEGER", "VARCHAR")
   * @param toArray function to convert List to Object array
   * @param <E> element type
   * @return writer for List of elements
   */
  static <E> DuckDbWrite<java.util.List<E>> writeList(
      String typeName, java.util.function.IntFunction<E[]> toArray) {
    return primitive(
        (ps, idx, list) -> {
          if (list == null) {
            ps.setNull(idx, java.sql.Types.ARRAY);
          } else {
            E[] array = list.toArray(toArray);
            org.duckdb.user.DuckDBUserArray userArray =
                new org.duckdb.user.DuckDBUserArray(typeName, array);
            ps.setObject(idx, userArray);
          }
        });
  }

  // ==================== SQL Literal-Based List Writers ====================
  // These types require string conversion because DuckDB JNI doesn't handle them
  // directly or has bugs (e.g., UUID byte-ordering). ~33% overhead at 100k rows.

  /**
   * Write a LIST/Array by formatting elements using DuckDbStringifier. Use this for types that
   * DuckDB JNI doesn't handle natively. Uses unquoted format (quoted=false) suitable for
   * DuckDBUserArray.
   *
   * @param typeName the DuckDB type name for the elements (e.g., "TIME", "DATE")
   * @param stringifier how to format elements
   * @param <E> element type
   * @return writer for List of elements
   */
  static <E> DuckDbWrite<java.util.List<E>> writeListViaSqlLiteral(
      String typeName, DuckDbStringifier<E> stringifier) {
    return primitive(
        (ps, idx, list) -> {
          if (list == null) {
            ps.setNull(idx, java.sql.Types.ARRAY);
          } else {
            String[] array =
                list.stream().map(e -> stringifier.encode(e, false)).toArray(String[]::new);
            org.duckdb.user.DuckDBUserArray userArray =
                new org.duckdb.user.DuckDBUserArray(typeName, array);
            ps.setObject(idx, userArray);
          }
        });
  }

  /**
   * Write a MAP with typed keys and values using DuckDBMap. DuckDB JDBC natively supports DuckDBMap
   * via setObject().
   *
   * @param sqlTypeName the full DuckDB type name (e.g., "MAP(VARCHAR, INTEGER)")
   * @param <K> key type
   * @param <V> value type
   * @return writer for Map
   */
  static <K, V> DuckDbWrite<java.util.Map<K, V>> writeMap(String sqlTypeName) {
    return primitive(
        (ps, idx, map) -> {
          if (map == null) {
            ps.setNull(idx, java.sql.Types.OTHER);
          } else {
            org.duckdb.user.DuckDBMap<K, V> duckDbMap =
                new org.duckdb.user.DuckDBMap<>(sqlTypeName, map);
            ps.setObject(idx, duckDbMap);
          }
        });
  }

  /**
   * Write a MAP with typed keys and values using DuckDBMap, converting via DuckDbStringifier. All
   * keys and values are converted to String. DuckDB parses them based on the type name. Uses
   * unquoted format (quoted=false) suitable for DuckDBMap.
   *
   * @param sqlTypeName the full DuckDB type name (e.g., "MAP(UUID, TIME)")
   * @param keyStringifier how to format keys
   * @param valueStringifier how to format values
   * @param <K> key type
   * @param <V> value type
   * @return writer for Map
   */
  static <K, V> DuckDbWrite<java.util.Map<K, V>> writeMapViaSqlLiteral(
      String sqlTypeName,
      DuckDbStringifier<K> keyStringifier,
      DuckDbStringifier<V> valueStringifier) {
    return primitive(
        (ps, idx, map) -> {
          if (map == null) {
            ps.setNull(idx, java.sql.Types.OTHER);
          } else {
            java.util.Map<String, String> wireMap = new java.util.LinkedHashMap<>();
            for (var entry : map.entrySet()) {
              wireMap.put(
                  keyStringifier.encode(entry.getKey(), false),
                  valueStringifier.encode(entry.getValue(), false));
            }
            org.duckdb.user.DuckDBMap<String, String> duckDbMap =
                new org.duckdb.user.DuckDBMap<>(sqlTypeName, wireMap);
            ps.setObject(idx, duckDbMap);
          }
        });
  }
}
