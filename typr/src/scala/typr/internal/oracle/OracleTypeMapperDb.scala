package typr
package internal
package oracle

/** Oracle type mapper from database metadata to db.OracleType
  *
  * @param objectTypes
  *   User-defined object types (CREATE TYPE AS OBJECT)
  * @param allTypes
  *   All user-defined types including object types, VARRAYs, and nested tables
  */
case class OracleTypeMapperDb(
    objectTypes: Map[String, db.OracleType.ObjectType],
    allTypes: Map[String, db.OracleType]
) extends TypeMapperDb {

  /** Map JDBC type name to db.OracleType (used for SQL files)
    *
    * JDBC's ResultSetMetaData.getColumnTypeName() returns type names like "NUMBER", "VARCHAR2", "DATE", "TIMESTAMP", etc.
    */
  override def dbTypeFrom(jdbcTypeName: String, characterMaximumLength: Option[Int])(logWarning: () => Unit): db.Type = {
    val normalized = jdbcTypeName.toUpperCase.trim

    // Handle parameterized types like "TIMESTAMP(6)", "NUMBER(10,2)", "VARCHAR2(100)"
    val (baseType, params) = parseTypeParams(normalized)

    baseType match {
      // Numeric types
      case "NUMBER" =>
        params match {
          case Some((p, s)) => db.OracleType.Number(Some(p), s)
          case None         => db.OracleType.Number(None, None)
        }

      case "BINARY_FLOAT"  => db.OracleType.BinaryFloat
      case "BINARY_DOUBLE" => db.OracleType.BinaryDouble

      case "FLOAT" =>
        db.OracleType.Float(params.map(_._1))

      case "INTEGER" | "INT" | "SMALLINT" =>
        db.OracleType.Number(Some(38), Some(0))

      case "REAL" =>
        db.OracleType.Float(Some(63))

      case "DOUBLE PRECISION" =>
        db.OracleType.Float(Some(126))

      // Character types
      case "VARCHAR2" | "VARCHAR" =>
        db.OracleType.Varchar2(params.map(_._1).orElse(characterMaximumLength))

      case "NVARCHAR2" =>
        db.OracleType.NVarchar2(params.map(_._1).orElse(characterMaximumLength))

      case "CHAR" =>
        db.OracleType.Char(params.map(_._1).orElse(characterMaximumLength))

      case "NCHAR" =>
        db.OracleType.NChar(params.map(_._1).orElse(characterMaximumLength))

      case "CLOB"  => db.OracleType.Clob
      case "NCLOB" => db.OracleType.NClob
      case "LONG"  => db.OracleType.Long

      // Binary types
      case "RAW" =>
        db.OracleType.Raw(params.map(_._1).orElse(characterMaximumLength))

      case "BLOB"     => db.OracleType.Blob
      case "LONG RAW" => db.OracleType.LongRaw

      // Date/Time types
      case "DATE" => db.OracleType.Date

      case "TIMESTAMP" =>
        db.OracleType.Timestamp(params.map(_._1))

      case "TIMESTAMP WITH TIME ZONE" | "TIMESTAMPTZ" =>
        db.OracleType.TimestampWithTimeZone(params.map(_._1))

      case "TIMESTAMP WITH LOCAL TIME ZONE" | "TIMESTAMPLTZ" =>
        db.OracleType.TimestampWithLocalTimeZone(params.map(_._1))

      case "INTERVAL YEAR TO MONTH" =>
        db.OracleType.IntervalYearToMonth(params.map(_._1))

      case "INTERVAL DAY TO SECOND" =>
        params match {
          case Some((dayPrec, Some(secPrec))) => db.OracleType.IntervalDayToSecond(Some(dayPrec), Some(secPrec))
          case Some((dayPrec, None))          => db.OracleType.IntervalDayToSecond(Some(dayPrec), None)
          case None                           => db.OracleType.IntervalDayToSecond(None, None)
        }

      // ROWID types
      case "ROWID"  => db.OracleType.RowId
      case "UROWID" => db.OracleType.URowId(params.map(_._1))

      // XML/JSON types
      case "XMLTYPE"     => db.OracleType.XmlType
      case "JSON"        => db.OracleType.Json
      case "SYS.XMLTYPE" => db.OracleType.XmlType

      // Boolean (Oracle 23c+)
      case "BOOLEAN" => db.OracleType.Boolean

      case _ =>
        // Check if it's a user-defined type
        allTypes.get(baseType).getOrElse {
          // For SQL file parameters where type couldn't be inferred, return Unknown
          db.Unknown(jdbcTypeName)
        }
    }
  }

  /** Map Oracle column type from ALL_TAB_COLUMNS to db.OracleType
    *
    * @param dataType
    *   DATA_TYPE column (e.g., "NUMBER", "VARCHAR2", "DATE")
    * @param dataLength
    *   DATA_LENGTH column (byte length)
    * @param dataScale
    *   DATA_SCALE column (scale for NUMBER)
    * @param dataPrecision
    *   DATA_PRECISION column (precision for NUMBER)
    * @param charLength
    *   CHAR_LENGTH column (character length for char types)
    */
  def dbTypeFrom(
      dataType: String,
      dataLength: Option[Int],
      dataScale: Option[Int],
      dataPrecision: Option[Int],
      charLength: Option[Int]
  ): db.OracleType = {
    val normalized = dataType.toUpperCase.trim

    // Handle types that may have precision in the name like "TIMESTAMP(6)"
    val (baseType, inlineParams) = parseTypeParams(normalized)

    baseType match {
      // Numeric types
      case "NUMBER" =>
        db.OracleType.Number(dataPrecision, dataScale)

      case "BINARY_FLOAT"  => db.OracleType.BinaryFloat
      case "BINARY_DOUBLE" => db.OracleType.BinaryDouble

      case "FLOAT" =>
        db.OracleType.Float(dataPrecision)

      // Character types
      case "VARCHAR2" | "VARCHAR" =>
        db.OracleType.Varchar2(charLength.orElse(dataLength))

      case "NVARCHAR2" =>
        db.OracleType.NVarchar2(charLength.orElse(dataLength))

      case "CHAR" =>
        db.OracleType.Char(charLength.orElse(dataLength))

      case "NCHAR" =>
        db.OracleType.NChar(charLength.orElse(dataLength))

      case "CLOB"  => db.OracleType.Clob
      case "NCLOB" => db.OracleType.NClob
      case "LONG"  => db.OracleType.Long

      // Binary types
      case "RAW" =>
        db.OracleType.Raw(dataLength)

      case "BLOB"     => db.OracleType.Blob
      case "LONG RAW" => db.OracleType.LongRaw

      // Date/Time types
      case "DATE" => db.OracleType.Date

      case "TIMESTAMP" =>
        db.OracleType.Timestamp(inlineParams.map(_._1).orElse(dataScale))

      case "TIMESTAMP WITH TIME ZONE" | "TIMESTAMPTZ" | "TIMESTAMP WITH TZ" =>
        db.OracleType.TimestampWithTimeZone(inlineParams.map(_._1).orElse(dataScale))

      case "TIMESTAMP WITH LOCAL TIME ZONE" | "TIMESTAMPLTZ" | "TIMESTAMP WITH LOCAL TZ" =>
        db.OracleType.TimestampWithLocalTimeZone(inlineParams.map(_._1).orElse(dataScale))

      case "INTERVAL YEAR TO MONTH" =>
        db.OracleType.IntervalYearToMonth(dataPrecision)

      case "INTERVAL DAY TO SECOND" =>
        db.OracleType.IntervalDayToSecond(dataPrecision, dataScale)

      // ROWID types
      case "ROWID"  => db.OracleType.RowId
      case "UROWID" => db.OracleType.URowId(dataLength)

      // XML/JSON types
      case "XMLTYPE"     => db.OracleType.XmlType
      case "JSON"        => db.OracleType.Json
      case "SYS.XMLTYPE" => db.OracleType.XmlType

      // Boolean (Oracle 23c+)
      case "BOOLEAN" => db.OracleType.Boolean

      case _ =>
        // Check if it's a user-defined type (object type, VARRAY, nested table)
        allTypes.get(baseType).getOrElse {
          sys.error(s"Unknown Oracle data type: $dataType")
        }
    }
  }

  /** Parse type parameters from type name like "NUMBER(10,2)" or "VARCHAR2(100)" or "TIMESTAMP(6)" */
  private def parseTypeParams(typeName: String): (String, Option[(Int, Option[Int])]) = {
    val pattern = """^([A-Z0-9_ ]+)\s*\(\s*(\d+)(?:\s*,\s*(\d+))?\s*\)$""".r
    typeName match {
      case pattern(base, p1, p2) =>
        val precision = p1.toInt
        val scale = Option(p2).map(_.toInt)
        (base.trim, Some((precision, scale)))
      case _ =>
        (typeName, None)
    }
  }
}
