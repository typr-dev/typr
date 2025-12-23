package typr
package internal
package duckdb

import scala.util.Try

/** DuckDB type mapper from database metadata to db.DuckDbType */
case class DuckDbTypeMapperDb() extends TypeMapperDb {

  /** Helper for Scala 2.12 compatibility */
  private def toIntOption(s: String): Option[Int] = Try(s.toInt).toOption

  /** Map JDBC type name to db.DuckDbType (used for SQL files and JDBC metadata)
    *
    * JDBC's ResultSetMetaData.getColumnTypeName() returns type names like "INTEGER", "VARCHAR", "DECIMAL(10,2)", etc.
    */
  override def dbTypeFrom(jdbcTypeName: String, characterMaximumLength: Option[Int])(logWarning: () => Unit): db.Type = {
    val normalized = jdbcTypeName.toUpperCase.trim
    parseType(normalized, characterMaximumLength)(logWarning)
  }

  /** Parse a DuckDB type string into db.DuckDbType */
  def parseType(typeStr: String, characterMaximumLength: Option[Int])(logWarning: () => Unit): db.Type = {
    val normalized = typeStr.toUpperCase.trim

    // Handle parameterized types first
    if (normalized.startsWith("DECIMAL(") || normalized.startsWith("NUMERIC(")) {
      val inner = normalized.dropWhile(_ != '(').drop(1).takeWhile(_ != ')')
      val parts = inner.split(",").map(_.trim)
      val precision = parts.headOption.flatMap(toIntOption)
      val scale = parts.lift(1).flatMap(toIntOption)
      return db.DuckDbType.Decimal(precision, scale)
    }

    if (normalized.startsWith("VARCHAR(")) {
      val inner = normalized.dropWhile(_ != '(').drop(1).takeWhile(_ != ')')
      val length = toIntOption(inner.trim)
      return db.DuckDbType.VarChar(length)
    }

    if (normalized.startsWith("CHAR(")) {
      val inner = normalized.dropWhile(_ != '(').drop(1).takeWhile(_ != ')')
      val length = toIntOption(inner.trim)
      return db.DuckDbType.Char(length)
    }

    if (normalized.startsWith("BIT(")) {
      val inner = normalized.dropWhile(_ != '(').drop(1).takeWhile(_ != ')')
      val length = toIntOption(inner.trim)
      return db.DuckDbType.Bit(length)
    }

    // Handle array suffix (e.g., "INTEGER[]" or "INTEGER[3]")
    if (normalized.contains("[") && normalized.endsWith("]")) {
      val bracketIdx = normalized.indexOf('[')
      val elementTypeName = normalized.substring(0, bracketIdx)
      val sizeStr = normalized.substring(bracketIdx + 1, normalized.length - 1)
      val elementType = parseType(elementTypeName, None)(logWarning)
      if (sizeStr.isEmpty) {
        // Variable-length LIST: INTEGER[]
        return db.DuckDbType.ListType(elementType)
      } else {
        // Fixed-size ARRAY: INTEGER[3]
        val size = toIntOption(sizeStr)
        return db.DuckDbType.ArrayType(elementType, size)
      }
    }

    // Handle LIST type (e.g., "LIST(INTEGER)" or "INTEGER LIST")
    if (normalized.startsWith("LIST(") || normalized.startsWith("LIST<")) {
      val inner = normalized.drop(5).dropRight(1) // Remove "LIST(" and ")"
      val elementType = parseType(inner, None)(logWarning)
      return db.DuckDbType.ListType(elementType)
    }

    // Handle MAP type
    if (normalized.startsWith("MAP(") || normalized.startsWith("MAP<")) {
      val inner = normalized.drop(4).dropRight(1) // Remove "MAP(" and ")"
      // Split by comma, but handle nested types
      val (keyPart, valuePart) = splitMapTypes(inner)
      val keyType = parseType(keyPart, None)(logWarning)
      val valueType = parseType(valuePart, None)(logWarning)
      return db.DuckDbType.MapType(keyType, valueType)
    }

    // Handle STRUCT type
    if (normalized.startsWith("STRUCT(") || normalized.startsWith("STRUCT<")) {
      // For now, return as Unknown - STRUCT is complex to parse
      return db.Unknown(typeStr)
    }

    // Handle UNION type
    if (normalized.startsWith("UNION(") || normalized.startsWith("UNION<")) {
      // For now, return as Unknown - UNION is complex to parse
      return db.Unknown(typeStr)
    }

    // Handle ENUM type - DuckDB reports enums as ENUM('value1', 'value2', ...)
    // The name will be resolved later using the enum definitions from duckdb_types()
    // Important: Use original typeStr (not normalized) to preserve enum value case
    if (normalized.startsWith("ENUM(")) {
      val inner = typeStr.trim.drop(5).dropRight(1) // Remove "ENUM(" and ")" from original
      val values = inner.split(",").map(_.trim.stripPrefix("'").stripSuffix("'")).toList
      // Use a placeholder name; will be resolved in DuckDbMetaDb.fromInput
      return db.DuckDbType.Enum(name = "", values = values)
    }

    // Simple types
    normalized match {
      // Integer types
      case "TINYINT" | "INT1"                    => db.DuckDbType.TinyInt
      case "SMALLINT" | "INT2" | "SHORT"         => db.DuckDbType.SmallInt
      case "INTEGER" | "INT4" | "INT" | "SIGNED" => db.DuckDbType.Integer
      case "BIGINT" | "INT8" | "LONG"            => db.DuckDbType.BigInt
      case "HUGEINT" | "INT128"                  => db.DuckDbType.HugeInt

      // Unsigned integer types
      case "UTINYINT" | "UINT1"          => db.DuckDbType.UTinyInt
      case "USMALLINT" | "UINT2"         => db.DuckDbType.USmallInt
      case "UINTEGER" | "UINT4" | "UINT" => db.DuckDbType.UInteger
      case "UBIGINT" | "UINT8"           => db.DuckDbType.UBigInt
      case "UHUGEINT" | "UINT128"        => db.DuckDbType.UHugeInt

      // Floating-point types
      case "FLOAT" | "FLOAT4" | "REAL" => db.DuckDbType.Float
      case "DOUBLE" | "FLOAT8"         => db.DuckDbType.Double

      // Fixed-point types
      case "DECIMAL" | "NUMERIC" => db.DuckDbType.Decimal(None, None)

      // Boolean
      case "BOOLEAN" | "BOOL" => db.DuckDbType.Boolean

      // String types
      case "VARCHAR" | "STRING" | "BPCHAR" => db.DuckDbType.VarChar(characterMaximumLength)
      case "CHAR"                          => db.DuckDbType.Char(characterMaximumLength)
      case "TEXT"                          => db.DuckDbType.Text

      // Binary types
      case "BLOB" | "BYTEA" | "BINARY" | "VARBINARY" => db.DuckDbType.Blob

      // Bit string
      case "BIT"       => db.DuckDbType.Bit(characterMaximumLength)
      case "BITSTRING" => db.DuckDbType.Bit(None)

      // Date/Time types
      case "DATE"                                     => db.DuckDbType.Date
      case "TIME"                                     => db.DuckDbType.Time
      case "TIMESTAMP" | "DATETIME" | "TIMESTAMPNTZ"  => db.DuckDbType.Timestamp
      case "TIMESTAMPTZ" | "TIMESTAMP WITH TIME ZONE" => db.DuckDbType.TimestampTz
      case "TIMESTAMP_S"                              => db.DuckDbType.TimestampS
      case "TIMESTAMP_MS"                             => db.DuckDbType.TimestampMS
      case "TIMESTAMP_NS"                             => db.DuckDbType.TimestampNS
      case "TIMETZ" | "TIME WITH TIME ZONE"           => db.DuckDbType.TimeTz
      case "INTERVAL"                                 => db.DuckDbType.Interval

      // UUID
      case "UUID" => db.DuckDbType.UUID

      // JSON
      case "JSON" => db.DuckDbType.Json

      case _ =>
        // Check if it's an enum type or unknown
        // DuckDB enum types would need schema introspection
        logWarning()
        db.Unknown(typeStr)
    }
  }

  /** Split MAP type parameters handling nested generics */
  private def splitMapTypes(inner: String): (String, String) = {
    var depth = 0
    var splitIdx = -1
    var i = 0
    while (i < inner.length && splitIdx < 0) {
      inner.charAt(i) match {
        case '(' | '<' | '['   => depth += 1
        case ')' | '>' | ']'   => depth -= 1
        case ',' if depth == 0 => splitIdx = i
        case _                 =>
      }
      i += 1
    }
    if (splitIdx > 0) {
      (inner.substring(0, splitIdx).trim, inner.substring(splitIdx + 1).trim)
    } else {
      (inner, "")
    }
  }
}
