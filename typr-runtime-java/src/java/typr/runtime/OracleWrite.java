package typr.runtime;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.function.Function;
import oracle.sql.BLOB;
import oracle.sql.CLOB;
import oracle.sql.INTERVALDS;
import oracle.sql.INTERVALYM;
import oracle.sql.RAW;
import oracle.sql.TIMESTAMPTZ;
import typr.data.OracleIntervalDS;
import typr.data.OracleIntervalYM;

/**
 * Describes how to write a value to a {@link PreparedStatement} for Oracle.
 *
 * <p>Similar to MariaWrite but adapted for Oracle-specific types.
 */
public sealed interface OracleWrite<A> extends DbWrite<A>
    permits OracleWrite.Instance, OracleWrite.Structured {
  /**
   * Convert a Java value to its Oracle SQL representation. - OBJECT types → oracle.sql.STRUCT -
   * ARRAY types → oracle.sql.ARRAY - Primitive types → value itself (identity)
   */
  Object toOracleValue(A value, Connection conn) throws SQLException;

  void set(PreparedStatement ps, int idx, A a) throws SQLException;

  OracleWrite<Optional<A>> opt(OracleTypename<A> typename);

  <B> OracleWrite<B> contramap(Function<B, A> f);

  @FunctionalInterface
  interface RawWriter<A> {
    void set(PreparedStatement ps, int index, A a) throws SQLException;
  }

  /** For primitive types - toOracleValue is identity. */
  record Instance<A, U>(RawWriter<U> rawWriter, Function<A, U> f) implements OracleWrite<A> {
    @Override
    public Object toOracleValue(A value, Connection conn) {
      if (value == null) return null;
      return f.apply(value); // Apply function to unwrap Optional or transform value
    }

    @Override
    public void set(PreparedStatement ps, int index, A a) throws SQLException {
      rawWriter.set(ps, index, f.apply(a));
    }

    @Override
    public OracleWrite<Optional<A>> opt(OracleTypename<A> typename) {
      return new Instance<>(
          (ps, index, u) -> {
            if (u == null) ps.setNull(index, java.sql.Types.NULL);
            else set(ps, index, u);
          },
          a -> a.orElse(null));
    }

    @Override
    public <B> OracleWrite<B> contramap(Function<B, A> f) {
      return new Instance<>(rawWriter, f.andThen(this.f));
    }
  }

  /** For structured types (OBJECT, ARRAY) - requires conversion. */
  record Structured<A>(SqlBiFunction<A, Connection, Object> converter, String typename, int sqlType)
      implements OracleWrite<A> {
    @Override
    public Object toOracleValue(A value, Connection conn) throws SQLException {
      if (value == null) return null;
      return converter.apply(value, conn);
    }

    @Override
    public void set(PreparedStatement ps, int index, A value) throws SQLException {
      if (value == null) {
        ps.setNull(index, sqlType, typename);
      } else {
        Object oracleValue = toOracleValue(value, ps.getConnection());
        if (oracleValue == null) {
          ps.setNull(index, sqlType, typename);
        } else {
          ps.setObject(index, oracleValue);
        }
      }
    }

    @Override
    public OracleWrite<Optional<A>> opt(OracleTypename<A> typeName) {
      return new Structured<>(
          (opt, conn) ->
              opt.map(
                      v -> {
                        try {
                          return converter.apply(v, conn);
                        } catch (SQLException e) {
                          throw new RuntimeException(e);
                        }
                      })
                  .orElse(null),
          typename,
          sqlType);
    }

    @Override
    public <B> OracleWrite<B> contramap(Function<B, A> f) {
      return new Structured<>((b, conn) -> converter.apply(f.apply(b), conn), typename, sqlType);
    }
  }

  static <A> OracleWrite<A> primitive(RawWriter<A> rawWriter) {
    return new Instance<>(rawWriter, Function.identity());
  }

  /**
   * Create OracleWrite for structured types (OBJECT, ARRAY). Provide converter function, typename,
   * and SQL type.
   */
  static <A> Structured<A> structured(
      SqlBiFunction<A, Connection, Object> converter, String typename, int sqlType) {
    return new Structured<>(converter, typename, sqlType);
  }

  static <A> OracleWrite<A> passObjectToJdbc() {
    return primitive(PreparedStatement::setObject);
  }

  /**
   * Writer for DATE (includes time component in Oracle). Converts LocalDateTime to
   * java.sql.Timestamp for STRUCT context.
   */
  static OracleWrite<LocalDateTime> writeDate() {
    return structured(
        (localDateTime, conn) -> {
          if (localDateTime == null) return null;
          // Convert LocalDateTime to java.sql.Timestamp
          // Oracle DATE includes time component (unlike SQL standard DATE)
          return Timestamp.valueOf(localDateTime);
        },
        "DATE",
        java.sql.Types.DATE);
  }

  /**
   * Writer for TIMESTAMP (without timezone). Converts LocalDateTime to java.sql.Timestamp for
   * STRUCT context.
   */
  static OracleWrite<LocalDateTime> writeTimestamp() {
    return structured(
        (localDateTime, conn) -> {
          if (localDateTime == null) return null;
          // Convert LocalDateTime to java.sql.Timestamp
          // Use java.sql.Timestamp directly - Oracle STRUCT handles the conversion
          return Timestamp.valueOf(localDateTime);
        },
        "TIMESTAMP",
        java.sql.Types.TIMESTAMP);
  }

  /**
   * Writer for TIMESTAMP WITH LOCAL TIME ZONE. Converts OffsetDateTime to oracle.sql.TIMESTAMPLTZ
   * for STRUCT context. Note: Oracle normalizes this to database timezone and returns in session
   * timezone. We use the instant (UTC) representation and let Oracle handle timezone conversion.
   */
  static OracleWrite<OffsetDateTime> writeTimestampWithLocalTimeZone() {
    return structured(
        (offsetDateTime, conn) -> {
          if (offsetDateTime == null) return null;
          // Convert OffsetDateTime to java.sql.Timestamp (UTC instant)
          // Oracle TIMESTAMPLTZ will store the instant and return in session timezone
          Timestamp timestamp = Timestamp.from(offsetDateTime.toInstant());
          return new oracle.sql.TIMESTAMPLTZ(conn, timestamp);
        },
        "TIMESTAMP WITH LOCAL TIME ZONE",
        java.sql.Types.TIMESTAMP_WITH_TIMEZONE);
  }

  /**
   * Writer for TIMESTAMP WITH TIME ZONE. Converts OffsetDateTime to oracle.sql.TIMESTAMPTZ for
   * STRUCT context. Oracle stores the timezone offset along with the timestamp.
   */
  static OracleWrite<OffsetDateTime> writeTimestampWithTimeZone() {
    return structured(
        (offsetDateTime, conn) -> {
          if (offsetDateTime == null) return null;
          // Convert OffsetDateTime to java.sql.Timestamp
          Timestamp timestamp = Timestamp.from(offsetDateTime.toInstant());
          // Create oracle.sql.TIMESTAMPTZ with timezone information
          // TIMESTAMPTZ constructor: TIMESTAMPTZ(Connection, Timestamp, Calendar)
          // We need to format with timezone offset
          java.util.Calendar calendar = java.util.Calendar.getInstance();
          calendar.setTimeZone(java.util.TimeZone.getTimeZone(offsetDateTime.getOffset()));
          calendar.setTimeInMillis(timestamp.getTime());
          return new TIMESTAMPTZ(conn, timestamp, calendar);
        },
        "TIMESTAMP WITH TIME ZONE",
        java.sql.Types.TIMESTAMP_WITH_TIMEZONE);
  }

  /**
   * Writer for INTERVAL YEAR TO MONTH. Converts OracleIntervalYM to oracle.sql.INTERVALYM for
   * STRUCT context.
   */
  static OracleWrite<OracleIntervalYM> writeIntervalYearToMonth() {
    return structured(
        (interval, conn) -> {
          if (interval == null) return null;
          // Convert to Oracle format string (+02-05) and create oracle.sql.INTERVALYM
          return new INTERVALYM(interval.toOracleFormat());
        },
        "INTERVAL YEAR TO MONTH",
        java.sql.Types.OTHER);
  }

  /**
   * Writer for INTERVAL DAY TO SECOND. Converts OracleIntervalDS to oracle.sql.INTERVALDS for
   * STRUCT context.
   */
  static OracleWrite<OracleIntervalDS> writeIntervalDayToSecond() {
    return structured(
        (interval, conn) -> {
          if (interval == null) return null;
          // Convert to Oracle format string (+03 14:30:45.123456) and create oracle.sql.INTERVALDS
          return new INTERVALDS(interval.toOracleFormat());
        },
        "INTERVAL DAY TO SECOND",
        java.sql.Types.OTHER);
  }

  /**
   * Writer for RAW (variable-length binary data). Converts byte[] to oracle.sql.RAW for STRUCT
   * context.
   */
  static OracleWrite<byte[]> writeRaw() {
    return structured(
        (bytes, conn) -> {
          if (bytes == null) return null;
          // Convert byte[] to oracle.sql.RAW
          return new RAW(bytes);
        },
        "RAW",
        java.sql.Types.VARBINARY);
  }

  /**
   * Writer for BLOB (large binary object). Converts byte[] to oracle.sql.BLOB for STRUCT context.
   */
  static OracleWrite<byte[]> writeBlobForStruct() {
    return structured(
        (bytes, conn) -> {
          if (bytes == null) return null;
          // Create temporary BLOB and populate it
          BLOB blob = BLOB.createTemporary(conn, true, BLOB.DURATION_SESSION);
          blob.setBytes(1, bytes);
          return blob;
        },
        "BLOB",
        java.sql.Types.BLOB);
  }

  /**
   * Writer for CLOB (character large object). Converts String to oracle.sql.CLOB for STRUCT
   * context.
   */
  static OracleWrite<String> writeClobForStruct() {
    return structured(
        (str, conn) -> {
          if (str == null) return null;
          // Create temporary CLOB and populate it
          CLOB clob = CLOB.createTemporary(conn, true, CLOB.DURATION_SESSION);
          clob.setString(1, str);
          return clob;
        },
        "CLOB",
        java.sql.Types.CLOB);
  }

  // Basic type writers
  OracleWrite<String> writeString = primitive(PreparedStatement::setString);
  OracleWrite<NonEmptyString> writeNonEmptyString =
      primitive(
          (ps, idx, nes) -> {
            ps.setString(idx, nes.value());
          });

  static OracleWrite<PaddedString> writePaddedString() {
    return primitive(
        (ps, idx, padded) -> {
          ps.setString(idx, padded.value());
        });
  }

  OracleWrite<Boolean> writeBoolean = primitive(PreparedStatement::setBoolean);
  OracleWrite<Byte> writeByte = primitive(PreparedStatement::setByte);
  OracleWrite<Short> writeShort = primitive(PreparedStatement::setShort);
  OracleWrite<Integer> writeInteger = primitive(PreparedStatement::setInt);
  OracleWrite<Long> writeLong = primitive(PreparedStatement::setLong);
  OracleWrite<Float> writeFloat = primitive(PreparedStatement::setFloat);
  OracleWrite<Double> writeDouble = primitive(PreparedStatement::setDouble);
  OracleWrite<BigDecimal> writeBigDecimal = primitive(PreparedStatement::setBigDecimal);
  OracleWrite<byte[]> writeByteArray = primitive(PreparedStatement::setBytes);
  OracleWrite<NonEmptyBlob> writeNonEmptyBlob =
      primitive(
          (ps, idx, neb) -> {
            ps.setBytes(idx, neb.value());
          });

  // BLOB writer
  OracleWrite<byte[]> writeBlob =
      primitive(
          (ps, idx, bytes) -> {
            ps.setBlob(idx, new java.io.ByteArrayInputStream(bytes), bytes.length);
          });

  // CLOB writer
  OracleWrite<String> writeClob =
      primitive(
          (ps, idx, str) -> {
            ps.setClob(idx, new java.io.StringReader(str), str.length());
          });
}
