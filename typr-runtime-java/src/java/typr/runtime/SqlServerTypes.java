package typr.runtime;

import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

/**
 * SQL Server type definitions for the typr-runtime-java library.
 *
 * <p>This interface provides type codecs for all SQL Server data types.
 *
 * <p>Key differences from other databases: - TINYINT is UNSIGNED (0-255), mapped to Short - Unicode
 * types (NCHAR, NVARCHAR, NTEXT) are separate from non-Unicode - DATETIMEOFFSET for timezone-aware
 * timestamps - UNIQUEIDENTIFIER for UUIDs/GUIDs - No native array support (use table-valued
 * parameters instead)
 */
public interface SqlServerTypes {

  // ==================== Integer Types ====================

  // TINYINT - UNSIGNED in SQL Server! (0-255)
  SqlServerType<Short> tinyint =
      SqlServerType.of(
          "TINYINT",
          SqlServerRead.readShort,
          SqlServerWrite.writeShort,
          SqlServerText.textShort,
          SqlServerJson.int2);

  SqlServerType<Short> smallint =
      SqlServerType.of(
          "SMALLINT",
          SqlServerRead.readShort,
          SqlServerWrite.writeShort,
          SqlServerText.textShort,
          SqlServerJson.int2);

  SqlServerType<Integer> int_ =
      SqlServerType.of(
          "INT",
          SqlServerRead.readInteger,
          SqlServerWrite.writeInteger,
          SqlServerText.textInteger,
          SqlServerJson.int4);

  SqlServerType<Long> bigint =
      SqlServerType.of(
          "BIGINT",
          SqlServerRead.readLong,
          SqlServerWrite.writeLong,
          SqlServerText.textLong,
          SqlServerJson.int8);

  // ==================== Fixed-Point Types ====================

  SqlServerType<BigDecimal> decimal =
      SqlServerType.of(
          "DECIMAL",
          SqlServerRead.readBigDecimal,
          SqlServerWrite.writeBigDecimal,
          SqlServerText.textBigDecimal,
          SqlServerJson.numeric);

  SqlServerType<BigDecimal> numeric = decimal.renamed("NUMERIC");

  static SqlServerType<BigDecimal> decimal(int precision, int scale) {
    return SqlServerType.of(
        SqlServerTypename.of("DECIMAL", precision, scale),
        SqlServerRead.readBigDecimal,
        SqlServerWrite.writeBigDecimal,
        SqlServerText.textBigDecimal,
        SqlServerJson.numeric);
  }

  static SqlServerType<BigDecimal> numeric(int precision, int scale) {
    return decimal(precision, scale).renamed("NUMERIC");
  }

  SqlServerType<BigDecimal> money =
      SqlServerType.of(
          "MONEY",
          SqlServerRead.readBigDecimal,
          SqlServerWrite.writeBigDecimal,
          SqlServerText.textBigDecimal,
          SqlServerJson.numeric);

  SqlServerType<BigDecimal> smallmoney =
      SqlServerType.of(
          "SMALLMONEY",
          SqlServerRead.readBigDecimal,
          SqlServerWrite.writeBigDecimal,
          SqlServerText.textBigDecimal,
          SqlServerJson.numeric);

  // ==================== Floating-Point Types ====================

  SqlServerType<Float> real =
      SqlServerType.of(
          "REAL",
          SqlServerRead.readFloat,
          SqlServerWrite.writeFloat,
          SqlServerText.textFloat,
          SqlServerJson.float4);

  SqlServerType<Double> float_ =
      SqlServerType.of(
          "FLOAT",
          SqlServerRead.readDouble,
          SqlServerWrite.writeDouble,
          SqlServerText.textDouble,
          SqlServerJson.float8);

  // ==================== Boolean Type ====================

  SqlServerType<Boolean> bit =
      SqlServerType.of(
          "BIT",
          SqlServerRead.readBoolean,
          SqlServerWrite.writeBoolean,
          SqlServerText.textBoolean,
          SqlServerJson.bool);

  // ==================== String Types (Non-Unicode) ====================

  SqlServerType<String> char_ =
      SqlServerType.of(
          "CHAR",
          SqlServerRead.readString,
          SqlServerWrite.writeString,
          SqlServerText.textString,
          SqlServerJson.text);

  static SqlServerType<String> char_(int length) {
    return SqlServerType.of(
        SqlServerTypename.of("CHAR", length),
        SqlServerRead.readString,
        SqlServerWrite.writeString,
        SqlServerText.textString,
        SqlServerJson.text);
  }

  SqlServerType<String> varchar =
      SqlServerType.of(
          "VARCHAR",
          SqlServerRead.readString,
          SqlServerWrite.writeString,
          SqlServerText.textString,
          SqlServerJson.text);

  static SqlServerType<String> varchar(int length) {
    return SqlServerType.of(
        SqlServerTypename.of("VARCHAR", length),
        SqlServerRead.readString,
        SqlServerWrite.writeString,
        SqlServerText.textString,
        SqlServerJson.text);
  }

  SqlServerType<String> varcharMax = varchar.renamed("VARCHAR(MAX)");
  SqlServerType<String> text =
      SqlServerType.of(
          "TEXT",
          SqlServerRead.readString,
          SqlServerWrite.writeText,
          SqlServerText.textString,
          SqlServerJson.text);

  // ==================== String Types (Unicode) ====================

  SqlServerType<String> nchar =
      SqlServerType.of(
          "NCHAR",
          SqlServerRead.readString,
          SqlServerWrite.writeString,
          SqlServerText.textString,
          SqlServerJson.text);

  static SqlServerType<String> nchar(int length) {
    return SqlServerType.of(
        SqlServerTypename.of("NCHAR", length),
        SqlServerRead.readString,
        SqlServerWrite.writeString,
        SqlServerText.textString,
        SqlServerJson.text);
  }

  SqlServerType<String> nvarchar =
      SqlServerType.of(
          "NVARCHAR",
          SqlServerRead.readString,
          SqlServerWrite.writeString,
          SqlServerText.textString,
          SqlServerJson.text);

  static SqlServerType<String> nvarchar(int length) {
    return SqlServerType.of(
        SqlServerTypename.of("NVARCHAR", length),
        SqlServerRead.readString,
        SqlServerWrite.writeString,
        SqlServerText.textString,
        SqlServerJson.text);
  }

  SqlServerType<String> nvarcharMax = nvarchar.renamed("NVARCHAR(MAX)");
  SqlServerType<String> ntext =
      SqlServerType.of(
          "NTEXT",
          SqlServerRead.readString,
          SqlServerWrite.writeNText,
          SqlServerText.textString,
          SqlServerJson.text);

  // ==================== Binary Types ====================

  SqlServerType<byte[]> binary =
      SqlServerType.of(
          "BINARY",
          SqlServerRead.readByteArray,
          SqlServerWrite.writeByteArray,
          SqlServerText.textByteArray,
          SqlServerJson.bytea);

  static SqlServerType<byte[]> binary(int length) {
    return SqlServerType.of(
        SqlServerTypename.of("BINARY", length),
        SqlServerRead.readByteArray,
        SqlServerWrite.writeByteArray,
        SqlServerText.textByteArray,
        SqlServerJson.bytea);
  }

  SqlServerType<byte[]> varbinary =
      SqlServerType.of(
          "VARBINARY",
          SqlServerRead.readByteArray,
          SqlServerWrite.writeByteArray,
          SqlServerText.textByteArray,
          SqlServerJson.bytea);

  static SqlServerType<byte[]> varbinary(int length) {
    return SqlServerType.of(
        SqlServerTypename.of("VARBINARY", length),
        SqlServerRead.readByteArray,
        SqlServerWrite.writeByteArray,
        SqlServerText.textByteArray,
        SqlServerJson.bytea);
  }

  SqlServerType<byte[]> varbinaryMax = varbinary.renamed("VARBINARY(MAX)");
  SqlServerType<byte[]> image = varbinary.renamed("IMAGE");

  // ==================== Date/Time Types ====================

  SqlServerType<LocalDate> date =
      SqlServerType.of(
          "DATE",
          SqlServerRead.readDate,
          SqlServerWrite.writeDate,
          SqlServerText.instanceToString(),
          SqlServerJson.date);

  SqlServerType<LocalTime> time =
      SqlServerType.of(
          "TIME",
          SqlServerRead.readTime,
          SqlServerWrite.writeTime,
          SqlServerText.instanceToString(),
          SqlServerJson.time);

  static SqlServerType<LocalTime> time(int scale) {
    return SqlServerType.of(
        SqlServerTypename.of("TIME", scale),
        SqlServerRead.readTime,
        SqlServerWrite.writeTime,
        SqlServerText.instanceToString(),
        SqlServerJson.time);
  }

  // DATETIME - legacy type with 3.33ms precision
  SqlServerType<LocalDateTime> datetime =
      SqlServerType.of(
          "DATETIME",
          SqlServerRead.readTimestamp,
          SqlServerWrite.writeTimestamp,
          SqlServerText.instanceToString(),
          SqlServerJson.timestamp);

  // SMALLDATETIME - minute precision
  SqlServerType<LocalDateTime> smalldatetime =
      SqlServerType.of(
          "SMALLDATETIME",
          SqlServerRead.readTimestamp,
          SqlServerWrite.writeTimestamp,
          SqlServerText.instanceToString(),
          SqlServerJson.timestamp);

  // DATETIME2 - modern type with 100ns precision
  SqlServerType<LocalDateTime> datetime2 =
      SqlServerType.of(
          "DATETIME2",
          SqlServerRead.readTimestamp,
          SqlServerWrite.writeTimestamp,
          SqlServerText.instanceToString(),
          SqlServerJson.timestamp);

  static SqlServerType<LocalDateTime> datetime2(int scale) {
    return SqlServerType.of(
        SqlServerTypename.of("DATETIME2", scale),
        SqlServerRead.readTimestamp,
        SqlServerWrite.writeTimestamp,
        SqlServerText.instanceToString(),
        SqlServerJson.timestamp);
  }

  // DATETIMEOFFSET - datetime with timezone offset (SQL Server specific!)
  SqlServerType<OffsetDateTime> datetimeoffset =
      SqlServerType.of(
          "DATETIMEOFFSET",
          SqlServerRead.readOffsetDateTime,
          SqlServerWrite.writeOffsetDateTime,
          SqlServerText.instanceToString(),
          SqlServerJson.timestamptz);

  static SqlServerType<OffsetDateTime> datetimeoffset(int scale) {
    return SqlServerType.of(
        SqlServerTypename.of("DATETIMEOFFSET", scale),
        SqlServerRead.readOffsetDateTime,
        SqlServerWrite.writeOffsetDateTime,
        SqlServerText.instanceToString(),
        SqlServerJson.timestamptz);
  }

  // ==================== Special Types ====================

  // UNIQUEIDENTIFIER (UUID/GUID)
  SqlServerType<UUID> uniqueidentifier =
      SqlServerType.of(
          "UNIQUEIDENTIFIER",
          SqlServerRead.readUUID,
          SqlServerWrite.writeUUID,
          SqlServerText.textUUID,
          SqlServerJson.uuid);

  // XML
  SqlServerType<String> xml =
      SqlServerType.of(
          "XML",
          SqlServerRead.readXml,
          SqlServerWrite.writeXml,
          SqlServerText.textString,
          SqlServerJson.text);

  // JSON - SQL Server 2016+, stored as NVARCHAR
  SqlServerType<String> json =
      SqlServerType.of(
          "NVARCHAR(MAX)", // JSON is stored as NVARCHAR(MAX)
          SqlServerRead.readJson,
          SqlServerWrite.writeJson,
          SqlServerText.textString,
          SqlServerJson.text);

  // VECTOR - SQL Server 2025 (stored as binary for now)
  SqlServerType<byte[]> vector =
      SqlServerType.of(
          "VECTOR",
          SqlServerRead.readVector,
          SqlServerWrite.writeVector,
          SqlServerText.textByteArray,
          SqlServerJson.bytea);

  // ==================== System Types ====================

  // ROWVERSION / TIMESTAMP - 8-byte binary version number
  SqlServerType<byte[]> rowversion =
      SqlServerType.of(
          "ROWVERSION",
          SqlServerRead.readByteArray,
          SqlServerWrite.writeByteArray,
          SqlServerText.textByteArray,
          SqlServerJson.bytea);

  SqlServerType<byte[]> timestamp = rowversion.renamed("TIMESTAMP");

  // HIERARCHYID - hierarchical data (tree structures)
  SqlServerType<String> hierarchyid =
      SqlServerType.of(
          "HIERARCHYID",
          SqlServerRead.readHierarchyId,
          SqlServerWrite.writeHierarchyId,
          SqlServerText.textString,
          SqlServerJson.text);

  // SQL_VARIANT - can store values of various types
  SqlServerType<Object> sqlVariant =
      SqlServerType.of(
          "SQL_VARIANT",
          SqlServerRead.readObject,
          SqlServerWrite.writeObject,
          SqlServerText.textObject,
          SqlServerJson.unknown);

  // ==================== Spatial Types ====================
  // Use JDBC driver's Geography and Geometry classes

  SqlServerType<com.microsoft.sqlserver.jdbc.Geography> geography =
      SqlServerType.of(
          "GEOGRAPHY",
          SqlServerRead.readGeography,
          SqlServerWrite.writeGeography,
          SqlServerText.textGeography,
          SqlServerJson.jsonGeography);

  SqlServerType<com.microsoft.sqlserver.jdbc.Geometry> geometry =
      SqlServerType.of(
          "GEOMETRY",
          SqlServerRead.readGeometry,
          SqlServerWrite.writeGeometry,
          SqlServerText.textGeometry,
          SqlServerJson.jsonGeometry);
}
