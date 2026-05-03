package typr
package internal
package sqlserver

/** SQL Server type mapper from database metadata to db.SqlServerType */
case class SqlServerTypeMapperDb(domains: List[db.Domain]) extends TypeMapperDb {
  val domainsByName: Map[String, db.Domain] = domains.flatMap(d => List((d.name.name, d), (d.name.value, d))).toMap

  /** Map column with potential alias type to db.SqlServerType
    *
    * If the column uses an alias type (user-defined type), create an AliasTypeRef. Otherwise, map the base data type.
    */
  def col(
      udtSchema: Option[String],
      udtName: Option[String],
      dataType: String,
      characterMaximumLength: Option[Long],
      numericPrecision: Option[Long],
      numericScale: Option[Long],
      datetimePrecision: Option[Long]
  ): db.SqlServerType = {
    val fromAliasType: Option[db.SqlServerType.AliasTypeRef] =
      udtName.flatMap { udt =>
        val udtRelationName = db.RelationName(udtSchema, udt)
        domainsByName.get(udtRelationName.value).map { d =>
          db.SqlServerType.AliasTypeRef(
            name = d.name,
            underlying = d.originalType,
            underlyingType = d.tpe,
            hasConstraint = d.constraintDefinition.isDefined
          )
        }
      }

    fromAliasType.getOrElse(dbTypeFrom(dataType, characterMaximumLength, numericPrecision, numericScale, datetimePrecision))
  }

  /** Map JDBC type name to db.SqlServerType (used for SQL files)
    *
    * JDBC's ResultSetMetaData.getColumnTypeName() returns type names like "BIGINT", "NVARCHAR", "DECIMAL", "DATETIME2", etc.
    */
  override def dbTypeFrom(jdbcTypeName: String, characterMaximumLength: Option[Int])(logWarning: () => Unit): db.Type = {
    val normalized = jdbcTypeName.toUpperCase.trim

    normalized match {
      // Integer types
      case "TINYINT"  => db.SqlServerType.TinyInt // 0-255 UNSIGNED
      case "SMALLINT" => db.SqlServerType.SmallInt
      case "INT"      => db.SqlServerType.Int
      case "BIGINT"   => db.SqlServerType.BigInt

      // Fixed-point types
      case "DECIMAL" | "NUMERIC" => db.SqlServerType.Decimal(None, None)
      case "MONEY"               => db.SqlServerType.Money
      case "SMALLMONEY"          => db.SqlServerType.SmallMoney

      // Floating-point types
      case "REAL"  => db.SqlServerType.Real
      case "FLOAT" => db.SqlServerType.Float

      // Boolean (BIT)
      case "BIT" => db.SqlServerType.Bit

      // String types (non-unicode)
      case "CHAR"    => db.SqlServerType.Char(characterMaximumLength)
      case "VARCHAR" => db.SqlServerType.VarChar(characterMaximumLength)
      case "TEXT"    => db.SqlServerType.Text

      // String types (unicode)
      case "NCHAR"    => db.SqlServerType.NChar(characterMaximumLength)
      case "NVARCHAR" => db.SqlServerType.NVarChar(characterMaximumLength)
      case "NTEXT"    => db.SqlServerType.NText

      // Binary types
      case "BINARY"    => db.SqlServerType.Binary(characterMaximumLength)
      case "VARBINARY" => db.SqlServerType.VarBinary(characterMaximumLength)
      case "IMAGE"     => db.SqlServerType.Image

      // Date/Time types
      case "DATE"           => db.SqlServerType.Date
      case "TIME"           => db.SqlServerType.Time(None)
      case "DATETIME"       => db.SqlServerType.DateTime
      case "SMALLDATETIME"  => db.SqlServerType.SmallDateTime
      case "DATETIME2"      => db.SqlServerType.DateTime2(None)
      case "DATETIMEOFFSET" => db.SqlServerType.DateTimeOffset(None)

      // Special types
      case "UNIQUEIDENTIFIER"         => db.SqlServerType.UniqueIdentifier
      case "XML"                      => db.SqlServerType.Xml
      case "GEOGRAPHY"                => db.SqlServerType.Geography
      case "GEOMETRY"                 => db.SqlServerType.Geometry
      case "HIERARCHYID"              => db.SqlServerType.HierarchyId
      case "ROWVERSION" | "TIMESTAMP" => db.SqlServerType.RowVersion
      case "SQL_VARIANT"              => db.SqlServerType.SqlVariant

      case _ =>
        // For SQL file parameters where type couldn't be inferred, return Unknown
        db.Unknown(jdbcTypeName)
    }
  }

  /** Map SQL Server column type from INFORMATION_SCHEMA.COLUMNS to db.SqlServerType
    *
    * @param dataType
    *   DATA_TYPE column (e.g., "int", "nvarchar", "datetime2")
    * @param characterMaximumLength
    *   CHARACTER_MAXIMUM_LENGTH column (-1 for MAX types)
    * @param numericPrecision
    *   NUMERIC_PRECISION column
    * @param numericScale
    *   NUMERIC_SCALE column
    * @param datetimePrecision
    *   DATETIME_PRECISION column (fractional seconds precision)
    */
  def dbTypeFrom(
      dataType: String,
      characterMaximumLength: Option[Long],
      numericPrecision: Option[Long],
      numericScale: Option[Long],
      datetimePrecision: Option[Long]
  ): db.SqlServerType = {
    dataType.toLowerCase match {
      // Integer types
      case "tinyint"  => db.SqlServerType.TinyInt
      case "smallint" => db.SqlServerType.SmallInt
      case "int"      => db.SqlServerType.Int
      case "bigint"   => db.SqlServerType.BigInt

      // Fixed-point types
      case "decimal" | "numeric" =>
        db.SqlServerType.Decimal(numericPrecision.map(_.toInt), numericScale.map(_.toInt))
      case "money"      => db.SqlServerType.Money
      case "smallmoney" => db.SqlServerType.SmallMoney

      // Floating-point types
      case "real"  => db.SqlServerType.Real
      case "float" => db.SqlServerType.Float

      // Boolean (BIT)
      case "bit" => db.SqlServerType.Bit

      // String types (non-unicode)
      case "char" =>
        db.SqlServerType.Char(characterMaximumLength.map(_.toInt))
      case "varchar" =>
        // -1 indicates VARCHAR(MAX)
        db.SqlServerType.VarChar(characterMaximumLength.filter(_ != -1).map(_.toInt))
      case "text" =>
        db.SqlServerType.Text

      // String types (unicode)
      case "nchar" =>
        db.SqlServerType.NChar(characterMaximumLength.map(_.toInt))
      case "nvarchar" =>
        // -1 indicates NVARCHAR(MAX)
        db.SqlServerType.NVarChar(characterMaximumLength.filter(_ != -1).map(_.toInt))
      case "ntext" =>
        db.SqlServerType.NText

      // Binary types
      case "binary" =>
        db.SqlServerType.Binary(characterMaximumLength.map(_.toInt))
      case "varbinary" =>
        // -1 indicates VARBINARY(MAX)
        db.SqlServerType.VarBinary(characterMaximumLength.filter(_ != -1).map(_.toInt))
      case "image" =>
        db.SqlServerType.Image

      // Date/Time types
      case "date" => db.SqlServerType.Date
      case "time" =>
        db.SqlServerType.Time(datetimePrecision.map(_.toInt))
      case "datetime"      => db.SqlServerType.DateTime
      case "smalldatetime" => db.SqlServerType.SmallDateTime
      case "datetime2" =>
        db.SqlServerType.DateTime2(datetimePrecision.map(_.toInt))
      case "datetimeoffset" =>
        db.SqlServerType.DateTimeOffset(datetimePrecision.map(_.toInt))

      // Special types
      case "uniqueidentifier"         => db.SqlServerType.UniqueIdentifier
      case "xml"                      => db.SqlServerType.Xml
      case "geography"                => db.SqlServerType.Geography
      case "geometry"                 => db.SqlServerType.Geometry
      case "hierarchyid"              => db.SqlServerType.HierarchyId
      case "rowversion" | "timestamp" => db.SqlServerType.RowVersion
      case "sql_variant"              => db.SqlServerType.SqlVariant

      // JSON is stored as NVARCHAR(MAX) with a CHECK constraint in SQL Server 2016+
      // but INFORMATION_SCHEMA.COLUMNS will still report it as "nvarchar"
      // We'll detect JSON columns later via CHECK constraints if needed

      case _ =>
        sys.error(s"Unknown SQL Server data type: $dataType")
    }
  }
}
