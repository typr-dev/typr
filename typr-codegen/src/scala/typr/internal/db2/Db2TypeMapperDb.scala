package typr
package internal
package db2

/** DB2 type mapper from database metadata to db.DB2Type
  *
  * @param distinctTypes
  *   Map of (schema, typeName) -> Db2DistinctType for resolving user-defined types
  */
case class Db2TypeMapperDb(
    distinctTypes: Map[(String, String), Db2MetaDb.Db2DistinctType] = Map.empty
) extends TypeMapperDb {

  /** Map JDBC type name to db.DB2Type (used for SQL files)
    *
    * JDBC's ResultSetMetaData.getColumnTypeName() returns type names like "BIGINT", "VARCHAR", "DECIMAL", etc.
    */
  override def dbTypeFrom(jdbcTypeName: String, characterMaximumLength: Option[Int])(logWarning: () => Unit): db.Type = {
    val normalized = jdbcTypeName.toUpperCase.trim

    normalized match {
      // Integer types
      case "SMALLINT"        => db.DB2Type.SmallInt
      case "INTEGER" | "INT" => db.DB2Type.Integer
      case "BIGINT"          => db.DB2Type.BigInt

      // Fixed-point
      case "DECIMAL" | "NUMERIC" | "DEC" => db.DB2Type.Decimal(None, None)
      case "DECFLOAT"                    => db.DB2Type.DecFloat(None)

      // Floating-point
      case "REAL"             => db.DB2Type.Real
      case "DOUBLE" | "FLOAT" => db.DB2Type.Double

      // Boolean (DB2 11.1+)
      case "BOOLEAN" => db.DB2Type.Boolean

      // Character types (SBCS)
      case "CHAR" | "CHARACTER"              => db.DB2Type.Char(characterMaximumLength)
      case "VARCHAR" | "CHARACTER VARYING"   => db.DB2Type.VarChar(characterMaximumLength)
      case "CLOB" | "CHARACTER LARGE OBJECT" => db.DB2Type.Clob
      case "LONG VARCHAR"                    => db.DB2Type.Long

      // Character types (DBCS/Graphic)
      case "GRAPHIC"         => db.DB2Type.Graphic(characterMaximumLength)
      case "VARGRAPHIC"      => db.DB2Type.VarGraphic(characterMaximumLength)
      case "DBCLOB"          => db.DB2Type.DbClob
      case "LONG VARGRAPHIC" => db.DB2Type.LongVarGraphic

      // Binary types
      case "BINARY"                             => db.DB2Type.Binary(characterMaximumLength)
      case "VARBINARY" | "VARCHAR FOR BIT DATA" => db.DB2Type.VarBinary(characterMaximumLength)
      case "CHAR FOR BIT DATA"                  => db.DB2Type.Binary(characterMaximumLength)
      case "BLOB" | "BINARY LARGE OBJECT"       => db.DB2Type.Blob

      // Date/Time types
      case "DATE"      => db.DB2Type.Date
      case "TIME"      => db.DB2Type.Time
      case "TIMESTAMP" => db.DB2Type.Timestamp(None)

      // XML type
      case "XML" => db.DB2Type.Xml

      // Row ID
      case "ROWID" => db.DB2Type.RowId

      case _ =>
        db.Unknown(jdbcTypeName)
    }
  }

  /** Map DB2 column type from SYSCAT.COLUMNS to db.DB2Type
    *
    * @param typeName
    *   TYPENAME column from SYSCAT.COLUMNS
    * @param length
    *   LENGTH column (character/binary length)
    * @param scale
    *   SCALE column (decimal scale)
    * @param typeSchemaName
    *   TYPESCHEMA column (for distinct types)
    * @param typeModuleName
    *   TYPEMODULENAME column (for distinct types)
    */
  def dbTypeFrom(
      typeName: String,
      length: Option[Int],
      scale: Option[Int],
      typeSchemaName: Option[String],
      @scala.annotation.unused typeModuleName: Option[String]
  ): db.DB2Type = {
    typeName.toUpperCase match {
      // Integer types
      case "SMALLINT" => db.DB2Type.SmallInt
      case "INTEGER"  => db.DB2Type.Integer
      case "BIGINT"   => db.DB2Type.BigInt

      // Fixed-point
      case "DECIMAL" | "NUMERIC" =>
        db.DB2Type.Decimal(length, scale)

      case "DECFLOAT" =>
        db.DB2Type.DecFloat(length)

      // Floating-point
      case "REAL"   => db.DB2Type.Real
      case "DOUBLE" => db.DB2Type.Double

      // Boolean
      case "BOOLEAN" => db.DB2Type.Boolean

      // Character types (SBCS)
      case "CHARACTER" | "CHAR" =>
        db.DB2Type.Char(length)

      case "VARCHAR" =>
        db.DB2Type.VarChar(length)

      case "CLOB" =>
        db.DB2Type.Clob

      case "LONG VARCHAR" =>
        db.DB2Type.Long

      // Character types (DBCS/Graphic)
      case "GRAPHIC" =>
        db.DB2Type.Graphic(length)

      case "VARGRAPHIC" =>
        db.DB2Type.VarGraphic(length)

      case "DBCLOB" =>
        db.DB2Type.DbClob

      case "LONG VARGRAPHIC" =>
        db.DB2Type.LongVarGraphic

      // Binary types
      case "BINARY" =>
        db.DB2Type.Binary(length)

      case "VARBINARY" =>
        db.DB2Type.VarBinary(length)

      case "BLOB" =>
        db.DB2Type.Blob

      // Date/Time types
      case "DATE" => db.DB2Type.Date
      case "TIME" => db.DB2Type.Time
      case "TIMESTAMP" =>
        db.DB2Type.Timestamp(scale) // Scale is fractional seconds precision

      // XML type
      case "XML" => db.DB2Type.Xml

      // Row ID
      case "ROWID" => db.DB2Type.RowId

      case _ =>
        // Check if it's a distinct (user-defined) type
        // In SingleSchema mode, typeSchemaName may be None (schema stripped), so we look up with empty string
        val schemaKey = typeSchemaName.getOrElse("")
        if (typeSchemaName.exists(_.startsWith("SYS"))) {
          sys.error(s"Unknown DB2 data type: $typeName")
        } else {
          // Look up the distinct type to get its source type
          distinctTypes.get((schemaKey, typeName)) match {
            case Some(dt) =>
              // Recursively resolve the source type
              val sourceType = dbTypeFrom(
                typeName = dt.sourceTypeName,
                length = dt.length,
                scale = dt.scale,
                typeSchemaName = Option(dt.sourceTypeSchema).filter(_.nonEmpty),
                typeModuleName = None
              )
              db.DB2Type.DistinctType(
                db.RelationName(Option(schemaKey).filter(_.nonEmpty), typeName),
                sourceType
              )
            case None =>
              // Distinct type not found - fall back to VARCHAR
              db.DB2Type.DistinctType(
                db.RelationName(Option(schemaKey).filter(_.nonEmpty), typeName),
                db.DB2Type.VarChar(None)
              )
          }
        }
    }
  }
}
