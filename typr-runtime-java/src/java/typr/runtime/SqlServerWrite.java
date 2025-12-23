package typr.runtime;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Describes how to write a value to a {@link PreparedStatement} for SQL Server.
 *
 * <p>Similar to MariaWrite but adapted for SQL Server-specific types like DATETIMEOFFSET,
 * UNIQUEIDENTIFIER, etc.
 */
public sealed interface SqlServerWrite<A> extends DbWrite<A> permits SqlServerWrite.Instance {
  void set(PreparedStatement ps, int idx, A a) throws SQLException;

  SqlServerWrite<Optional<A>> opt(SqlServerTypename<A> typename);

  <B> SqlServerWrite<B> contramap(Function<B, A> f);

  @FunctionalInterface
  interface RawWriter<A> {
    void set(PreparedStatement ps, int index, A a) throws SQLException;
  }

  record Instance<A, U>(RawWriter<U> rawWriter, Function<A, U> f) implements SqlServerWrite<A> {
    @Override
    public void set(PreparedStatement ps, int index, A a) throws SQLException {
      rawWriter.set(ps, index, f.apply(a));
    }

    @Override
    public SqlServerWrite<Optional<A>> opt(SqlServerTypename<A> typename) {
      // SQL Server requires the actual SQL type for setNull(), not java.sql.Types.NULL
      // Use the type name to determine the correct JDBC type constant
      int sqlType = getSqlTypeForTypename(typename.sqlTypeNoPrecision());
      return new Instance<>(
          (ps, index, u) -> {
            if (u == null) ps.setNull(index, sqlType);
            else set(ps, index, u);
          },
          a -> a.orElse(null));
    }

    @Override
    public <B> SqlServerWrite<B> contramap(Function<B, A> f) {
      return new Instance<>(rawWriter, f.andThen(this.f));
    }
  }

  static <A> SqlServerWrite<A> primitive(RawWriter<A> rawWriter) {
    return new Instance<>(rawWriter, Function.identity());
  }

  static <A> SqlServerWrite<A> passObjectToJdbc() {
    return primitive(PreparedStatement::setObject);
  }

  static int getSqlTypeForTypename(String sqlType) {
    return switch (sqlType.toUpperCase()) {
      case "TINYINT" -> java.sql.Types.TINYINT;
      case "SMALLINT" -> java.sql.Types.SMALLINT;
      case "INT", "INTEGER" -> java.sql.Types.INTEGER;
      case "BIGINT" -> java.sql.Types.BIGINT;
      case "DECIMAL", "NUMERIC", "MONEY", "SMALLMONEY" -> java.sql.Types.DECIMAL;
      case "REAL" -> java.sql.Types.REAL;
      case "FLOAT" -> java.sql.Types.FLOAT;
      case "BIT" -> java.sql.Types.BIT;
      case "CHAR" -> java.sql.Types.CHAR;
      case "VARCHAR" -> java.sql.Types.VARCHAR;
      case "NCHAR" -> java.sql.Types.NCHAR;
      case "NVARCHAR" -> java.sql.Types.NVARCHAR;
      case "TEXT" -> java.sql.Types.LONGVARCHAR;
      case "NTEXT" -> java.sql.Types.LONGNVARCHAR;
      case "BINARY" -> java.sql.Types.BINARY;
      case "VARBINARY" -> java.sql.Types.VARBINARY;
      case "IMAGE" -> java.sql.Types.LONGVARBINARY;
      case "DATE" -> java.sql.Types.DATE;
      case "TIME" -> java.sql.Types.TIME;
      case "DATETIME", "DATETIME2", "SMALLDATETIME" -> java.sql.Types.TIMESTAMP;
      case "DATETIMEOFFSET" -> java.sql.Types.TIMESTAMP_WITH_TIMEZONE;
      case "UNIQUEIDENTIFIER" -> java.sql.Types.CHAR;
      case "XML" -> java.sql.Types.SQLXML;
      case "GEOGRAPHY", "GEOMETRY", "HIERARCHYID", "SQL_VARIANT" -> java.sql.Types.OTHER;
      default -> java.sql.Types.OTHER;
    };
  }

  // ==================== Basic Type Writers ====================

  SqlServerWrite<String> writeString = primitive(PreparedStatement::setString);

  // TEXT and NTEXT need explicit SQL type to avoid NVARCHAR conversion
  SqlServerWrite<String> writeText =
      primitive((ps, idx, s) -> ps.setObject(idx, s, java.sql.Types.LONGVARCHAR));
  SqlServerWrite<String> writeNText =
      primitive((ps, idx, s) -> ps.setObject(idx, s, java.sql.Types.LONGNVARCHAR));
  SqlServerWrite<Boolean> writeBoolean = primitive(PreparedStatement::setBoolean);
  SqlServerWrite<Short> writeShort = primitive(PreparedStatement::setShort);
  SqlServerWrite<Integer> writeInteger = primitive(PreparedStatement::setInt);
  SqlServerWrite<Long> writeLong = primitive(PreparedStatement::setLong);
  SqlServerWrite<Float> writeFloat = primitive(PreparedStatement::setFloat);
  SqlServerWrite<Double> writeDouble = primitive(PreparedStatement::setDouble);
  SqlServerWrite<BigDecimal> writeBigDecimal = primitive(PreparedStatement::setBigDecimal);
  SqlServerWrite<byte[]> writeByteArray = primitive(PreparedStatement::setBytes);

  // ==================== Date/Time Writers ====================

  SqlServerWrite<LocalDate> writeDate = primitive(PreparedStatement::setObject);
  SqlServerWrite<LocalTime> writeTime = primitive(PreparedStatement::setObject);
  SqlServerWrite<LocalDateTime> writeTimestamp = primitive(PreparedStatement::setObject);

  // DATETIMEOFFSET - SQL Server specific!
  SqlServerWrite<OffsetDateTime> writeOffsetDateTime =
      primitive((ps, idx, odt) -> ps.setObject(idx, odt));

  // ==================== Special Type Writers ====================

  // UNIQUEIDENTIFIER (UUID/GUID)
  SqlServerWrite<UUID> writeUUID = primitive((ps, idx, uuid) -> ps.setString(idx, uuid.toString()));

  // XML
  SqlServerWrite<String> writeXml =
      primitive(
          (ps, idx, xml) -> {
            java.sql.SQLXML sqlxml = ps.getConnection().createSQLXML();
            sqlxml.setString(xml);
            ps.setSQLXML(idx, sqlxml);
          });

  // JSON - SQL Server stores JSON as NVARCHAR
  SqlServerWrite<String> writeJson = writeString;

  // HIERARCHYID - write as string
  SqlServerWrite<String> writeHierarchyId = writeString;

  // SQL_VARIANT - write as object
  SqlServerWrite<Object> writeObject = primitive(PreparedStatement::setObject);

  // GEOGRAPHY and GEOMETRY - use JDBC driver's spatial classes
  SqlServerWrite<com.microsoft.sqlserver.jdbc.Geography> writeGeography =
      primitive((ps, idx, geo) -> ps.setObject(idx, geo));
  SqlServerWrite<com.microsoft.sqlserver.jdbc.Geometry> writeGeometry =
      primitive((ps, idx, geom) -> ps.setObject(idx, geom));

  // VECTOR
  SqlServerWrite<byte[]> writeVector = writeByteArray;
}
