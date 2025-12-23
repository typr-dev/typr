package typo
package internal
package codegen

import typo.jvm.Code

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

  val KotlinDbTypes: jvm.Type.Qualified = jvm.Type.Qualified("typo.kotlindsl.KotlinDbTypes")
  val KotlinNullableExtension: jvm.Type.Qualified = jvm.Type.Qualified("typo.kotlindsl.nullable")
  val ScalaDbTypes: jvm.Type.Qualified = jvm.Type.Qualified("typo.scaladsl.ScalaDbTypes")
  val ScalaDbTypeOps: jvm.Type.Qualified = jvm.Type.Qualified("typo.scaladsl.SqlServerTypeOps")
  val Types: jvm.Type.Qualified = jvm.Type.Qualified("typo.runtime.SqlServerTypes")
  val TypeClass: jvm.Type.Qualified = jvm.Type.Qualified("typo.runtime.SqlServerType")
  val TextClass: jvm.Type.Qualified = jvm.Type.Qualified("typo.runtime.SqlServerText")
  val typeFieldName: jvm.Ident = jvm.Ident("sqlServerType")
  val textFieldName: jvm.Ident = jvm.Ident("sqlServerText")
  def dialectRef(lang: Lang): Code = code"${lang.dsl.Dialect}.SQLSERVER"

  def lookupType(typoType: TypoType, naming: Naming, typeSupport: TypeSupport): Code =
    typoType match {
      case TypoType.Standard(_, dbType) =>
        lookupByDbType(dbType, typeSupport)

      case TypoType.Nullable(_, inner) =>
        val innerCode = lookupType(inner, naming, typeSupport)
        typeSupport match {
          case TypeSupportScala  => code"${jvm.Import(ScalaDbTypeOps)}$innerCode.nullable"
          case TypeSupportKotlin => code"${jvm.Import(KotlinNullableExtension)}$innerCode.nullable()"
          case _                 => code"$innerCode.opt()"
        }

      case TypoType.Generated(_, _, qualifiedType) =>
        code"$qualifiedType.$typeFieldName"

      case TypoType.UserDefined(_, _, userType) =>
        userType match {
          case Left(qualifiedType) =>
            code"$qualifiedType.$typeFieldName"
          case Right(primitive) =>
            lookupPrimitive(primitive, typeSupport)
        }

      case TypoType.Array(_, _) =>
        sys.error("SqlServerAdapter.lookupType: SQL Server does not support array types")
    }

  def lookupPrimitive(primitive: analysis.WellKnownPrimitive, typeSupport: TypeSupport): Code = {
    def primitiveType(name: String): Code = {
      val nameIdent = jvm.Ident(name)
      typeSupport match {
        case TypeSupportScala    => code"$ScalaDbTypes.SqlServerTypes.$nameIdent"
        case TypeSupportKotlin   => code"$KotlinDbTypes.SqlServerTypes.$nameIdent"
        case TypeSupportJava | _ => code"$Types.$nameIdent"
      }
    }

    primitive match {
      case analysis.WellKnownPrimitive.String        => code"$Types.nvarchar" // Unicode by default
      case analysis.WellKnownPrimitive.Boolean       => primitiveType("bit")
      case analysis.WellKnownPrimitive.Byte          => sys.error("SQL Server TINYINT is unsigned - use Short")
      case analysis.WellKnownPrimitive.Short         => primitiveType("smallint")
      case analysis.WellKnownPrimitive.Int           => primitiveType("int_")
      case analysis.WellKnownPrimitive.Long          => primitiveType("bigint")
      case analysis.WellKnownPrimitive.Float         => primitiveType("real")
      case analysis.WellKnownPrimitive.Double        => primitiveType("float_")
      case analysis.WellKnownPrimitive.BigDecimal    => primitiveType("numeric")
      case analysis.WellKnownPrimitive.LocalDate     => code"$Types.date"
      case analysis.WellKnownPrimitive.LocalTime     => code"$Types.time"
      case analysis.WellKnownPrimitive.LocalDateTime => code"$Types.datetime2"
      case analysis.WellKnownPrimitive.Instant       => code"$Types.datetimeoffset"
      case analysis.WellKnownPrimitive.UUID          => code"$Types.uniqueidentifier"
    }
  }

  def lookupTypeByDbType(dbType: db.Type, Types: jvm.Type.Qualified, naming: Naming, typeSupport: TypeSupport): Code =
    lookupByDbType(dbType, typeSupport)

  def textType: db.Type = db.SqlServerType.NVarChar(None) // NVARCHAR(MAX)

  private def lookupByDbType(dbType: db.Type, typeSupport: TypeSupport): Code = {
    def primitiveType(name: String): Code = {
      val nameIdent = jvm.Ident(name)
      typeSupport match {
        case TypeSupportScala    => code"$ScalaDbTypes.SqlServerTypes.$nameIdent"
        case TypeSupportKotlin   => code"$KotlinDbTypes.SqlServerTypes.$nameIdent"
        case TypeSupportJava | _ => code"$Types.$nameIdent"
      }
    }

    dbType match {
      case db.Unknown(sqlType) =>
        sys.error(s"SqlServerAdapter.lookupByDbType: unknown SQL Server type: $sqlType")

      case dbType: db.SqlServerType =>
        dbType match {
          // Unknown case handled in outer match, but needed here for exhaustiveness
          case db.Unknown(sqlType) =>
            sys.error(s"SqlServerAdapter.lookupByDbType: unknown SQL Server type: $sqlType")

          // Primitive types with language-specific overrides
          case db.SqlServerType.TinyInt       => primitiveType("tinyint") // Unsigned!
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
          case db.SqlServerType.Char(_)     => code"$Types.char_"
          case db.SqlServerType.VarChar(_)  => code"$Types.varchar"
          case db.SqlServerType.Text        => code"$Types.text"
          case db.SqlServerType.NChar(_)    => code"$Types.nchar"
          case db.SqlServerType.NVarChar(_) => code"$Types.nvarchar"
          case db.SqlServerType.NText       => code"$Types.ntext"

          // Binary types
          case db.SqlServerType.Binary(_)    => code"$Types.binary"
          case db.SqlServerType.VarBinary(_) => code"$Types.varbinary"
          case db.SqlServerType.Image        => code"$Types.image"

          // Date/Time types
          case db.SqlServerType.Date              => code"$Types.date"
          case db.SqlServerType.Time(_)           => code"$Types.time"
          case db.SqlServerType.DateTime          => code"$Types.datetime"
          case db.SqlServerType.SmallDateTime     => code"$Types.smalldatetime"
          case db.SqlServerType.DateTime2(_)      => code"$Types.datetime2"
          case db.SqlServerType.DateTimeOffset(_) => code"$Types.datetimeoffset"

          // Special types
          case db.SqlServerType.UniqueIdentifier => code"$Types.uniqueidentifier"
          case db.SqlServerType.Xml              => code"$Types.xml"
          case db.SqlServerType.Json             => code"$Types.json"
          case db.SqlServerType.Vector           => code"$Types.vector"
          case db.SqlServerType.RowVersion       => code"$Types.rowversion"
          case db.SqlServerType.HierarchyId      => code"$Types.hierarchyid"
          case db.SqlServerType.SqlVariant       => code"$Types.sqlVariant"
          case db.SqlServerType.Geography        => code"$Types.geography"
          case db.SqlServerType.Geometry         => code"$Types.geometry"

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
    // This is a simplified version - for production you'd want to generate a proper MERGE
    returning match {
      case Some(cols) =>
        // cols already has INSERTED. prefix from returningColumns
        code"""|MERGE INTO $tableName AS target
               |USING (VALUES ($values)) AS source($columns)
               |ON target.$idColumns = source.$idColumns
               |WHEN MATCHED THEN UPDATE SET $conflictUpdate
               |WHEN NOT MATCHED THEN INSERT ($columns) VALUES ($values)
               |OUTPUT $cols;""".stripMargin
      case None =>
        code"""|MERGE INTO $tableName AS target
               |USING (VALUES ($values)) AS source($columns)
               |ON target.$idColumns = source.$idColumns
               |WHEN MATCHED THEN UPDATE SET $conflictUpdate
               |WHEN NOT MATCHED THEN INSERT ($columns) VALUES ($values);""".stripMargin
    }
  }

  def conflictUpdateClause(cols: List[ComputedColumn], quotedColName: ComputedColumn => Code): Code =
    cols.map(c => code"${quotedColName(c)} = source.${quotedColName(c)}").mkCode(",\n")

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
}
