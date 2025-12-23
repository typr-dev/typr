package typr.runtime;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.*;
import java.util.Optional;
import java.util.UUID;

/**
 * Describes how to read a column from a {@link ResultSet} for DuckDB. DuckDB's JDBC driver provides
 * good type support for most types.
 */
public sealed interface DuckDbRead<A> extends DbRead<A>
    permits DuckDbRead.NonNullable, DuckDbRead.Nullable, KotlinNullableDuckDbRead {
  A read(ResultSet rs, int col) throws SQLException;

  <B> DuckDbRead<B> map(SqlFunction<A, B> f);

  /** Derive a DuckDbRead which allows nullable values */
  DuckDbRead<Optional<A>> opt();

  @FunctionalInterface
  interface RawRead<A> {
    A apply(ResultSet rs, int column) throws SQLException;
  }

  /**
   * Create an instance of {@link DuckDbRead} from a function that reads a value from a result set.
   *
   * @param f Should not blow up if the value returned is `null`
   */
  static <A> NonNullable<A> of(RawRead<A> f) {
    RawRead<Optional<A>> readNullableA =
        (rs, col) -> {
          var a = f.apply(rs, col);
          if (rs.wasNull()) return Optional.empty();
          else return Optional.of(a);
        };
    return new NonNullable<>(readNullableA);
  }

  final class NonNullable<A> implements DuckDbRead<A> {
    final RawRead<Optional<A>> readNullable;

    public NonNullable(RawRead<Optional<A>> readNullable) {
      this.readNullable = readNullable;
    }

    @Override
    public A read(ResultSet rs, int col) throws SQLException {
      return readNullable
          .apply(rs, col)
          .orElseThrow(() -> new SQLException("null value in column " + col));
    }

    @Override
    public <B> NonNullable<B> map(SqlFunction<A, B> f) {
      return new NonNullable<>(
          (rs, col) -> {
            Optional<A> maybeA = readNullable.apply(rs, col);
            if (maybeA.isEmpty()) return Optional.empty();
            return Optional.of(f.apply(maybeA.get()));
          });
    }

    @Override
    public DuckDbRead<Optional<A>> opt() {
      return new Nullable<>(readNullable);
    }
  }

  final class Nullable<A> implements DuckDbRead<Optional<A>>, DbRead.Nullable {
    final RawRead<Optional<A>> readNullable;

    public Nullable(RawRead<Optional<A>> readNullable) {
      this.readNullable = readNullable;
    }

    @Override
    public Optional<A> read(ResultSet rs, int col) throws SQLException {
      return readNullable.apply(rs, col);
    }

    @Override
    public <B> DuckDbRead<B> map(SqlFunction<Optional<A>, B> f) {
      // Mapping over Nullable should return NonNullable because the function f
      // is expected to produce a non-null B from Optional<A>
      // However, if B itself is Optional, we need to flatten it
      return new NonNullable<>(
          (rs, col) -> {
            Optional<A> opt = read(rs, col);
            B result = f.apply(opt);
            return Optional.ofNullable(result);
          });
    }

    @Override
    public Nullable<Optional<A>> opt() {
      return new Nullable<>(
          (rs, col) -> {
            Optional<A> maybeA = readNullable.apply(rs, col);
            if (maybeA.isEmpty()) return Optional.empty();
            return Optional.of(maybeA);
          });
    }
  }

  static <A> NonNullable<A> castJdbcObjectTo(Class<A> cls) {
    return of((rs, i) -> cls.cast(rs.getObject(i)));
  }

  static <A> NonNullable<A> getObjectAs(Class<A> cls) {
    return of((rs, i) -> rs.getObject(i, cls));
  }

  // Basic type readers
  DuckDbRead<String> readString = of(ResultSet::getString);
  DuckDbRead<Boolean> readBoolean = of(ResultSet::getBoolean);
  DuckDbRead<Byte> readByte = of(ResultSet::getByte);
  DuckDbRead<Short> readShort = of(ResultSet::getShort);
  DuckDbRead<Integer> readInteger = of(ResultSet::getInt);
  DuckDbRead<Long> readLong = of(ResultSet::getLong);
  DuckDbRead<Float> readFloat = of(ResultSet::getFloat);
  DuckDbRead<Double> readDouble = of(ResultSet::getDouble);
  DuckDbRead<BigDecimal> readBigDecimal = of(ResultSet::getBigDecimal);
  DuckDbRead<byte[]> readByteArray = of(ResultSet::getBytes);

  // BigInteger for HUGEINT/UHUGEINT - DuckDB JDBC returns BigInteger directly
  DuckDbRead<BigInteger> readBigInteger = castJdbcObjectTo(BigInteger.class);

  // Date/Time readers - DuckDB JDBC has specific return types
  DuckDbRead<LocalDate> readLocalDate = castJdbcObjectTo(LocalDate.class);
  DuckDbRead<LocalTime> readLocalTime = castJdbcObjectTo(LocalTime.class);
  // DuckDB returns java.sql.Timestamp for TIMESTAMP types
  DuckDbRead<LocalDateTime> readLocalDateTime =
      of(
          (rs, idx) -> {
            Object obj = rs.getObject(idx);
            if (obj == null) return null;
            if (obj instanceof LocalDateTime) return (LocalDateTime) obj;
            if (obj instanceof java.sql.Timestamp)
              return ((java.sql.Timestamp) obj).toLocalDateTime();
            throw new SQLException("Cannot convert " + obj.getClass() + " to LocalDateTime");
          });
  DuckDbRead<OffsetDateTime> readOffsetDateTime =
      of(
          (rs, idx) -> {
            Object obj = rs.getObject(idx);
            if (obj == null) return null;
            if (obj instanceof OffsetDateTime) return (OffsetDateTime) obj;
            if (obj instanceof java.sql.Timestamp) {
              // DuckDB TIMESTAMPTZ is stored as UTC, returned as Timestamp
              return ((java.sql.Timestamp) obj).toLocalDateTime().atOffset(ZoneOffset.UTC);
            }
            throw new SQLException("Cannot convert " + obj.getClass() + " to OffsetDateTime");
          });

  // UUID - DuckDB has native UUID support
  DuckDbRead<UUID> readUuid =
      of(
          (rs, idx) -> {
            Object obj = rs.getObject(idx);
            if (obj == null) return null;
            if (obj instanceof UUID) return (UUID) obj;
            if (obj instanceof String) return UUID.fromString((String) obj);
            throw new SQLException("Cannot convert " + obj.getClass() + " to UUID");
          });

  // Interval - DuckDB returns as string in "HH:MM:SS" or "HH:MM:SS.micros" format
  DuckDbRead<Duration> readDuration =
      of(
          (rs, idx) -> {
            String s = rs.getString(idx);
            if (s == null) return null;
            // DuckDB interval format: "HH:MM:SS" or "HH:MM:SS.micros" for time intervals
            // Parse manually since Duration.parse expects PT format
            try {
              String[] parts = s.split(":");
              if (parts.length >= 3) {
                long hours = Long.parseLong(parts[0]);
                long minutes = Long.parseLong(parts[1]);
                // Handle seconds with potential fractional part
                String secPart = parts[2];
                int dotIdx = secPart.indexOf('.');
                long seconds;
                long nanos = 0;
                if (dotIdx >= 0) {
                  seconds = Long.parseLong(secPart.substring(0, dotIdx));
                  String fracStr = secPart.substring(dotIdx + 1);
                  // Pad or truncate to 9 digits for nanoseconds
                  while (fracStr.length() < 9) fracStr += "0";
                  if (fracStr.length() > 9) fracStr = fracStr.substring(0, 9);
                  nanos = Long.parseLong(fracStr);
                } else {
                  seconds = Long.parseLong(secPart);
                }
                return Duration.ofHours(hours)
                    .plusMinutes(minutes)
                    .plusSeconds(seconds)
                    .plusNanos(nanos);
              }
              // Fallback to ISO 8601 parse
              return Duration.parse(s);
            } catch (Exception e) {
              throw new SQLException("Cannot parse interval: " + s, e);
            }
          });

  // BLOB - DuckDB returns as byte[]
  DuckDbRead<byte[]> readBlob =
      of(
          (rs, idx) -> {
            java.sql.Blob blob = rs.getBlob(idx);
            if (blob == null) return null;
            return blob.getBytes(1, (int) blob.length());
          });

  // BIT type - DuckDB returns as String of 0s and 1s
  DuckDbRead<String> readBitString = readString;

  // ==================== Nested Types ====================

  /**
   * Read a LIST/Array column. DuckDB returns org.duckdb.DuckDBArray which implements
   * java.sql.Array. The elements are extracted as a Java array.
   *
   * @param elementClass the Java class of array elements
   * @param <E> element type
   * @return reader for List of elements
   */
  static <E> DuckDbRead<java.util.List<E>> readList(Class<E> elementClass) {
    return of(
        (rs, idx) -> {
          java.sql.Array arr = rs.getArray(idx);
          if (arr == null) return null;
          Object[] elements = (Object[]) arr.getArray();
          java.util.List<E> result = new java.util.ArrayList<>(elements.length);
          for (Object elem : elements) {
            @SuppressWarnings("unchecked")
            E typedElem = (E) elem;
            result.add(typedElem);
          }
          return result;
        });
  }

  /**
   * Read a LIST/Array column with element conversion. Use this when DuckDB returns elements in a
   * different type than expected (e.g., java.sql.Timestamp instead of LocalDateTime).
   *
   * @param converter function to convert raw element to target type
   * @param <E> target element type
   * @param <W> wire type (what DuckDB JDBC returns)
   * @return reader for List of elements
   */
  static <E, W> DuckDbRead<java.util.List<E>> readListConverted(
      java.util.function.Function<W, E> converter) {
    return of(
        (rs, idx) -> {
          java.sql.Array arr = rs.getArray(idx);
          if (arr == null) return null;
          Object[] elements = (Object[]) arr.getArray();
          java.util.List<E> result = new java.util.ArrayList<>(elements.length);
          for (Object elem : elements) {
            @SuppressWarnings("unchecked")
            W wireElem = (W) elem;
            result.add(converter.apply(wireElem));
          }
          return result;
        });
  }

  /**
   * Read a STRUCT column. DuckDB returns org.duckdb.DuckDBStruct which implements java.sql.Struct.
   * Returns a Map of field names to values.
   */
  DuckDbRead<java.util.Map<String, Object>> readStruct =
      of(
          (rs, idx) -> {
            Object obj = rs.getObject(idx);
            if (obj == null) return null;
            if (obj instanceof java.sql.Struct) {
              java.sql.Struct struct = (java.sql.Struct) obj;
              // DuckDB's DuckDBStruct has a toString that shows field info
              // The attributes are returned in order, but we can get field names from type name
              String typeName = struct.getSQLTypeName(); // e.g. STRUCT("name" VARCHAR, age INTEGER)
              Object[] attrs = struct.getAttributes();
              java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
              // Parse field names from type name
              String[] fieldNames = parseStructFieldNames(typeName);
              for (int i = 0; i < attrs.length && i < fieldNames.length; i++) {
                result.put(fieldNames[i], attrs[i]);
              }
              return result;
            }
            throw new SQLException("Cannot convert " + obj.getClass() + " to Struct");
          });

  /**
   * Read a MAP column with typed keys and values. DuckDB returns java.util.HashMap directly with
   * proper types.
   *
   * @param keyReader reader for key type
   * @param keyClass the Java class of keys
   * @param valueReader reader for value type
   * @param valueClass the Java class of values
   * @param <K> key type
   * @param <V> value type
   * @return reader for Map
   */
  @SuppressWarnings("unchecked")
  static <K, V> DuckDbRead<java.util.Map<K, V>> readMap(
      DuckDbRead<K> keyReader, Class<K> keyClass, DuckDbRead<V> valueReader, Class<V> valueClass) {
    return of(
        (rs, idx) -> {
          Object obj = rs.getObject(idx);
          if (obj == null) return null;
          if (obj instanceof java.util.Map) {
            java.util.Map<?, ?> rawMap = (java.util.Map<?, ?>) obj;
            java.util.Map<K, V> result = new java.util.LinkedHashMap<>();
            for (var entry : rawMap.entrySet()) {
              K key = keyClass.cast(entry.getKey());
              V value = valueClass.cast(entry.getValue());
              result.put(key, value);
            }
            return result;
          }
          throw new SQLException("Cannot convert " + obj.getClass() + " to Map");
        });
  }

  /**
   * Parse field names from a STRUCT type definition. e.g. STRUCT("name" VARCHAR, age INTEGER) ->
   * ["name", "age"]
   */
  private static String[] parseStructFieldNames(String typeName) {
    // Simple parser for STRUCT(field1 type1, field2 type2, ...)
    if (!typeName.startsWith("STRUCT(") || !typeName.endsWith(")")) {
      return new String[0];
    }
    String inner = typeName.substring(7, typeName.length() - 1);
    java.util.List<String> names = new java.util.ArrayList<>();
    int depth = 0;
    StringBuilder current = new StringBuilder();
    for (char c : inner.toCharArray()) {
      if (c == '(' || c == '[') depth++;
      else if (c == ')' || c == ']') depth--;
      else if (c == ',' && depth == 0) {
        names.add(extractFieldName(current.toString().trim()));
        current = new StringBuilder();
        continue;
      }
      current.append(c);
    }
    if (current.length() > 0) {
      names.add(extractFieldName(current.toString().trim()));
    }
    return names.toArray(new String[0]);
  }

  private static String extractFieldName(String fieldDef) {
    // Field can be: "name" VARCHAR or name VARCHAR
    fieldDef = fieldDef.trim();
    if (fieldDef.startsWith("\"")) {
      int end = fieldDef.indexOf('"', 1);
      if (end > 0) {
        return fieldDef.substring(1, end);
      }
    }
    // Find first space
    int space = fieldDef.indexOf(' ');
    if (space > 0) {
      return fieldDef.substring(0, space);
    }
    return fieldDef;
  }
}
