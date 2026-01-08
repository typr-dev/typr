package typr
package internal
package codegen

import typr.jvm.Code

object Db2Adapter extends DbAdapter {
  val dbType: DbType = DbType.DB2

  // ═══════════════════════════════════════════════════════════════════════════
  // LAYER 1: SQL Syntax
  // ═══════════════════════════════════════════════════════════════════════════

  // DB2 uses double quotes for identifiers (like PostgreSQL/Oracle)
  def quoteIdent(name: String): String = s""""$name""""

  def typeCast(value: Code, typeName: String): Code =
    if (typeName.isEmpty) value else code"CAST($value AS $typeName)"

  // DB2 doesn't need PostgreSQL-style casts
  def columnReadCast(col: ComputedColumn): Code = Code.Empty
  def columnWriteCast(col: ComputedColumn): Code = Code.Empty
  def writeCastTypeName(col: ComputedColumn): Option[String] = None
  def writeCast(dbType: db.Type, udtName: Option[String]): Option[SqlCastValue] = None
  def readCast(dbType: db.Type): Option[SqlCastValue] = None

  def dropSchemaDdl(schemaName: String, cascade: Boolean): String = {
    // DB2 uses RESTRICT by default, CASCADE not directly supported the same way
    // For cascade, you'd need to drop objects first
    s"DROP SCHEMA $schemaName RESTRICT"
  }

  def createSchemaDdl(schemaName: String): String =
    s"CREATE SCHEMA $schemaName"

  // ═══════════════════════════════════════════════════════════════════════════
  // LAYER 2: Runtime Type System
  // ═══════════════════════════════════════════════════════════════════════════

  val KotlinDbTypes: jvm.Type.Qualified = jvm.Type.Qualified("dev.typr.foundations.kotlin.KotlinDbTypes")
  val ScalaDbTypes: jvm.Type.Qualified = jvm.Type.Qualified("dev.typr.foundations.scala.ScalaDbTypes")
  val Types: jvm.Type.Qualified = jvm.Type.Qualified("dev.typr.foundations.Db2Types")
  val TypeClass: jvm.Type.Qualified = jvm.Type.Qualified("dev.typr.foundations.Db2Type")
  val TextClass: jvm.Type.Qualified = jvm.Type.Qualified("dev.typr.foundations.Db2Text")
  val typeFieldName: jvm.Ident = jvm.Ident("dbType")
  val textFieldName: jvm.Ident = jvm.Ident("db2Text")
  def dialectRef(lang: Lang): Code = code"${lang.dsl.Dialect}.DB2"

  def lookupType(typoType: TypoType, naming: Naming, typeSupport: TypeSupport): Code =
    typoType match {
      case TypoType.Standard(_, dbType) =>
        lookupByDbType(dbType, typeSupport)

      case TypoType.Nullable(_, inner) =>
        nullableType(lookupType(inner, naming, typeSupport), typeSupport)

      case TypoType.Generated(_, _, qualifiedType) =>
        code"$qualifiedType.$typeFieldName"

      // Precise types - wrapper types with constraints, all have their own dbType field
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
        sys.error("Db2Adapter.lookupType: DB2 does not support array types")
    }

  def lookupPrimitive(primitive: analysis.WellKnownPrimitive, typeSupport: TypeSupport): Code = {
    def primitiveType(name: String): Code = {
      val nameIdent = jvm.Ident(name)
      typeSupport match {
        case TypeSupportScala    => code"$ScalaDbTypes.Db2Types.$nameIdent"
        case TypeSupportKotlin   => code"$KotlinDbTypes.Db2Types.$nameIdent"
        case TypeSupportJava | _ => code"$Types.$nameIdent"
      }
    }

    primitive match {
      case analysis.WellKnownPrimitive.String        => code"$Types.varchar"
      case analysis.WellKnownPrimitive.Boolean       => primitiveType("boolean_")
      case analysis.WellKnownPrimitive.Byte          => primitiveType("smallint") // DB2 has no TINYINT
      case analysis.WellKnownPrimitive.Short         => primitiveType("smallint")
      case analysis.WellKnownPrimitive.Int           => primitiveType("integer")
      case analysis.WellKnownPrimitive.Long          => primitiveType("bigint")
      case analysis.WellKnownPrimitive.Float         => primitiveType("real")
      case analysis.WellKnownPrimitive.Double        => primitiveType("double_")
      case analysis.WellKnownPrimitive.BigDecimal    => primitiveType("decimal")
      case analysis.WellKnownPrimitive.LocalDate     => code"$Types.date"
      case analysis.WellKnownPrimitive.LocalTime     => code"$Types.time"
      case analysis.WellKnownPrimitive.LocalDateTime => code"$Types.timestamp"
      case analysis.WellKnownPrimitive.Instant       => code"$Types.timestamp"
      case analysis.WellKnownPrimitive.UUID          => code"$Types.char_" // UUID as CHAR(36) in DB2
    }
  }

  def textType: db.Type = db.DB2Type.Clob

  private def lookupByDbType(dbType: db.Type, typeSupport: TypeSupport): Code = {
    def primitiveType(name: String): Code = {
      val nameIdent = jvm.Ident(name)
      typeSupport match {
        case TypeSupportScala    => code"$ScalaDbTypes.Db2Types.$nameIdent"
        case TypeSupportKotlin   => code"$KotlinDbTypes.Db2Types.$nameIdent"
        case TypeSupportJava | _ => code"$Types.$nameIdent"
      }
    }

    dbType match {
      case dbType: db.DB2Type =>
        dbType match {
          // Integer types
          case db.DB2Type.SmallInt => primitiveType("smallint")
          case db.DB2Type.Integer  => primitiveType("integer")
          case db.DB2Type.BigInt   => primitiveType("bigint")

          // Fixed-point
          case db.DB2Type.Decimal(_, _) => primitiveType("decimal")
          case db.DB2Type.DecFloat(_)   => primitiveType("decfloat")

          // Floating-point
          case db.DB2Type.Real   => primitiveType("real")
          case db.DB2Type.Double => primitiveType("double_")

          // Boolean
          case db.DB2Type.Boolean => primitiveType("boolean_")

          // Character types (SBCS)
          case db.DB2Type.Char(_)    => code"$Types.char_"
          case db.DB2Type.VarChar(_) => code"$Types.varchar"
          case db.DB2Type.Clob       => code"$Types.clob"
          case db.DB2Type.Long       => code"$Types.varchar" // Deprecated, map to VARCHAR

          // Character types (DBCS/Graphic)
          case db.DB2Type.Graphic(_)     => code"$Types.graphic"
          case db.DB2Type.VarGraphic(_)  => code"$Types.vargraphic"
          case db.DB2Type.DbClob         => code"$Types.dbclob"
          case db.DB2Type.LongVarGraphic => code"$Types.vargraphic" // Deprecated

          // Binary types
          case db.DB2Type.Binary(_)    => code"$Types.binary"
          case db.DB2Type.VarBinary(_) => code"$Types.varbinary"
          case db.DB2Type.Blob         => code"$Types.blob"

          // Date/Time types
          case db.DB2Type.Date         => code"$Types.date"
          case db.DB2Type.Time         => code"$Types.time"
          case db.DB2Type.Timestamp(_) => code"$Types.timestamp"

          // XML type
          case db.DB2Type.Xml => code"$Types.xml"

          // Row ID
          case db.DB2Type.RowId => code"$Types.rowid"

          // Distinct types - use the underlying type
          case db.DB2Type.DistinctType(_, _) => code"$Types.varchar" // Fallback

          case db.Unknown(_) => code"$Types.unknown"
        }
      case _ =>
        sys.error(s"Db2Adapter.lookupByDbType: Cannot lookup db type from another database")
    }
  }

  def lookupTypeByDbType(dbType: db.Type, Types: jvm.Type.Qualified, naming: Naming, typeSupport: TypeSupport): Code =
    lookupByDbType(dbType, typeSupport)

  // ═══════════════════════════════════════════════════════════════════════════
  // LAYER 3: Capabilities
  // ═══════════════════════════════════════════════════════════════════════════

  val supportsArrays: Boolean = false
  val supportsReturning: Boolean = true // Via SELECT FROM FINAL TABLE
  val supportsCopyStreaming: Boolean = false // DB2 has no COPY command
  val supportsDefaultInCopy: Boolean = false

  /** DB2 uses SELECT FROM FINAL TABLE for RETURNING behavior */
  def returningStrategy(cols: NonEmptyList[ComputedColumn], rowType: jvm.Type, maybeId: Option[IdComputed]): ReturningStrategy =
    ReturningStrategy.SqlReturning(rowType) // Will need special handling for SELECT FROM FINAL TABLE

  /** DB2 doesn't support SELECT FROM FINAL TABLE with MERGE */
  def upsertStrategy(rowType: jvm.Type): UpsertStrategy =
    UpsertStrategy.NotSupported

  // ═══════════════════════════════════════════════════════════════════════════
  // LAYER 4: SQL Templates
  // ═══════════════════════════════════════════════════════════════════════════

  /** DB2 uses MERGE for upserts */
  def upsertSql(
      tableName: Code,
      columns: Code,
      idColumns: Code,
      values: Code,
      conflictUpdate: Code,
      returning: Option[Code]
  ): Code = {
    // DB2 MERGE statement
    // idColumns is already a proper join condition like "t."col" = s."col""
    val mergeBase =
      code"""|MERGE INTO $tableName AS t
             |USING (VALUES ($values)) AS s($columns)
             |ON $idColumns
             |WHEN MATCHED THEN UPDATE SET $conflictUpdate
             |WHEN NOT MATCHED THEN INSERT ($columns) VALUES ($values)""".stripMargin

    returning match {
      case Some(cols) =>
        // Wrap in SELECT FROM FINAL TABLE for RETURNING behavior
        code"SELECT $cols FROM FINAL TABLE ($mergeBase)"
      case None =>
        mergeBase
    }
  }

  def conflictUpdateClause(cols: List[ComputedColumn], quotedColName: ComputedColumn => Code): Code =
    cols.map(c => code"${quotedColName(c)} = s.${quotedColName(c)}").mkCode(",\n")

  def conflictNoOpClause(firstPkCol: ComputedColumn, quotedColName: ComputedColumn => Code): Code =
    code"${quotedColName(firstPkCol)} = t.${quotedColName(firstPkCol)}"

  def streamingInsertSql(tableName: Code, columns: Code): Code =
    code"/* DB2 LOAD not directly supported, use batch inserts */"

  def createTempTableLike(tempName: String, sourceTable: Code): Code =
    code"CREATE GLOBAL TEMPORARY TABLE $tempName LIKE $sourceTable ON COMMIT PRESERVE ROWS"

  /** DB2 uses SELECT FROM FINAL TABLE pattern - not used directly since insertReturning is overridden */
  def returningClause(columns: Code): Code =
    code"/* Use SELECT FROM FINAL TABLE wrapper */"

  /** Override to wrap INSERT in SELECT FROM FINAL TABLE for DB2 */
  override def insertReturning(
      tableName: Code,
      columns: Code,
      values: Code,
      returningCols: NonEmptyList[ComputedColumn]
  ): Code = {
    val returnColsCode = returningColumns(returningCols)
    code"""|SELECT $returnColsCode FROM FINAL TABLE (INSERT INTO $tableName($columns)
           |VALUES ($values))
           |""".stripMargin
  }

  /** Override to wrap INSERT DEFAULT VALUES in SELECT FROM FINAL TABLE for DB2 */
  override def insertDefaultValuesReturning(
      tableName: Code,
      returningCols: NonEmptyList[ComputedColumn]
  ): Code = {
    val returnColsCode = returningColumns(returningCols)
    code"""|SELECT $returnColsCode FROM FINAL TABLE (INSERT INTO $tableName
           |DEFAULT VALUES)
           |""".stripMargin
  }
}
