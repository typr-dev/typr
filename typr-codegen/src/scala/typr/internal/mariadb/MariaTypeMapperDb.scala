package typr
package internal
package mariadb

/** MariaDB type mapper from database metadata to db.MariaType */
case class MariaTypeMapperDb() extends TypeMapperDb {

  /** Map JDBC type name to db.MariaType (used for SQL files)
    *
    * JDBC's ResultSetMetaData.getColumnTypeName() returns type names like "BIGINT UNSIGNED", "VARCHAR", "DECIMAL", "DATETIME", etc.
    */
  override def dbTypeFrom(jdbcTypeName: String, characterMaximumLength: Option[Int])(logWarning: () => Unit): db.Type = {
    // Parse the JDBC type name which may include modifiers like "UNSIGNED"
    val normalized = jdbcTypeName.toUpperCase.trim
    val isUnsigned = normalized.contains("UNSIGNED")
    val baseType = normalized.replace("UNSIGNED", "").trim.split("\\s+")(0)

    baseType match {
      // Integer types - including INT1/INT2/INT3/INT4/INT8 aliases and U-prefixed unsigned aliases
      case "TINYINT" | "INT1" =>
        if (isUnsigned) db.MariaType.TinyIntUnsigned
        else db.MariaType.TinyInt
      case "UTINYINT" | "UINT1" =>
        db.MariaType.TinyIntUnsigned

      case "SMALLINT" | "INT2" =>
        if (isUnsigned) db.MariaType.SmallIntUnsigned
        else db.MariaType.SmallInt
      case "USMALLINT" | "UINT2" =>
        db.MariaType.SmallIntUnsigned

      case "MEDIUMINT" | "INT3" | "MIDDLEINT" =>
        if (isUnsigned) db.MariaType.MediumIntUnsigned
        else db.MariaType.MediumInt
      case "UMEDIUMINT" | "UINT3" =>
        db.MariaType.MediumIntUnsigned

      case "INT" | "INTEGER" | "INT4" =>
        if (isUnsigned) db.MariaType.IntUnsigned
        else db.MariaType.Int
      case "UINT" | "UINTEGER" | "UINT4" =>
        db.MariaType.IntUnsigned

      case "BIGINT" | "INT8" =>
        if (isUnsigned) db.MariaType.BigIntUnsigned
        else db.MariaType.BigInt
      case "UBIGINT" | "UINT8" | "SERIAL" =>
        db.MariaType.BigIntUnsigned

      // Fixed-point (DEC and FIXED are MySQL/MariaDB aliases for DECIMAL)
      case "DECIMAL" | "NUMERIC" | "DEC" | "FIXED" =>
        db.MariaType.Decimal(None, None)

      // Floating-point (REAL is an alias for DOUBLE in MariaDB by default)
      case "FLOAT"           => db.MariaType.Float
      case "DOUBLE" | "REAL" => db.MariaType.Double

      // Boolean
      case "BOOLEAN" | "BOOL" => db.MariaType.Boolean

      // Bit
      case "BIT" => db.MariaType.Bit(characterMaximumLength)

      // String types
      case "CHAR"       => db.MariaType.Char(characterMaximumLength)
      case "VARCHAR"    => db.MariaType.VarChar(characterMaximumLength)
      case "TINYTEXT"   => db.MariaType.TinyText
      case "TEXT"       => db.MariaType.Text
      case "MEDIUMTEXT" => db.MariaType.MediumText
      case "LONGTEXT"   => db.MariaType.LongText

      // Binary types
      case "BINARY"     => db.MariaType.Binary(characterMaximumLength)
      case "VARBINARY"  => db.MariaType.VarBinary(characterMaximumLength)
      case "TINYBLOB"   => db.MariaType.TinyBlob
      case "BLOB"       => db.MariaType.Blob
      case "MEDIUMBLOB" => db.MariaType.MediumBlob
      case "LONGBLOB"   => db.MariaType.LongBlob

      // Date/Time types
      case "DATE"      => db.MariaType.Date
      case "TIME"      => db.MariaType.Time(None)
      case "DATETIME"  => db.MariaType.DateTime(None)
      case "TIMESTAMP" => db.MariaType.Timestamp(None)
      case "YEAR"      => db.MariaType.Year

      // ENUM and SET - we can't parse values from JDBC, use text fallback
      case "ENUM" => db.MariaType.Enum(Nil)
      case "SET"  => db.MariaType.Set(Nil)

      // Network types
      case "INET4" => db.MariaType.Inet4
      case "INET6" => db.MariaType.Inet6

      // Spatial types
      case "GEOMETRY"           => db.MariaType.Geometry
      case "POINT"              => db.MariaType.Point
      case "LINESTRING"         => db.MariaType.LineString
      case "POLYGON"            => db.MariaType.Polygon
      case "MULTIPOINT"         => db.MariaType.MultiPoint
      case "MULTILINESTRING"    => db.MariaType.MultiLineString
      case "MULTIPOLYGON"       => db.MariaType.MultiPolygon
      case "GEOMETRYCOLLECTION" => db.MariaType.GeometryCollection

      // JSON
      case "JSON" => db.MariaType.Json

      case _ =>
        // For SQL file parameters where type couldn't be inferred, return Unknown
        // This allows explicit type overrides to still work
        db.Unknown(jdbcTypeName)
    }
  }

  /** Map MariaDB column type from INFORMATION_SCHEMA.COLUMNS to db.MariaType
    *
    * @param dataType
    *   DATA_TYPE column (e.g., "int", "varchar", "enum")
    * @param columnType
    *   COLUMN_TYPE column (e.g., "int(11) unsigned", "enum('a','b')", "varchar(255)")
    * @param characterMaximumLength
    *   CHARACTER_MAXIMUM_LENGTH column
    * @param numericPrecision
    *   NUMERIC_PRECISION column
    * @param numericScale
    *   NUMERIC_SCALE column
    * @param datetimePrecision
    *   DATETIME_PRECISION column (fractional seconds precision)
    */
  def dbTypeFrom(
      dataType: String,
      columnType: String,
      characterMaximumLength: Option[Long],
      numericPrecision: Option[Long],
      numericScale: Option[Long],
      datetimePrecision: Option[Long]
  ): db.MariaType = {
    val isUnsigned = columnType.toLowerCase.contains("unsigned")

    dataType.toLowerCase match {
      // Integer types
      case "tinyint" =>
        // BOOLEAN is TINYINT(1) in MariaDB
        if (columnType.toLowerCase.startsWith("tinyint(1)") && !isUnsigned) db.MariaType.Boolean
        else if (isUnsigned) db.MariaType.TinyIntUnsigned
        else db.MariaType.TinyInt

      case "smallint" =>
        if (isUnsigned) db.MariaType.SmallIntUnsigned
        else db.MariaType.SmallInt

      case "mediumint" =>
        if (isUnsigned) db.MariaType.MediumIntUnsigned
        else db.MariaType.MediumInt

      case "int" | "integer" =>
        if (isUnsigned) db.MariaType.IntUnsigned
        else db.MariaType.Int

      case "bigint" =>
        if (isUnsigned) db.MariaType.BigIntUnsigned
        else db.MariaType.BigInt

      // Fixed-point
      case "decimal" | "numeric" =>
        db.MariaType.Decimal(numericPrecision.map(_.toInt), numericScale.map(_.toInt))

      // Floating-point
      case "float"  => db.MariaType.Float
      case "double" => db.MariaType.Double

      // Bit
      case "bit" =>
        db.MariaType.Bit(characterMaximumLength.map(_.toInt))

      // String types
      case "char" =>
        db.MariaType.Char(characterMaximumLength.map(_.toInt))

      case "varchar" =>
        db.MariaType.VarChar(characterMaximumLength.map(_.toInt))

      case "tinytext"   => db.MariaType.TinyText
      case "text"       => db.MariaType.Text
      case "mediumtext" => db.MariaType.MediumText
      case "longtext"   => db.MariaType.LongText

      // Binary types
      case "binary" =>
        db.MariaType.Binary(characterMaximumLength.map(_.toInt))

      case "varbinary" =>
        db.MariaType.VarBinary(characterMaximumLength.map(_.toInt))

      case "tinyblob"   => db.MariaType.TinyBlob
      case "blob"       => db.MariaType.Blob
      case "mediumblob" => db.MariaType.MediumBlob
      case "longblob"   => db.MariaType.LongBlob

      // Date/Time types
      case "date" => db.MariaType.Date
      case "time" =>
        db.MariaType.Time(datetimePrecision.map(_.toInt))

      case "datetime" =>
        db.MariaType.DateTime(datetimePrecision.map(_.toInt))

      case "timestamp" =>
        db.MariaType.Timestamp(datetimePrecision.map(_.toInt))

      case "year" => db.MariaType.Year

      // ENUM and SET - parse values from COLUMN_TYPE
      case "enum" =>
        val values = parseEnumOrSetValues(columnType)
        db.MariaType.Enum(values)

      case "set" =>
        val values = parseEnumOrSetValues(columnType)
        db.MariaType.Set(values)

      // Network types (MariaDB 10.10+)
      case "inet4" => db.MariaType.Inet4
      case "inet6" => db.MariaType.Inet6

      // Spatial types
      case "geometry"           => db.MariaType.Geometry
      case "point"              => db.MariaType.Point
      case "linestring"         => db.MariaType.LineString
      case "polygon"            => db.MariaType.Polygon
      case "multipoint"         => db.MariaType.MultiPoint
      case "multilinestring"    => db.MariaType.MultiLineString
      case "multipolygon"       => db.MariaType.MultiPolygon
      case "geometrycollection" => db.MariaType.GeometryCollection

      // JSON (alias for LONGTEXT in MariaDB, but treated specially)
      case "json" => db.MariaType.Json

      case _ =>
        sys.error(s"Unknown MariaDB data type: $dataType (column type: $columnType)")
    }
  }

  /** Parse ENUM or SET values from COLUMN_TYPE string like "enum('a','b','c')" or "set('x','y')" */
  private def parseEnumOrSetValues(columnType: String): List[String] = {
    val pattern = """(?:enum|set)\s*\((.+)\)""".r
    columnType.toLowerCase match {
      case pattern(valuesPart) =>
        // Split by comma, handling escaped quotes within values
        val values = valuesPart.split("','").toList.map { v =>
          v.stripPrefix("'").stripSuffix("'").replace("''", "'")
        }
        values
      case _ => Nil
    }
  }
}
