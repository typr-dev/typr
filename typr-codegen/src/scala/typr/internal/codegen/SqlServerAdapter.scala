package typr
package internal
package codegen

import typr.jvm.Code

object SqlServerAdapter extends DbAdapter {
  val dbType: DbType = DbType.SqlServer

  // ═══════════════════════════════════════════════════════════════════════════
  // LAYER 1: SQL Syntax
  // ═══════════════════════════════════════════════════════════════════════════

  def quoteIdent(name: String): String = s"[$name]" // SQL Server bracket style

  def typeCast(value: Code, typeName: String): Code =
    if (typeName.isEmpty) value else code"CAST($value AS $typeName)"

  // SQL Server doesn't need special read/write casts like PostgreSQL
  def columnReadCast(col: ComputedColumn): Code = Code.Empty
  def columnWriteCast(col: ComputedColumn): Code = Code.Empty
  def writeCastTypeName(col: ComputedColumn): Option[String] = None
  def writeCast(dbType: db.Type, udtName: Option[String]): Option[SqlCastValue] = None
  def readCast(dbType: db.Type): Option[SqlCastValue] = None

  // ═══════════════════════════════════════════════════════════════════════════
  // LAYER 2: Runtime Type System
  // ═══════════════════════════════════════════════════════════════════════════

  override val KotlinTypes: jvm.Type.Qualified = jvm.Type.Qualified("dev.typr.foundationskt.SqlServerTypes")
  override val ScalaTypes: jvm.Type.Qualified = jvm.Type.Qualified("dev.typr.foundationssc.SqlServerTypes")
  val Types: jvm.Type.Qualified = jvm.Type.Qualified("dev.typr.foundations.SqlServerTypes")
  val TypeClass: jvm.Type.Qualified = jvm.Type.Qualified("dev.typr.foundations.SqlServerType")
  val TextClass: jvm.Type.Qualified = jvm.Type.Qualified("dev.typr.foundations.SqlServerText")
  val typeFieldName: jvm.Ident = jvm.Ident("sqlServerType")
  val textFieldName: jvm.Ident = jvm.Ident("sqlServerText")
  def dialectRef(lang: Lang): Code = code"${lang.dsl.Dialect}.SQLSERVER"

  def lookupType(typoType: TypoType, naming: Naming, typeSupport: TypeSupport): Code =
    typoType match {
      case TypoType.Standard(_, dbType) =>
        lookupByDbType(dbType, typeSupport)

      case TypoType.Nullable(_, inner) =>
        nullableType(lookupType(inner, naming, typeSupport))

      case TypoType.Generated(_, _, qualifiedType) =>
        code"$qualifiedType.$typeFieldName"

      // Precise types - wrapper types with constraints, all have their own sqlServerType field
      case TypoType.StringN(_, _, _, qualifiedType)         => code"$qualifiedType.$typeFieldName"
      case TypoType.NonEmptyString(_, _, qualifiedType)     => code"$qualifiedType.$typeFieldName"
      case TypoType.NonEmptyStringN(_, _, _, qualifiedType) => code"$qualifiedType.$typeFieldName"
      case TypoType.BinaryN(_, _, _, qualifiedType)         => code"$qualifiedType.$typeFieldName"
      case TypoType.DecimalN(_, _, _, _, qualifiedType)     => code"$qualifiedType.$typeFieldName"
      case TypoType.LocalDateTimeN(_, _, _, qualifiedType)  => code"$qualifiedType.$typeFieldName"
      case TypoType.InstantN(_, _, _, qualifiedType)        => code"$qualifiedType.$typeFieldName"
      case TypoType.LocalTimeN(_, _, _, qualifiedType)      => code"$qualifiedType.$typeFieldName"
      case TypoType.OffsetDateTimeN(_, _, _, qualifiedType) => code"$qualifiedType.$typeFieldName"

      case TypoType.UserDefined(_, _, userType) =>
        userType match {
          case Left(qualifiedType) =>
            code"$qualifiedType.$typeFieldName"
          case Right(primitive) =>
            lookupPrimitive(primitive, typeSupport)
        }

      case TypoType.Array(_, _) =>
        sys.error("SqlServerAdapter.lookupType: SQL Server does not support array types")

      case TypoType.Aligned(_, sourceType, _, _) =>
        lookupType(sourceType, naming, typeSupport)
    }

  def lookupPrimitive(primitive: analysis.WellKnownPrimitive, typeSupport: TypeSupport): Code = {
    def primitiveType(name: String): Code = {
      val nameIdent = jvm.Ident(name)
      typeSupport match {
        case TypeSupportScala    => code"$ScalaTypes.$nameIdent"
        case TypeSupportKotlin   => code"$KotlinTypes.$nameIdent"
        case TypeSupportJava | _ => code"$Types.$nameIdent"
      }
    }

    primitive match {
      case analysis.WellKnownPrimitive.String        => primitiveType("nvarchar")
      case analysis.WellKnownPrimitive.Boolean       => primitiveType("bit")
      case analysis.WellKnownPrimitive.Byte          => sys.error("SQL Server TINYINT is unsigned - use Short")
      case analysis.WellKnownPrimitive.Short         => primitiveType("smallint")
      case analysis.WellKnownPrimitive.Int           => primitiveType("int_")
      case analysis.WellKnownPrimitive.Long          => primitiveType("bigint")
      case analysis.WellKnownPrimitive.Float         => primitiveType("real")
      case analysis.WellKnownPrimitive.Double        => primitiveType("float_")
      case analysis.WellKnownPrimitive.BigDecimal    => primitiveType("numeric")
      case analysis.WellKnownPrimitive.LocalDate     => primitiveType("date")
      case analysis.WellKnownPrimitive.LocalTime     => primitiveType("time")
      case analysis.WellKnownPrimitive.LocalDateTime => primitiveType("datetime2")
      case analysis.WellKnownPrimitive.Instant       => primitiveType("datetimeoffset")
      case analysis.WellKnownPrimitive.UUID          => primitiveType("uniqueidentifier")
    }
  }

  def lookupTypeByDbType(dbType: db.Type, Types: jvm.Type.Qualified, naming: Naming, typeSupport: TypeSupport): Code =
    lookupByDbType(dbType, typeSupport)

  def textType: db.Type = db.SqlServerType.NVarChar(None) // NVARCHAR(MAX)

  private def lookupByDbType(dbType: db.Type, typeSupport: TypeSupport): Code = {
    def primitiveType(name: String): Code = {
      val nameIdent = jvm.Ident(name)
      typeSupport match {
        case TypeSupportScala    => code"$ScalaTypes.$nameIdent"
        case TypeSupportKotlin   => code"$KotlinTypes.$nameIdent"
        case TypeSupportJava | _ => code"$Types.$nameIdent"
      }
    }

    dbType match {
      case dbType: db.SqlServerType =>
        dbType match {
          case db.Unknown(sqlType)            => primitiveType("unknown")
          case db.SqlServerType.TinyInt       => primitiveType("tinyint")
          case db.SqlServerType.SmallInt      => primitiveType("smallint")
          case db.SqlServerType.Int           => primitiveType("int_")
          case db.SqlServerType.BigInt        => primitiveType("bigint")
          case db.SqlServerType.Real          => primitiveType("real")
          case db.SqlServerType.Float         => primitiveType("float_")
          case db.SqlServerType.Decimal(_, _) => primitiveType("decimal")
          case db.SqlServerType.Numeric(_, _) => primitiveType("numeric")
          case db.SqlServerType.Money         => primitiveType("money")
          case db.SqlServerType.SmallMoney    => primitiveType("smallmoney")
          case db.SqlServerType.Bit           => primitiveType("bit")

          // String types
          case db.SqlServerType.Char(_)     => primitiveType("char_")
          case db.SqlServerType.VarChar(_)  => primitiveType("varchar")
          case db.SqlServerType.Text        => primitiveType("text")
          case db.SqlServerType.NChar(_)    => primitiveType("nchar")
          case db.SqlServerType.NVarChar(_) => primitiveType("nvarchar")
          case db.SqlServerType.NText       => primitiveType("ntext")

          // Binary types
          case db.SqlServerType.Binary(_)    => primitiveType("binary")
          case db.SqlServerType.VarBinary(_) => primitiveType("varbinary")
          case db.SqlServerType.Image        => primitiveType("image")

          // Date/Time types
          case db.SqlServerType.Date              => primitiveType("date")
          case db.SqlServerType.Time(_)           => primitiveType("time")
          case db.SqlServerType.DateTime          => primitiveType("datetime")
          case db.SqlServerType.SmallDateTime     => primitiveType("smalldatetime")
          case db.SqlServerType.DateTime2(_)      => primitiveType("datetime2")
          case db.SqlServerType.DateTimeOffset(_) => primitiveType("datetimeoffset")

          // Special types
          case db.SqlServerType.UniqueIdentifier => primitiveType("uniqueidentifier")
          case db.SqlServerType.Xml              => primitiveType("xml")
          case db.SqlServerType.Json             => primitiveType("json")
          case db.SqlServerType.Vector           => primitiveType("vector")
          case db.SqlServerType.RowVersion       => primitiveType("rowversion")
          case db.SqlServerType.HierarchyId      => primitiveType("hierarchyid")
          case db.SqlServerType.SqlVariant       => primitiveType("sqlVariant")
          case db.SqlServerType.Geography        => primitiveType("geography")
          case db.SqlServerType.Geometry         => primitiveType("geometry")

          // Complex types (future)
          case db.SqlServerType.TableTypeRef(_, _)       => sys.error("Table-valued types not yet supported")
          case db.SqlServerType.AliasTypeRef(_, _, _, _) => sys.error("Alias types not yet supported")
          case db.SqlServerType.ClrTypeRef(_, _, _)      => sys.error("CLR types not yet supported")
        }

      case _ =>
        sys.error(s"SqlServerAdapter.lookupByDbType: unsupported type $dbType")
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // LAYER 3: Capabilities
  // ═══════════════════════════════════════════════════════════════════════════

  def returningStrategy(cols: NonEmptyList[ComputedColumn], rowType: jvm.Type, maybeId: Option[IdComputed]): ReturningStrategy =
    // SQL Server uses OUTPUT clause which returns columns directly in the result set
    ReturningStrategy.SqlReturning(rowType)

  /** SQL Server supports OUTPUT with MERGE */
  def upsertStrategy(rowType: jvm.Type): UpsertStrategy =
    UpsertStrategy.Returning(rowType)

  def supportsReturning: Boolean = true // OUTPUT clause
  def supportsArrays: Boolean = false
  def supportsCopyStreaming: Boolean = false
  val supportsDefaultInCopy: Boolean = false

  // ═══════════════════════════════════════════════════════════════════════════
  // LAYER 4: SQL Templates
  // ═══════════════════════════════════════════════════════════════════════════

  def upsertSql(
      tableName: Code,
      columns: Code,
      idColumns: Code,
      values: Code,
      conflictUpdate: Code,
      returning: Option[Code]
  ): Code = {
    // SQL Server uses MERGE statement for UPSERT
    // idColumns is already a proper join condition like "target.[col] = source.[col]"
    returning match {
      case Some(cols) =>
        // cols already has INSERTED. prefix from returningColumns
        code"""|MERGE INTO $tableName AS target
               |USING (VALUES ($values)) AS source($columns)
               |ON $idColumns
               |WHEN MATCHED THEN UPDATE SET $conflictUpdate
               |WHEN NOT MATCHED THEN INSERT ($columns) VALUES ($values)
               |OUTPUT $cols;""".stripMargin
      case None =>
        code"""|MERGE INTO $tableName AS target
               |USING (VALUES ($values)) AS source($columns)
               |ON $idColumns
               |WHEN MATCHED THEN UPDATE SET $conflictUpdate
               |WHEN NOT MATCHED THEN INSERT ($columns) VALUES ($values);""".stripMargin
    }
  }

  def conflictUpdateClause(cols: List[ComputedColumn], quotedColName: ComputedColumn => Code): Code =
    cols.map(c => code"${quotedColName(c)} = source.${quotedColName(c)}").mkCode(",\n")

  /** SQL Server uses target and source as MERGE aliases */
  override def mergeOnClause(idCols: NonEmptyList[ComputedColumn], quotedColName: ComputedColumn => Code): Code =
    idCols.map(c => code"target.${quotedColName(c)} = source.${quotedColName(c)}").mkCode(" AND ")

  def conflictNoOpClause(firstPkCol: ComputedColumn, quotedColName: ComputedColumn => Code): Code =
    // For no-op, we just set the same column to itself
    code"${quotedColName(firstPkCol)} = target.${quotedColName(firstPkCol)}"

  def streamingInsertSql(tableName: Code, columns: Code): Code =
    // SQL Server doesn't have COPY-like functionality, use batch inserts or BULK INSERT
    code"/* SQL Server doesn't support streaming inserts like COPY, use batch inserts or BULK INSERT */"

  def createTempTableLike(tempName: String, sourceTable: Code): Code =
    code"SELECT TOP 0 * INTO #$tempName FROM $sourceTable"

  override def returningColumns(cols: NonEmptyList[ComputedColumn]): Code =
    // SQL Server needs INSERTED. prefix on each column in OUTPUT clause
    cols.map(c => code"INSERTED.${quotedColName(c)}" ++ columnReadCast(c)).mkCode(", ")

  def returningClause(columns: Code): Code =
    code"OUTPUT $columns"

  override def returningBeforeValues: Boolean = true

  // ═══════════════════════════════════════════════════════════════════════════
  // LAYER 5: Schema DDL
  // ═══════════════════════════════════════════════════════════════════════════

  def dropSchemaDdl(schemaName: String, cascade: Boolean): String =
    s"IF EXISTS (SELECT * FROM sys.schemas WHERE name = '$schemaName') DROP SCHEMA [$schemaName]"

  def createSchemaDdl(schemaName: String): String =
    s"IF NOT EXISTS (SELECT * FROM sys.schemas WHERE name = '$schemaName') EXEC('CREATE SCHEMA [$schemaName]')"
}
