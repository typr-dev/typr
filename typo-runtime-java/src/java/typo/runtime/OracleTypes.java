package typo.runtime;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.function.Function;
import typo.data.Json;
import typo.data.OracleIntervalDS;
import typo.data.OracleIntervalYM;

/**
 * Oracle type definitions for the typo-runtime-java library.
 *
 * <p>This interface provides type codecs for all Oracle data types.
 *
 * <p>Oracle Type System Reference: - NUMBER(p,s): Universal numeric type -
 * BINARY_FLOAT/BINARY_DOUBLE: IEEE 754 floating point - VARCHAR2/CHAR/NVARCHAR2/NCHAR: Character
 * types - CLOB/NCLOB/BLOB: Large object types - DATE: Date with time (second precision) -
 * TIMESTAMP: Date with fractional seconds - TIMESTAMP WITH TIME ZONE / WITH LOCAL TIME ZONE:
 * Timezone-aware timestamps - INTERVAL YEAR TO MONTH / INTERVAL DAY TO SECOND: Interval types -
 * RAW: Variable-length binary data - ROWID/UROWID: Row identifier types - XMLTYPE: XML document
 * storage - JSON: Native JSON type (21c+)
 */
public interface OracleTypes {
  // ═══════════════════════════════════════════════════════════════════════════
  // Numeric Types
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * NUMBER - Oracle's universal numeric type (no precision/scale specified). Can hold integers,
   * fixed-point, and floating-point values.
   */
  OracleType<BigDecimal> number =
      OracleType.of(
          "NUMBER", OracleRead.readBigDecimal, OracleWrite.writeBigDecimal, OracleJson.numeric);

  /** NUMBER(p,0) where p<=9 -> Integer */
  OracleType<Integer> numberInt =
      OracleType.of(
          "NUMBER", OracleRead.readNumberAsInt, OracleWrite.writeInteger, OracleJson.int4);

  /**
   * NUMBER(p,0) where 9
   *
   * <p<=18 ->Long
   */
  OracleType<Long> numberLong =
      OracleType.of("NUMBER", OracleRead.readNumberAsLong, OracleWrite.writeLong, OracleJson.int8);

  /** NUMBER with precision and scale factory methods */
  static OracleType<BigDecimal> number(int precision) {
    return OracleType.of(
        OracleTypename.of("NUMBER", precision),
        OracleRead.readBigDecimal,
        OracleWrite.writeBigDecimal,
        OracleJson.numeric);
  }

  static OracleType<BigDecimal> number(int precision, int scale) {
    return OracleType.of(
        OracleTypename.of("NUMBER", precision, scale),
        OracleRead.readBigDecimal,
        OracleWrite.writeBigDecimal,
        OracleJson.numeric);
  }

  static OracleType<Integer> numberAsInt(int precision) {
    return OracleType.of(
        OracleTypename.of("NUMBER", precision),
        OracleRead.readInteger,
        OracleWrite.writeInteger,
        OracleJson.int4);
  }

  static OracleType<Long> numberAsLong(int precision) {
    return OracleType.of(
        OracleTypename.of("NUMBER", precision),
        OracleRead.readLong,
        OracleWrite.writeLong,
        OracleJson.int8);
  }

  /** BINARY_FLOAT - 32-bit IEEE 754 floating point. Range: +/-1.17549E-38 to +/-3.40282E+38 */
  OracleType<Float> binaryFloat =
      OracleType.of(
          "BINARY_FLOAT", OracleRead.readBinaryFloat, OracleWrite.writeFloat, OracleJson.float4);

  /** BINARY_DOUBLE - 64-bit IEEE 754 floating point. Range: +/-2.22507E-308 to +/-1.79769E+308 */
  OracleType<Double> binaryDouble =
      OracleType.of(
          "BINARY_DOUBLE", OracleRead.readBinaryDouble, OracleWrite.writeDouble, OracleJson.float8);

  /**
   * FLOAT(precision) - ANSI float type (actually maps to NUMBER internally). Binary precision 1-126
   * (approximately 1-38 decimal digits).
   */
  OracleType<Double> float_ =
      OracleType.of("FLOAT", OracleRead.readDouble, OracleWrite.writeDouble, OracleJson.float8);

  static OracleType<Double> float_(int binaryPrecision) {
    return OracleType.of(
        OracleTypename.of("FLOAT", binaryPrecision),
        OracleRead.readDouble,
        OracleWrite.writeDouble,
        OracleJson.float8);
  }

  /** INTEGER - Equivalent to NUMBER(38,0). Used for ANSI compatibility. */
  OracleType<BigDecimal> integer = number.renamed("INTEGER");

  /** SMALLINT - Equivalent to NUMBER(38,0). Used for ANSI compatibility. */
  OracleType<BigDecimal> smallint = number.renamed("SMALLINT");

  /** REAL - Equivalent to FLOAT(63). Used for ANSI compatibility. */
  OracleType<Double> real = float_.renamed("REAL");

  /** DOUBLE PRECISION - Equivalent to FLOAT(126). Used for ANSI compatibility. */
  OracleType<Double> doublePrecision = float_.renamed("DOUBLE PRECISION");

  // ═══════════════════════════════════════════════════════════════════════════
  // Character Types
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * VARCHAR2(n) - Variable-length character string. Max 4000 bytes (or 32767 with
   * MAX_STRING_SIZE=EXTENDED).
   */
  OracleType<String> varchar2 =
      OracleType.of("VARCHAR2", OracleRead.readString, OracleWrite.writeString, OracleJson.text);

  static OracleType<String> varchar2(int maxLength) {
    return OracleType.of(
        OracleTypename.of("VARCHAR2", maxLength),
        OracleRead.readString,
        OracleWrite.writeString,
        OracleJson.text);
  }

  /**
   * VARCHAR2(n) - Variable-length character string, using NonEmptyString. For NOT NULL columns -
   * guarantees non-empty values.
   */
  static OracleType<NonEmptyString> varchar2NonEmpty(int maxLength) {
    return OracleType.of(
        OracleTypename.of("VARCHAR2", maxLength),
        OracleRead.readNonEmptyString,
        OracleWrite.writeNonEmptyString,
        OracleJson.nonEmptyString);
  }

  /** CHAR(n) - Fixed-length character string, blank-padded. Max 2000 bytes. */
  OracleType<String> char_ =
      OracleType.of("CHAR", OracleRead.readString, OracleWrite.writeString, OracleJson.text);

  static OracleType<String> char_(int length) {
    return OracleType.of(
        OracleTypename.of("CHAR", length),
        OracleRead.readString,
        OracleWrite.writeString,
        OracleJson.text);
  }

  /**
   * CHAR(n) - Fixed-length character string, using PaddedString. For NOT NULL columns - guarantees
   * non-empty, padded values.
   */
  static OracleType<PaddedString> charPadded(int length) {
    return OracleType.of(
        OracleTypename.of("CHAR", length),
        OracleRead.readPaddedString(length),
        OracleWrite.writePaddedString(),
        OracleJson.paddedString(length));
  }

  /**
   * NVARCHAR2(n) - Variable-length National character string. Uses AL16UTF16 or UTF8 encoding based
   * on national character set.
   */
  OracleType<String> nvarchar2 =
      OracleType.of("NVARCHAR2", OracleRead.readString, OracleWrite.writeString, OracleJson.text);

  static OracleType<String> nvarchar2(int maxLength) {
    return OracleType.of(
        OracleTypename.of("NVARCHAR2", maxLength),
        OracleRead.readString,
        OracleWrite.writeString,
        OracleJson.text);
  }

  /**
   * NVARCHAR2(n) - Variable-length National character string, using NonEmptyString. For NOT NULL
   * columns - guarantees non-empty values.
   */
  static OracleType<NonEmptyString> nvarchar2NonEmpty(int maxLength) {
    return OracleType.of(
        OracleTypename.of("NVARCHAR2", maxLength),
        OracleRead.readNonEmptyString,
        OracleWrite.writeNonEmptyString,
        OracleJson.nonEmptyString);
  }

  /** NCHAR(n) - Fixed-length National character string. */
  OracleType<String> nchar =
      OracleType.of("NCHAR", OracleRead.readString, OracleWrite.writeString, OracleJson.text);

  static OracleType<String> nchar(int length) {
    return OracleType.of(
        OracleTypename.of("NCHAR", length),
        OracleRead.readString,
        OracleWrite.writeString,
        OracleJson.text);
  }

  /**
   * NCHAR(n) - Fixed-length National character string, using PaddedString. For NOT NULL columns -
   * guarantees non-empty, padded values.
   */
  static OracleType<PaddedString> ncharPadded(int length) {
    return OracleType.of(
        OracleTypename.of("NCHAR", length),
        OracleRead.readPaddedString(length),
        OracleWrite.writePaddedString(),
        OracleJson.paddedString(length));
  }

  /** CLOB - Character Large Object. Up to (4GB - 1) * DB_BLOCK_SIZE. */
  OracleType<String> clob =
      OracleType.of("CLOB", OracleRead.readClob, OracleWrite.writeClobForStruct(), OracleJson.text);

  /**
   * CLOB - Character Large Object, using NonEmptyString. For NOT NULL columns - guarantees
   * non-empty values.
   */
  OracleType<NonEmptyString> clobNonEmpty =
      OracleType.of(
          "CLOB",
          OracleRead.readClob.map(NonEmptyString::force),
          OracleWrite.writeClob.contramap(NonEmptyString::value),
          OracleJson.nonEmptyString);

  /** NCLOB - National Character Large Object. */
  OracleType<String> nclob =
      OracleType.of(
          "NCLOB", OracleRead.readClob, OracleWrite.writeClobForStruct(), OracleJson.text);

  /**
   * NCLOB - National Character Large Object, using NonEmptyString. For NOT NULL columns -
   * guarantees non-empty values.
   */
  OracleType<NonEmptyString> nclobNonEmpty =
      OracleType.of(
          "NCLOB",
          OracleRead.readClob.map(NonEmptyString::force),
          OracleWrite.writeClob.contramap(NonEmptyString::value),
          OracleJson.nonEmptyString);

  /**
   * LONG - Deprecated character type (for backward compatibility only). Use CLOB instead for new
   * applications.
   */
  OracleType<String> long_ =
      OracleType.of("LONG", OracleRead.readString, OracleWrite.writeString, OracleJson.text);

  // ═══════════════════════════════════════════════════════════════════════════
  // Binary Types
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * RAW(n) - Variable-length raw binary data. Max 2000 bytes (or 32767 with
   * MAX_STRING_SIZE=EXTENDED).
   */
  OracleType<byte[]> raw =
      OracleType.of("RAW", OracleRead.readByteArray, OracleWrite.writeRaw(), OracleJson.bytea);

  static OracleType<byte[]> raw(int maxLength) {
    return OracleType.of(
        OracleTypename.of("RAW", maxLength),
        OracleRead.readByteArray,
        OracleWrite.writeRaw(),
        OracleJson.bytea);
  }

  /**
   * RAW(n) - Variable-length raw binary data, using NonEmptyBlob. For NOT NULL columns - guarantees
   * non-empty values.
   */
  static OracleType<NonEmptyBlob> rawNonEmpty(int maxLength) {
    return OracleType.of(
        OracleTypename.of("RAW", maxLength),
        OracleRead.readNonEmptyBlob,
        OracleWrite.writeNonEmptyBlob,
        OracleJson.nonEmptyBlob);
  }

  /** BLOB - Binary Large Object. Up to (4GB - 1) * DB_BLOCK_SIZE. */
  OracleType<byte[]> blob =
      OracleType.of(
          "BLOB", OracleRead.readBlob, OracleWrite.writeBlobForStruct(), OracleJson.bytea);

  /**
   * BLOB - Binary Large Object, using NonEmptyBlob. For NOT NULL columns - guarantees non-empty
   * values.
   */
  OracleType<NonEmptyBlob> blobNonEmpty =
      OracleType.of(
          "BLOB",
          OracleRead.readBlob.map(NonEmptyBlob::force),
          OracleWrite.writeBlob.contramap(NonEmptyBlob::value),
          OracleJson.nonEmptyBlob);

  /**
   * LONG RAW - Deprecated binary type (for backward compatibility only). Use BLOB instead for new
   * applications.
   */
  OracleType<byte[]> longRaw =
      OracleType.of(
          "LONG RAW", OracleRead.readByteArray, OracleWrite.writeByteArray, OracleJson.bytea);

  // BFILE - External file pointer (read-only, references files on server filesystem)
  // Omitted: Requires special handling and rarely used in typical applications

  // ═══════════════════════════════════════════════════════════════════════════
  // Date/Time Types
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * DATE - Date with time to second precision. Note: Oracle DATE includes time unlike SQL standard
   * DATE! Range: January 1, 4712 BC to December 31, 9999 AD.
   */
  OracleType<LocalDateTime> date =
      OracleType.of("DATE", OracleRead.readLocalDateTime, OracleWrite.writeDate(), OracleJson.date);

  /** TIMESTAMP - Timestamp with fractional seconds. Default precision is 6 (microseconds). */
  OracleType<LocalDateTime> timestamp =
      OracleType.of(
          "TIMESTAMP",
          OracleRead.readTimestamp,
          OracleWrite.writeTimestamp(),
          OracleJson.timestamp);

  static OracleType<LocalDateTime> timestamp(int fractionalSecondsPrecision) {
    return OracleType.of(
        OracleTypename.of("TIMESTAMP", fractionalSecondsPrecision),
        OracleRead.readTimestamp,
        OracleWrite.writeTimestamp(),
        OracleJson.timestamp);
  }

  /**
   * TIMESTAMP WITH TIME ZONE - Timestamp with explicit timezone. Stores the time zone offset or
   * region name.
   */
  OracleType<OffsetDateTime> timestampWithTimeZone =
      OracleType.of(
          "TIMESTAMP WITH TIME ZONE",
          OracleRead.readOffsetDateTime,
          OracleWrite.writeTimestampWithTimeZone(),
          OracleJson.timestampWithTimeZone);

  static OracleType<OffsetDateTime> timestampWithTimeZone(int fractionalSecondsPrecision) {
    return OracleType.of(
        OracleTypename.of("TIMESTAMP(" + fractionalSecondsPrecision + ") WITH TIME ZONE"),
        OracleRead.readOffsetDateTime,
        OracleWrite.writeTimestampWithTimeZone(),
        OracleJson.timestampWithTimeZone);
  }

  /**
   * TIMESTAMP WITH LOCAL TIME ZONE - Timestamp with timezone information. Oracle normalizes to
   * session timezone, but we preserve OffsetDateTime to avoid data loss.
   */
  OracleType<OffsetDateTime> timestampWithLocalTimeZone =
      OracleType.of(
          "TIMESTAMP WITH LOCAL TIME ZONE",
          OracleRead.readLocalTimezoneTimestamp,
          OracleWrite.writeTimestampWithLocalTimeZone(),
          OracleJson.timestampWithTimeZone);

  static OracleType<OffsetDateTime> timestampWithLocalTimeZone(int fractionalSecondsPrecision) {
    return OracleType.of(
        OracleTypename.of("TIMESTAMP(" + fractionalSecondsPrecision + ") WITH LOCAL TIME ZONE"),
        OracleRead.readLocalTimezoneTimestamp,
        OracleWrite.writeTimestampWithLocalTimeZone(),
        OracleJson.timestampWithTimeZone);
  }

  /**
   * INTERVAL YEAR TO MONTH - Interval in years and months. Represented as OracleIntervalYM which
   * can convert to/from Oracle format (+02-05) and ISO-8601 (P2Y5M).
   */
  OracleType<OracleIntervalYM> intervalYearToMonth =
      OracleType.of(
          "INTERVAL YEAR TO MONTH",
          OracleRead.readIntervalYearToMonth,
          OracleWrite.writeIntervalYearToMonth(),
          OracleJson.intervalYearToMonth);

  static OracleType<OracleIntervalYM> intervalYearToMonth(int yearPrecision) {
    return OracleType.of(
        OracleTypename.of("INTERVAL YEAR(" + yearPrecision + ") TO MONTH"),
        OracleRead.readIntervalYearToMonth,
        OracleWrite.writeIntervalYearToMonth(),
        OracleJson.intervalYearToMonth);
  }

  /**
   * INTERVAL DAY TO SECOND - Interval in days, hours, minutes, seconds. Represented as
   * OracleIntervalDS which can convert to/from Oracle format (+03 14:30:45.123456) and ISO-8601
   * (P3DT14H30M45.123456S).
   */
  OracleType<OracleIntervalDS> intervalDayToSecond =
      OracleType.of(
          "INTERVAL DAY TO SECOND",
          OracleRead.readIntervalDayToSecond,
          OracleWrite.writeIntervalDayToSecond(),
          OracleJson.intervalDayToSecond);

  static OracleType<OracleIntervalDS> intervalDayToSecond(
      int dayPrecision, int fractionalSecondsPrecision) {
    return OracleType.of(
        OracleTypename.of(
            "INTERVAL DAY(" + dayPrecision + ") TO SECOND(" + fractionalSecondsPrecision + ")"),
        OracleRead.readIntervalDayToSecond,
        OracleWrite.writeIntervalDayToSecond(),
        OracleJson.intervalDayToSecond);
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // ROWID Types (Oracle-specific)
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * ROWID - Physical row address for heap-organized tables. 10-byte internal format, displayed as
   * 18-character base-64 string.
   */
  OracleType<String> rowId =
      OracleType.of("ROWID", OracleRead.readRowId, OracleWrite.writeString, OracleJson.rowId);

  /**
   * UROWID - Universal ROWID for index-organized tables and foreign tables. Variable length, max
   * 4000 bytes.
   */
  OracleType<String> uRowId =
      OracleType.of("UROWID", OracleRead.readRowId, OracleWrite.writeString, OracleJson.rowId);

  static OracleType<String> uRowId(int maxLength) {
    return OracleType.of(
        OracleTypename.of("UROWID", maxLength),
        OracleRead.readRowId,
        OracleWrite.writeString,
        OracleJson.rowId);
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // XML/JSON Types
  // ═══════════════════════════════════════════════════════════════════════════

  /** XMLTYPE - XML document storage. Supports XQuery, XPath, and XML Schema validation. */
  OracleType<String> xmlType =
      OracleType.of("XMLTYPE", OracleRead.readClob, OracleWrite.writeClob, OracleJson.xmlType);

  /**
   * JSON - Native JSON type (Oracle 21c+). Binary optimized storage with efficient query support.
   */
  OracleType<Json> json =
      OracleType.of(
          "JSON",
          OracleRead.readString.map(Json::new),
          OracleWrite.writeString.contramap(Json::value),
          OracleJson.json);

  // ═══════════════════════════════════════════════════════════════════════════
  // Boolean Type (Oracle 23c+)
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * BOOLEAN - Native boolean type (Oracle 23c+). Prior to 23c, use NUMBER(1) with 0/1 convention.
   */
  OracleType<Boolean> boolean_ =
      OracleType.of("BOOLEAN", OracleRead.readBoolean, OracleWrite.writeBoolean, OracleJson.bool);

  /**
   * NUMBER(1) as Boolean - Traditional Oracle boolean representation. 0 = false, 1 = true (or any
   * non-zero = true).
   */
  OracleType<Boolean> numberAsBoolean =
      OracleType.of(
          OracleTypename.of("NUMBER", 1),
          OracleRead.readInteger.map(i -> i != 0),
          OracleWrite.writeInteger.contramap(b -> b ? 1 : 0),
          OracleJson.bool);

  // ═══════════════════════════════════════════════════════════════════════════
  // ENUM Type Helper
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Create an OracleType for ENUM-like columns (stored as VARCHAR2 or NUMBER). Oracle doesn't have
   * native ENUM type, so enums are typically stored as strings.
   *
   * @param sqlType The SQL type (e.g., "VARCHAR2(20)")
   * @param fromString Function to convert string to enum value
   * @param <E> The enum type
   */
  static <E extends Enum<E>> OracleType<E> ofEnum(String sqlType, Function<String, E> fromString) {
    return OracleType.of(
        sqlType,
        OracleRead.readString.map(fromString::apply),
        OracleWrite.writeString.contramap(Enum::name),
        OracleJson.text.bimap(fromString::apply, Enum::name));
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Spatial Types (Oracle Spatial)
  // ═══════════════════════════════════════════════════════════════════════════

  // SDO_GEOMETRY - Requires Oracle Spatial license and complex object mapping
  // SDO_POINT_TYPE - Point type
  // These are left as comments because they require:
  // 1. Oracle Spatial extension
  // 2. Special JDBC handling with oracle.spatial.geometry.JGeometry
  // 3. Complex struct/object type handling

  // ═══════════════════════════════════════════════════════════════════════════
  // Object/Collection Types (Oracle Object-Relational)
  // ═══════════════════════════════════════════════════════════════════════════

  // OBJECT TYPE (CREATE TYPE ... AS OBJECT) - User-defined object types
  // VARRAY - Fixed-size ordered arrays
  // NESTED TABLE - Unbounded collection types
  // REF types - Object references
  //
  // These require special handling with:
  // - oracle.sql.STRUCT for object types
  // - oracle.sql.ARRAY for collections
  // - oracle.sql.REF for references
  //
  // Code generation will create specific types for each user-defined type

  // ═══════════════════════════════════════════════════════════════════════════
  // Any Types (Dynamic typing - rarely used directly)
  // ═══════════════════════════════════════════════════════════════════════════

  // ANYDATA - Container for any SQL type
  // ANYTYPE - Type descriptor
  // ANYDATASET - Collection of ANYDATA
  //
  // These are used for generic/polymorphic PL/SQL procedures
  // Not commonly mapped to Java types directly
}
