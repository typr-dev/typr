package typo.runtime;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Describes how to read a column from a {@link ResultSet} for SQL Server.
 *
 * <p>Similar to MariaRead but adapted for SQL Server-specific types.
 */
public sealed interface SqlServerRead<A> extends DbRead<A>
    permits SqlServerRead.NonNullable, SqlServerRead.Nullable, KotlinNullableSqlServerRead {
  A read(ResultSet rs, int col) throws SQLException;

  <B> SqlServerRead<B> map(SqlFunction<A, B> f);

  /** Derive a SqlServerRead which allows nullable values */
  SqlServerRead<Optional<A>> opt();

  @FunctionalInterface
  interface RawRead<A> {
    A apply(ResultSet rs, int column) throws SQLException;
  }

  /**
   * Create an instance of {@link SqlServerRead} from a function that reads a value from a result
   * set.
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

  final class NonNullable<A> implements SqlServerRead<A> {
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
    public SqlServerRead<Optional<A>> opt() {
      return new Nullable<>(readNullable);
    }
  }

  final class Nullable<A> implements SqlServerRead<Optional<A>>, DbRead.Nullable {
    final RawRead<Optional<A>> readNullable;

    public Nullable(RawRead<Optional<A>> readNullable) {
      this.readNullable = readNullable;
    }

    @Override
    public Optional<A> read(ResultSet rs, int col) throws SQLException {
      return readNullable.apply(rs, col);
    }

    @Override
    public <B> SqlServerRead<B> map(SqlFunction<Optional<A>, B> f) {
      return new NonNullable<>((rs, col) -> Optional.of(f.apply(read(rs, col))));
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

  /**
   * Read a value by requesting a specific class from JDBC. This uses rs.getObject(i, cls) which
   * allows the JDBC driver to do proper type conversion.
   */
  static <A> NonNullable<A> getObjectAs(Class<A> cls) {
    return of((rs, i) -> rs.getObject(i, cls));
  }

  // ==================== Basic Type Readers ====================

  SqlServerRead<String> readString = of(ResultSet::getString);
  SqlServerRead<Boolean> readBoolean = of(ResultSet::getBoolean);

  // SQL Server TINYINT is UNSIGNED (0-255), so we read as Short
  SqlServerRead<Short> readShort = of(ResultSet::getShort);
  SqlServerRead<Integer> readInteger = of(ResultSet::getInt);
  SqlServerRead<Long> readLong = of(ResultSet::getLong);
  SqlServerRead<Float> readFloat = of(ResultSet::getFloat);
  SqlServerRead<Double> readDouble = of(ResultSet::getDouble);
  SqlServerRead<BigDecimal> readBigDecimal = of(ResultSet::getBigDecimal);

  // Binary types
  SqlServerRead<byte[]> readByteArray = of(ResultSet::getBytes);

  // ==================== Date/Time Readers ====================

  SqlServerRead<LocalDate> readDate = of((rs, idx) -> rs.getObject(idx, LocalDate.class));
  SqlServerRead<LocalTime> readTime = of((rs, idx) -> rs.getObject(idx, LocalTime.class));

  // DATETIME, SMALLDATETIME, DATETIME2
  SqlServerRead<LocalDateTime> readTimestamp =
      of((rs, idx) -> rs.getObject(idx, LocalDateTime.class));

  // DATETIMEOFFSET - SQL Server specific!
  SqlServerRead<OffsetDateTime> readOffsetDateTime =
      of(
          (rs, idx) -> {
            // SQL Server JDBC driver returns microsoft.sql.DateTimeOffset
            // We need to convert it to java.time.OffsetDateTime
            Object obj = rs.getObject(idx);
            if (obj == null) return null;

            // Modern JDBC drivers support direct conversion to OffsetDateTime
            return rs.getObject(idx, OffsetDateTime.class);
          });

  // ==================== Special Types ====================

  // UNIQUEIDENTIFIER (UUID/GUID)
  SqlServerRead<UUID> readUUID =
      of(
          (rs, idx) -> {
            String str = rs.getString(idx);
            if (str == null) return null;
            return UUID.fromString(str);
          });

  // XML
  SqlServerRead<String> readXml =
      of(
          (rs, idx) -> {
            java.sql.SQLXML sqlxml = rs.getSQLXML(idx);
            if (sqlxml == null) return null;
            return sqlxml.getString();
          });

  // JSON - SQL Server stores JSON as NVARCHAR
  SqlServerRead<String> readJson = readString;

  // HIERARCHYID - read as string representation
  SqlServerRead<String> readHierarchyId = readString;

  // SQL_VARIANT - read as Object
  SqlServerRead<Object> readObject = of(ResultSet::getObject);

  // GEOGRAPHY and GEOMETRY - use JDBC driver's spatial classes
  // Read as bytes and deserialize (getObject doesn't support these types)
  // Handle NULL values properly - deserialize() doesn't accept null
  SqlServerRead<com.microsoft.sqlserver.jdbc.Geography> readGeography =
      of(
          (rs, idx) -> {
            byte[] bytes = rs.getBytes(idx);
            return bytes == null ? null : com.microsoft.sqlserver.jdbc.Geography.deserialize(bytes);
          });
  SqlServerRead<com.microsoft.sqlserver.jdbc.Geometry> readGeometry =
      of(
          (rs, idx) -> {
            byte[] bytes = rs.getBytes(idx);
            return bytes == null ? null : com.microsoft.sqlserver.jdbc.Geometry.deserialize(bytes);
          });

  // VECTOR - SQL Server 2025 vector type
  // Read as byte[] for now, or parse as float[] if needed
  SqlServerRead<byte[]> readVector = readByteArray;
}
