package typr.runtime;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Year;
import java.util.function.Function;
import org.mariadb.jdbc.type.Geometry;
import org.mariadb.jdbc.type.GeometryCollection;
import org.mariadb.jdbc.type.LineString;
import org.mariadb.jdbc.type.MultiLineString;
import org.mariadb.jdbc.type.MultiPoint;
import org.mariadb.jdbc.type.MultiPolygon;
import org.mariadb.jdbc.type.Point;
import org.mariadb.jdbc.type.Polygon;
import typr.data.Json;
import typr.data.maria.Inet4;
import typr.data.maria.Inet6;
import typr.data.maria.MariaSet;

/**
 * MariaDB type definitions for the typr-runtime-java library.
 *
 * <p>This interface provides type codecs for all MariaDB data types, similar to PgTypes for
 * PostgreSQL.
 */
public interface MariaTypes {
  // ==================== Integer Types (Signed) ====================

  MariaType<Byte> tinyint =
      MariaType.of(
          "TINYINT",
          MariaRead.readByte,
          MariaWrite.writeByte,
          MariaText.textByte,
          MariaJson.int4.bimap(Integer::byteValue, Byte::intValue));

  MariaType<Short> smallint =
      MariaType.of(
          "SMALLINT",
          MariaRead.readShort,
          MariaWrite.writeShort,
          MariaText.textShort,
          MariaJson.int2);

  MariaType<Integer> mediumint =
      MariaType.of(
          "MEDIUMINT",
          MariaRead.readInteger,
          MariaWrite.writeInteger,
          MariaText.textInteger,
          MariaJson.int4);

  MariaType<Integer> int_ =
      MariaType.of(
          "INT",
          MariaRead.readInteger,
          MariaWrite.writeInteger,
          MariaText.textInteger,
          MariaJson.int4);

  MariaType<Long> bigint =
      MariaType.of(
          "BIGINT", MariaRead.readLong, MariaWrite.writeLong, MariaText.textLong, MariaJson.int8);

  // ==================== Integer Types (Unsigned) ====================

  // TINYINT UNSIGNED: 0-255, fits in Short
  MariaType<Short> tinyintUnsigned =
      MariaType.of(
          "TINYINT UNSIGNED",
          MariaRead.readShort,
          MariaWrite.writeShort,
          MariaText.textShort,
          MariaJson.int2);

  // SMALLINT UNSIGNED: 0-65535, fits in Integer
  MariaType<Integer> smallintUnsigned =
      MariaType.of(
          "SMALLINT UNSIGNED",
          MariaRead.readInteger,
          MariaWrite.writeInteger,
          MariaText.textInteger,
          MariaJson.int4);

  // MEDIUMINT UNSIGNED: 0-16777215, fits in Integer
  MariaType<Integer> mediumintUnsigned =
      MariaType.of(
          "MEDIUMINT UNSIGNED",
          MariaRead.readInteger,
          MariaWrite.writeInteger,
          MariaText.textInteger,
          MariaJson.int4);

  // INT UNSIGNED: 0-4294967295, fits in Long
  MariaType<Long> intUnsigned =
      MariaType.of(
          "INT UNSIGNED",
          MariaRead.readLong,
          MariaWrite.writeLong,
          MariaText.textLong,
          MariaJson.int8);

  // BIGINT UNSIGNED: 0-18446744073709551615, needs BigInteger
  MariaType<BigInteger> bigintUnsigned =
      MariaType.of(
          "BIGINT UNSIGNED",
          MariaRead.readBigInteger,
          MariaWrite.writeBigInteger,
          MariaText.textBigInteger,
          MariaJson.numeric.bimap(BigDecimal::toBigInteger, BigDecimal::new));

  // ==================== Fixed-Point Types ====================

  MariaType<BigDecimal> decimal =
      MariaType.of(
          "DECIMAL",
          MariaRead.readBigDecimal,
          MariaWrite.writeBigDecimal,
          MariaText.textBigDecimal,
          MariaJson.numeric);

  MariaType<BigDecimal> numeric = decimal.renamed("NUMERIC");

  static MariaType<BigDecimal> decimal(int precision, int scale) {
    return MariaType.of(
        MariaTypename.of("DECIMAL", precision, scale),
        MariaRead.readBigDecimal,
        MariaWrite.writeBigDecimal,
        MariaText.textBigDecimal,
        MariaJson.numeric);
  }

  // ==================== Floating-Point Types ====================

  MariaType<Float> float_ =
      MariaType.of(
          "FLOAT",
          MariaRead.readFloat,
          MariaWrite.writeFloat,
          MariaText.textFloat,
          MariaJson.float4);

  MariaType<Double> double_ =
      MariaType.of(
          "DOUBLE",
          MariaRead.readDouble,
          MariaWrite.writeDouble,
          MariaText.textDouble,
          MariaJson.float8);

  // ==================== Boolean Type ====================

  MariaType<Boolean> bool =
      MariaType.of(
          "BOOLEAN",
          MariaRead.readBoolean,
          MariaWrite.writeBoolean,
          MariaText.textBoolean,
          MariaJson.bool);

  // ==================== Bit Types ====================

  // BIT(1) as Boolean
  MariaType<Boolean> bit1 =
      MariaType.of(
          "BIT",
          MariaRead.readBitAsBoolean,
          MariaWrite.writeBoolean,
          MariaText.textBoolean,
          MariaJson.bool);

  // BIT(n) as byte[]
  MariaType<byte[]> bit =
      MariaType.of(
          "BIT",
          MariaRead.readBit,
          MariaWrite.writeByteArray,
          MariaText.textByteArray,
          MariaJson.bytea);

  // ==================== String Types ====================

  MariaType<String> char_ =
      MariaType.of(
          "CHAR",
          MariaRead.readString,
          MariaWrite.writeString,
          MariaText.textString,
          MariaJson.text);

  MariaType<String> varchar =
      MariaType.of(
          "VARCHAR",
          MariaRead.readString,
          MariaWrite.writeString,
          MariaText.textString,
          MariaJson.text);

  MariaType<String> tinytext =
      MariaType.of(
          "TINYTEXT",
          MariaRead.readString,
          MariaWrite.writeString,
          MariaText.textString,
          MariaJson.text);

  MariaType<String> text =
      MariaType.of(
          "TEXT",
          MariaRead.readString,
          MariaWrite.writeString,
          MariaText.textString,
          MariaJson.text);

  MariaType<String> mediumtext =
      MariaType.of(
          "MEDIUMTEXT",
          MariaRead.readString,
          MariaWrite.writeString,
          MariaText.textString,
          MariaJson.text);

  MariaType<String> longtext =
      MariaType.of(
          "LONGTEXT",
          MariaRead.readString,
          MariaWrite.writeString,
          MariaText.textString,
          MariaJson.text);

  static MariaType<String> char_(int length) {
    return MariaType.of(
        MariaTypename.of("CHAR", length),
        MariaRead.readString,
        MariaWrite.writeString,
        MariaText.textString,
        MariaJson.text);
  }

  static MariaType<String> varchar(int length) {
    return MariaType.of(
        MariaTypename.of("VARCHAR", length),
        MariaRead.readString,
        MariaWrite.writeString,
        MariaText.textString,
        MariaJson.text);
  }

  // ==================== Binary Types ====================

  MariaType<byte[]> binary =
      MariaType.of(
          "BINARY",
          MariaRead.readByteArray,
          MariaWrite.writeByteArray,
          MariaText.textByteArray,
          MariaJson.bytea);

  MariaType<byte[]> varbinary =
      MariaType.of(
          "VARBINARY",
          MariaRead.readByteArray,
          MariaWrite.writeByteArray,
          MariaText.textByteArray,
          MariaJson.bytea);

  MariaType<byte[]> tinyblob =
      MariaType.of(
          "TINYBLOB",
          MariaRead.readBlob,
          MariaWrite.writeByteArray,
          MariaText.textByteArray,
          MariaJson.bytea);

  MariaType<byte[]> blob =
      MariaType.of(
          "BLOB",
          MariaRead.readBlob,
          MariaWrite.writeByteArray,
          MariaText.textByteArray,
          MariaJson.bytea);

  MariaType<byte[]> mediumblob =
      MariaType.of(
          "MEDIUMBLOB",
          MariaRead.readBlob,
          MariaWrite.writeByteArray,
          MariaText.textByteArray,
          MariaJson.bytea);

  MariaType<byte[]> longblob =
      MariaType.of(
          "LONGBLOB",
          MariaRead.readBlob,
          MariaWrite.writeByteArray,
          MariaText.textByteArray,
          MariaJson.bytea);

  static MariaType<byte[]> binary(int length) {
    return MariaType.of(
        MariaTypename.of("BINARY", length),
        MariaRead.readByteArray,
        MariaWrite.writeByteArray,
        MariaText.textByteArray,
        MariaJson.bytea);
  }

  static MariaType<byte[]> varbinary(int length) {
    return MariaType.of(
        MariaTypename.of("VARBINARY", length),
        MariaRead.readByteArray,
        MariaWrite.writeByteArray,
        MariaText.textByteArray,
        MariaJson.bytea);
  }

  // ==================== Date/Time Types ====================

  MariaType<LocalDate> date =
      MariaType.of(
          "DATE",
          MariaRead.readLocalDate,
          MariaWrite.passObjectToJdbc(),
          MariaText.instanceToString(),
          MariaJson.date);

  MariaType<LocalTime> time =
      MariaType.of(
          "TIME",
          MariaRead.readLocalTime,
          MariaWrite.passObjectToJdbc(),
          MariaText.instanceToString(),
          MariaJson.time);

  MariaType<LocalDateTime> datetime =
      MariaType.of(
          "DATETIME",
          MariaRead.readLocalDateTime,
          MariaWrite.passObjectToJdbc(),
          MariaText.instanceToString(),
          MariaJson.timestamp);

  MariaType<LocalDateTime> timestamp =
      MariaType.of(
          "TIMESTAMP",
          MariaRead.readLocalDateTime,
          MariaWrite.passObjectToJdbc(),
          MariaText.instanceToString(),
          MariaJson.timestamp);

  MariaType<Year> year =
      MariaType.of(
          "YEAR",
          MariaRead.readYear,
          MariaWrite.writeShort.contramap(y -> (short) y.getValue()),
          MariaText.textInteger.contramap(Year::getValue),
          MariaJson.int4.bimap(Year::of, Year::getValue));

  static MariaType<LocalTime> time(int fsp) {
    return MariaType.of(
        MariaTypename.of("TIME", fsp),
        MariaRead.readLocalTime,
        MariaWrite.passObjectToJdbc(),
        MariaText.instanceToString(),
        MariaJson.time);
  }

  static MariaType<LocalDateTime> datetime(int fsp) {
    return MariaType.of(
        MariaTypename.of("DATETIME", fsp),
        MariaRead.readLocalDateTime,
        MariaWrite.passObjectToJdbc(),
        MariaText.instanceToString(),
        MariaJson.timestamp);
  }

  static MariaType<LocalDateTime> timestamp(int fsp) {
    return MariaType.of(
        MariaTypename.of("TIMESTAMP", fsp),
        MariaRead.readLocalDateTime,
        MariaWrite.passObjectToJdbc(),
        MariaText.instanceToString(),
        MariaJson.timestamp);
  }

  // ==================== ENUM Type ====================

  /**
   * Create a MariaType for ENUM columns. MariaDB ENUMs are read/written as strings.
   *
   * @param fromString function to convert string to enum value
   * @param <E> the enum type
   * @return MariaType for the enum
   */
  static <E extends Enum<E>> MariaType<E> ofEnum(String sqlType, Function<String, E> fromString) {
    return MariaType.of(
        sqlType,
        MariaRead.readString.map(fromString::apply),
        MariaWrite.writeString.contramap(Enum::name),
        MariaText.textString.contramap(Enum::name),
        MariaJson.text.bimap(fromString::apply, Enum::name));
  }

  // ==================== SET Type ====================

  /** MariaSet wrapper for SET columns. */
  MariaType<MariaSet> set =
      MariaType.of(
          "SET",
          MariaRead.readString.map(MariaSet::fromString),
          MariaWrite.writeString.contramap(MariaSet::toCommaSeparated),
          MariaText.textString.contramap(MariaSet::toCommaSeparated),
          MariaJson.text.bimap(MariaSet::fromString, MariaSet::toCommaSeparated));

  // ==================== JSON Type ====================

  /** JSON type - reuses typr.data.Json from the common types. */
  MariaType<Json> json =
      MariaType.of(
          "JSON",
          MariaRead.readString.map(Json::new),
          MariaWrite.writeString.contramap(Json::value),
          MariaText.textString.contramap(Json::value),
          MariaJson.json);

  // ==================== Network Types ====================

  MariaType<Inet4> inet4 =
      MariaType.of(
          "INET4",
          MariaRead.readString.map(Inet4::parse),
          MariaWrite.writeString.contramap(Inet4::value),
          MariaText.textString.contramap(Inet4::value),
          MariaJson.text.bimap(Inet4::parse, Inet4::value));

  MariaType<Inet6> inet6 =
      MariaType.of(
          "INET6",
          MariaRead.readString.map(Inet6::parse),
          MariaWrite.writeString.contramap(Inet6::value),
          MariaText.textString.contramap(Inet6::value),
          MariaJson.text.bimap(Inet6::parse, Inet6::value));

  // ==================== Spatial Types ====================
  // Using MariaDB Connector/J types directly.
  // We use getObjectAs() instead of castJdbcObjectTo() because it allows the JDBC driver
  // to convert WKB bytes (returned from RETURNING clauses) back to typed geometry objects.
  // Note: Spatial types use text representation for JSON (WKT format would be better but is
  // complex)

  MariaType<Geometry> geometry =
      MariaType.of(
          "GEOMETRY",
          MariaRead.getObjectAs(Geometry.class),
          MariaWrite.passObjectToJdbc(),
          MariaText.NotWorking(),
          MariaJson.text.bimap(
              s -> {
                throw new UnsupportedOperationException("Geometry JSON not supported");
              },
              Object::toString));

  MariaType<Point> point =
      MariaType.of(
          "POINT",
          MariaRead.getObjectAs(Point.class),
          MariaWrite.passObjectToJdbc(),
          MariaText.NotWorking(),
          MariaJson.text.bimap(
              s -> {
                throw new UnsupportedOperationException("Point JSON not supported");
              },
              Object::toString));

  MariaType<LineString> linestring =
      MariaType.of(
          "LINESTRING",
          MariaRead.getObjectAs(LineString.class),
          MariaWrite.passObjectToJdbc(),
          MariaText.NotWorking(),
          MariaJson.text.bimap(
              s -> {
                throw new UnsupportedOperationException("LineString JSON not supported");
              },
              Object::toString));

  MariaType<Polygon> polygon =
      MariaType.of(
          "POLYGON",
          MariaRead.getObjectAs(Polygon.class),
          MariaWrite.passObjectToJdbc(),
          MariaText.NotWorking(),
          MariaJson.text.bimap(
              s -> {
                throw new UnsupportedOperationException("Polygon JSON not supported");
              },
              Object::toString));

  MariaType<MultiPoint> multipoint =
      MariaType.of(
          "MULTIPOINT",
          MariaRead.getObjectAs(MultiPoint.class),
          MariaWrite.passObjectToJdbc(),
          MariaText.NotWorking(),
          MariaJson.text.bimap(
              s -> {
                throw new UnsupportedOperationException("MultiPoint JSON not supported");
              },
              Object::toString));

  MariaType<MultiLineString> multilinestring =
      MariaType.of(
          "MULTILINESTRING",
          MariaRead.getObjectAs(MultiLineString.class),
          MariaWrite.passObjectToJdbc(),
          MariaText.NotWorking(),
          MariaJson.text.bimap(
              s -> {
                throw new UnsupportedOperationException("MultiLineString JSON not supported");
              },
              Object::toString));

  MariaType<MultiPolygon> multipolygon =
      MariaType.of(
          "MULTIPOLYGON",
          MariaRead.getObjectAs(MultiPolygon.class),
          MariaWrite.passObjectToJdbc(),
          MariaText.NotWorking(),
          MariaJson.text.bimap(
              s -> {
                throw new UnsupportedOperationException("MultiPolygon JSON not supported");
              },
              Object::toString));

  MariaType<GeometryCollection> geometrycollection =
      MariaType.of(
          "GEOMETRYCOLLECTION",
          MariaRead.getObjectAs(GeometryCollection.class),
          MariaWrite.passObjectToJdbc(),
          MariaText.NotWorking(),
          MariaJson.text.bimap(
              s -> {
                throw new UnsupportedOperationException("GeometryCollection JSON not supported");
              },
              Object::toString));
}
