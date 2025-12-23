package typr.runtime;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.postgresql.jdbc.PgArray;
import org.postgresql.util.PGobject;
import typr.data.Json;
import typr.data.Jsonb;
import typr.data.Money;
import typr.runtime.internal.arrayMap;

/**
 * Describes how to read a column from a {@link ResultSet}
 *
 * <p>Note that the implementation is a bit more complex than you would expect. This is because we
 * need to check {@link ResultSet#wasNull()} "in the middle" of extracting data. <br>
 * <br>
 * Correct use of {@code Column} <b>requires</b> use of either
 *
 * <ul>
 *   <li>- pre-defined instances
 *   <li>- or `Column.instance` with a provided function which does not blow up if the value from
 *       the {@link ResultSet} is {@code null}
 * </ul>
 *
 * Then you create derived instances with {@code map} and/or {@code opt}
 */
public sealed interface PgRead<A> extends DbRead<A>
    permits PgRead.NonNullable, PgRead.Nullable, KotlinNullablePgRead {
  A read(ResultSet rs, int col) throws SQLException;

  <B> PgRead<B> map(SqlFunction<A, B> f);

  /** Derive a `Column` which allows nullable values */
  PgRead<Optional<A>> opt();

  @FunctionalInterface
  interface RawRead<A> {
    A apply(ResultSet rs, int column) throws SQLException;
  }

  /**
   * Create an instance of {@link PgRead} from a function that reads a value from a result set.
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

  final class NonNullable<A> implements PgRead<A> {
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
            // this looks like map, but there is a checked exception
            if (maybeA.isEmpty()) return Optional.empty();
            return Optional.of(f.apply(maybeA.get()));
          });
    }

    @Override
    public PgRead<Optional<A>> opt() {
      return new Nullable<>(readNullable);
    }
  }

  final class Nullable<A> implements PgRead<Optional<A>>, DbRead.Nullable {
    final RawRead<Optional<A>> readNullable;

    public Nullable(RawRead<Optional<A>> readNullable) {
      this.readNullable = readNullable;
    }

    @Override
    public Optional<A> read(ResultSet rs, int col) throws SQLException {
      return readNullable.apply(rs, col);
    }

    @Override
    public <B> PgRead<B> map(SqlFunction<Optional<A>, B> f) {
      // note that there is an implicit assertion here -
      // we're not adding another level of optionality and we *know* we have a `B` even if there was
      // no `A`
      // note that `B` may very well be `Optional<>` itself
      // Use ofNullable to support Kotlin nullable types where B can be null
      return new NonNullable<>((rs, col) -> Optional.ofNullable(f.apply(read(rs, col))));
    }

    // just here for completeness, doesn't make much sense
    @Override
    public Nullable<Optional<A>> opt() {
      return new Nullable<>(
          (rs, col) -> {
            Optional<A> maybeA = readNullable.apply(rs, col);
            // avoid `Some(None)`
            if (maybeA.isEmpty()) return Optional.empty();
            return Optional.of(maybeA);
          });
    }
  }

  static <A> NonNullable<A> castJdbcObjectTo(Class<A> cls) {
    return of((rs, i) -> cls.cast(rs.getObject(i)));
  }

  PgRead<PgArray> readPgArray = of((rs, i) -> (PgArray) rs.getArray(i));

  @SuppressWarnings("unchecked")
  static <A> PgRead<A[]> massageJdbcArrayTo(Class<A[]> arrayCls) {
    return readPgArray.map(
        sqlArray -> {
          Object arrayObj = sqlArray.getArray();
          // if the array is already of the correct type, just return it
          if (arrayCls.isInstance(arrayObj)) return arrayCls.cast(arrayObj);
          // if the array is an Object[], we need to copy elements manually
          Object[] array = (Object[]) arrayObj;
          Class<?> componentType = arrayCls.getComponentType();
          A[] result = (A[]) Array.newInstance(componentType, array.length);
          for (int i = 0; i < array.length; i++) {
            result[i] = (A) array[i];
          }
          return result;
        });
  }

  /**
   * Read an array where JDBC driver returns Object[] containing elements that need casting. Used
   * for PostgreSQL geometric types (box[], circle[], etc.) where driver returns PGobject[].
   */
  @SuppressWarnings("unchecked")
  static <A> PgRead<A[]> castJdbcArrayTo(Class<A> elementCls) {
    return readPgArray.map(
        sqlArray -> {
          Object[] array = (Object[]) sqlArray.getArray();
          A[] result = (A[]) Array.newInstance(elementCls, array.length);
          for (int i = 0; i < array.length; i++) {
            result[i] = elementCls.cast(array[i]);
          }
          return result;
        });
  }

  @SuppressWarnings("unchecked")
  static <T> PgRead<T[]> pgObjectArray(Function<String, T> fromString, Class<T> clazz) {
    return readPgArray.map(
        sqlArray -> {
          Object[] objects = (Object[]) sqlArray.getArray();
          T[] array = (T[]) Array.newInstance(clazz, objects.length);
          for (int i = 0; i < objects.length; i++) {
            PGobject object = (PGobject) objects[i];
            array[i] = fromString.apply(object.getValue());
          }
          return array;
        });
  }

  static PgRead<String> pgObject(String sqlType) {
    return PgRead.of(
        (rs, i) -> {
          PGobject object = (PGobject) rs.getObject(i);
          if (object == null) return null;
          if (!object.getType().equals(sqlType)) {
            throw new SQLException("Expected " + sqlType + " but got " + object.getType());
          }
          return object.getValue();
        });
  }

  PgRead<OffsetDateTime> readOffsetDateTime =
      of((rs, idx) -> rs.getObject(idx, OffsetDateTime.class));
  PgRead<java.sql.Timestamp[]> readTimestampArray = massageJdbcArrayTo(java.sql.Timestamp[].class);
  PgRead<java.sql.Date[]> readDateArray = massageJdbcArrayTo(java.sql.Date[].class);
  PgRead<String> readString = of(ResultSet::getString);
  PgRead<String[]> readStringArray = PgRead.massageJdbcArrayTo(String[].class);

  PgRead<BigDecimal> readBigDecimal = of(ResultSet::getBigDecimal);
  PgRead<BigDecimal[]> readBigDecimalArray = PgRead.massageJdbcArrayTo(BigDecimal[].class);
  PgRead<Boolean> readBoolean = of(ResultSet::getBoolean);
  PgRead<Boolean[]> readBooleanArray = PgRead.massageJdbcArrayTo(Boolean[].class);
  PgRead<Byte> readByte = of(ResultSet::getByte);
  PgRead<byte[]> readByteArray = castJdbcObjectTo(byte[].class);
  PgRead<Double> readDouble = of(ResultSet::getDouble);
  PgRead<Double[]> readDoubleArray = PgRead.massageJdbcArrayTo(Double[].class);
  PgRead<Float> readFloat = of(ResultSet::getFloat);
  PgRead<Float[]> readFloatArray = PgRead.massageJdbcArrayTo(Float[].class);
  PgRead<Instant> readInstant = readOffsetDateTime.map(OffsetDateTime::toInstant);
  PgRead<Instant[]> readInstantArray =
      readTimestampArray.map(
          ts -> Arrays.stream(ts).map(java.sql.Timestamp::toInstant).toArray(Instant[]::new));
  PgRead<Integer> readInteger = of(ResultSet::getInt);
  PgRead<Integer[]> readIntegerArray = PgRead.massageJdbcArrayTo(Integer[].class);
  PgRead<Json[]> readJsonArray =
      PgRead.readStringArray.map(as -> arrayMap.map(as, Json::new, Json.class));
  PgRead<Jsonb[]> readJsonbArray =
      PgRead.readStringArray.map(as -> arrayMap.map(as, Jsonb::new, Jsonb.class));
  PgRead<LocalDate> readLocalDate = of((rs, idx) -> rs.getObject(idx, LocalDate.class));
  PgRead<LocalDate[]> readLocalDateArray =
      readDateArray.map(
          dates -> Arrays.stream(dates).map(java.sql.Date::toLocalDate).toArray(LocalDate[]::new));
  PgRead<LocalDateTime> readLocalDateTime = of((rs, idx) -> rs.getObject(idx, LocalDateTime.class));
  PgRead<LocalDateTime[]> readLocalDateTimeArray =
      readTimestampArray.map(
          ts ->
              Arrays.stream(ts)
                  .map(java.sql.Timestamp::toLocalDateTime)
                  .toArray(LocalDateTime[]::new));
  PgRead<LocalTime> readLocalTime = of((rs, idx) -> rs.getObject(idx, LocalTime.class));
  PgRead<LocalTime[]> readLocalTimeArray = readString.map(Impl::parseLocalTimeArray);
  PgRead<Long> readLong = of(ResultSet::getLong);
  PgRead<Long[]> readLongArray = PgRead.massageJdbcArrayTo(Long[].class);
  PgRead<OffsetTime> readOffsetTime = of((rs, idx) -> rs.getObject(idx, OffsetTime.class));
  PgRead<OffsetTime[]> readOffsetTimeArray = readString.map(Impl::parseOffsetTimeArray);
  PgRead<Short> readShort = of(ResultSet::getShort);
  PgRead<Short[]> readShortArray = PgRead.massageJdbcArrayTo(Short[].class);

  // Unboxed (primitive) array readers - convert from boxed arrays returned by JDBC
  PgRead<boolean[]> readBooleanArrayUnboxed = readBooleanArray.map(Impl::unboxBooleanArray);
  PgRead<short[]> readShortArrayUnboxed = readShortArray.map(Impl::unboxShortArray);
  PgRead<int[]> readIntArrayUnboxed = readIntegerArray.map(Impl::unboxIntArray);
  PgRead<long[]> readLongArrayUnboxed = readLongArray.map(Impl::unboxLongArray);
  PgRead<float[]> readFloatArrayUnboxed = readFloatArray.map(Impl::unboxFloatArray);
  PgRead<double[]> readDoubleArrayUnboxed = readDoubleArray.map(Impl::unboxDoubleArray);

  PgRead<UUID> readUUID = readString.map(UUID::fromString);
  PgRead<Money[]> readMoneyArray =
      PgRead.readString.map(
          str -> {
            if (str.equals("{}")) return new Money[0];
            return arrayMap.map(
                str.substring(1, str.length() - 1).split(","), Money::new, Money.class);
          });
  PgRead<Map<String, String>> readMapStringString =
      PgRead.of(
          (rs, i) -> {
            var obj = rs.getObject(i);
            if (obj == null) return null;
            return (Map<String, String>) obj;
          });

  interface Impl {
    // postgres driver throws away all precision after whole seconds !?!
    static LocalTime[] parseLocalTimeArray(String str) {
      if (str == null) return null;
      if (str.equals("{}")) return new LocalTime[0];
      if (str.charAt(0) != '{' || str.charAt(str.length() - 1) != '}')
        throw new IllegalArgumentException("Invalid array format");
      String[] strings = str.substring(1, str.length() - 1).split(",");
      LocalTime[] ret = new LocalTime[strings.length];
      for (int i = 0; i < strings.length; i++) {
        ret[i] = LocalTime.parse(strings[i]);
      }
      return ret;
    }

    DateTimeFormatter offsetTimeParser =
        new DateTimeFormatterBuilder()
            .appendPattern("HH:mm:ss")
            .appendFraction(ChronoField.MICRO_OF_SECOND, 0, 6, true)
            .appendPattern("X")
            .toFormatter();

    static OffsetTime[] parseOffsetTimeArray(String str) {
      if (str == null) return null;
      if (str.equals("{}")) return new OffsetTime[0];
      if (str.charAt(0) != '{' || str.charAt(str.length() - 1) != '}')
        throw new IllegalArgumentException("Invalid array format");
      String[] strings = str.substring(1, str.length() - 1).split(",");
      var ret = new OffsetTime[strings.length];
      for (int i = 0; i < strings.length; i++) {
        ret[i] = OffsetTime.parse(strings[i], offsetTimeParser);
      }
      return ret;
    }

    // Unboxing methods - convert boxed arrays to primitive arrays
    static boolean[] unboxBooleanArray(Boolean[] boxed) {
      if (boxed == null) return null;
      boolean[] unboxed = new boolean[boxed.length];
      for (int i = 0; i < boxed.length; i++) {
        unboxed[i] = boxed[i];
      }
      return unboxed;
    }

    static short[] unboxShortArray(Short[] boxed) {
      if (boxed == null) return null;
      short[] unboxed = new short[boxed.length];
      for (int i = 0; i < boxed.length; i++) {
        unboxed[i] = boxed[i];
      }
      return unboxed;
    }

    static int[] unboxIntArray(Integer[] boxed) {
      if (boxed == null) return null;
      int[] unboxed = new int[boxed.length];
      for (int i = 0; i < boxed.length; i++) {
        unboxed[i] = boxed[i];
      }
      return unboxed;
    }

    static long[] unboxLongArray(Long[] boxed) {
      if (boxed == null) return null;
      long[] unboxed = new long[boxed.length];
      for (int i = 0; i < boxed.length; i++) {
        unboxed[i] = boxed[i];
      }
      return unboxed;
    }

    static float[] unboxFloatArray(Float[] boxed) {
      if (boxed == null) return null;
      float[] unboxed = new float[boxed.length];
      for (int i = 0; i < boxed.length; i++) {
        unboxed[i] = boxed[i];
      }
      return unboxed;
    }

    static double[] unboxDoubleArray(Double[] boxed) {
      if (boxed == null) return null;
      double[] unboxed = new double[boxed.length];
      for (int i = 0; i < boxed.length; i++) {
        unboxed[i] = boxed[i];
      }
      return unboxed;
    }

    // Boxing methods - convert primitive arrays to boxed arrays
    static Boolean[] boxBooleanArray(boolean[] unboxed) {
      if (unboxed == null) return null;
      Boolean[] boxed = new Boolean[unboxed.length];
      for (int i = 0; i < unboxed.length; i++) {
        boxed[i] = unboxed[i];
      }
      return boxed;
    }

    static Short[] boxShortArray(short[] unboxed) {
      if (unboxed == null) return null;
      Short[] boxed = new Short[unboxed.length];
      for (int i = 0; i < unboxed.length; i++) {
        boxed[i] = unboxed[i];
      }
      return boxed;
    }

    static Integer[] boxIntArray(int[] unboxed) {
      if (unboxed == null) return null;
      Integer[] boxed = new Integer[unboxed.length];
      for (int i = 0; i < unboxed.length; i++) {
        boxed[i] = unboxed[i];
      }
      return boxed;
    }

    static Long[] boxLongArray(long[] unboxed) {
      if (unboxed == null) return null;
      Long[] boxed = new Long[unboxed.length];
      for (int i = 0; i < unboxed.length; i++) {
        boxed[i] = unboxed[i];
      }
      return boxed;
    }

    static Float[] boxFloatArray(float[] unboxed) {
      if (unboxed == null) return null;
      Float[] boxed = new Float[unboxed.length];
      for (int i = 0; i < unboxed.length; i++) {
        boxed[i] = unboxed[i];
      }
      return boxed;
    }

    static Double[] boxDoubleArray(double[] unboxed) {
      if (unboxed == null) return null;
      Double[] boxed = new Double[unboxed.length];
      for (int i = 0; i < unboxed.length; i++) {
        boxed[i] = unboxed[i];
      }
      return boxed;
    }
  }
}
