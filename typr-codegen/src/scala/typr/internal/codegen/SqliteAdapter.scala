package typr
package internal
package codegen

import typr.jvm.Code

object SqliteAdapter extends DbAdapter {
  val dbType: DbType = DbType.SQLite

  // ═══════════════════════════════════════════════════════════════════════════
  // LAYER 1: SQL Syntax
  // ═══════════════════════════════════════════════════════════════════════════

  def quoteIdent(name: String): String = s""""$name""""

  def typeCast(value: Code, typeName: String): Code =
    if (typeName.isEmpty) value else code"CAST($value AS $typeName)"

  def columnReadCast(col: ComputedColumn): Code = Code.Empty
  def columnWriteCast(col: ComputedColumn): Code = Code.Empty
  def writeCastTypeName(col: ComputedColumn): Option[String] = None
  def writeCast(dbType: db.Type, udtName: Option[String]): Option[SqlCastValue] = None
  def readCast(dbType: db.Type): Option[SqlCastValue] = None

  // ═══════════════════════════════════════════════════════════════════════════
  // LAYER 2: Runtime Type System
  // ═══════════════════════════════════════════════════════════════════════════

  override val KotlinTypes: jvm.Type.Qualified = jvm.Type.Qualified("dev.typr.foundationskt.SqliteTypes")
  override val ScalaTypes: jvm.Type.Qualified = jvm.Type.Qualified("dev.typr.foundationssc.SqliteTypes")
  val Types: jvm.Type.Qualified = jvm.Type.Qualified("dev.typr.foundations.SqliteTypes")
  val TypeClass: jvm.Type.Qualified = jvm.Type.Qualified("dev.typr.foundations.SqliteType")
  val TextClass: jvm.Type.Qualified = jvm.Type.Qualified("dev.typr.foundations.SqliteText")
  val typeFieldName: jvm.Ident = jvm.Ident("sqliteType")
  val textFieldName: jvm.Ident = jvm.Ident("sqliteText")
  def dialectRef(lang: Lang): Code = code"${lang.dsl.Dialect}.SQLITE"

  override def numericTypeName: String = "numeric"

  def lookupType(typoType: TypoType, naming: Naming, typeSupport: TypeSupport): Code =
    typoType match {
      case TypoType.Standard(_, dbType) =>
        lookupByDbType(dbType, typeSupport)

      case TypoType.Nullable(_, inner) =>
        nullableType(lookupType(inner, naming, typeSupport))

      case TypoType.Generated(_, _, qualifiedType) =>
        code"$qualifiedType.$typeFieldName"

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
          case Left(qualifiedType) => code"$qualifiedType.$typeFieldName"
          case Right(primitive)    => lookupPrimitive(primitive, typeSupport)
        }

      case TypoType.Array(_, _) =>
        sys.error("SqliteAdapter.lookupType: SQLite does not support array types")

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
      case analysis.WellKnownPrimitive.String        => primitiveType("text")
      case analysis.WellKnownPrimitive.Boolean       => primitiveType("boolean_")
      case analysis.WellKnownPrimitive.Byte          => primitiveType("tinyint")
      case analysis.WellKnownPrimitive.Short         => primitiveType("smallint")
      case analysis.WellKnownPrimitive.Int           => primitiveType("int_")
      case analysis.WellKnownPrimitive.Long          => primitiveType("integer")
      case analysis.WellKnownPrimitive.Float         => primitiveType("float_")
      case analysis.WellKnownPrimitive.Double        => primitiveType("real")
      case analysis.WellKnownPrimitive.BigDecimal    => primitiveType("numeric")
      case analysis.WellKnownPrimitive.LocalDate     => primitiveType("date")
      case analysis.WellKnownPrimitive.LocalTime     => primitiveType("time")
      case analysis.WellKnownPrimitive.LocalDateTime => primitiveType("datetime")
      case analysis.WellKnownPrimitive.Instant       => primitiveType("instant")
      case analysis.WellKnownPrimitive.UUID          => primitiveType("uuid")
    }
  }

  def textType: db.Type = db.SqliteType.Text

  def lookupTypeByDbType(dbType: db.Type, Types: jvm.Type.Qualified, naming: Naming, typeSupport: TypeSupport): Code =
    lookupByDbType(dbType, typeSupport)

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
      case dbType: db.SqliteType =>
        dbType match {
          // INTEGER affinity
          case db.SqliteType.Integer  => primitiveType("integer")
          case db.SqliteType.BigInt   => primitiveType("bigint")
          case db.SqliteType.Int      => primitiveType("int_")
          case db.SqliteType.SmallInt => primitiveType("smallint")
          case db.SqliteType.TinyInt  => primitiveType("tinyint")
          case db.SqliteType.Boolean  => primitiveType("boolean_")

          // REAL affinity
          case db.SqliteType.Real   => primitiveType("real")
          case db.SqliteType.Double => primitiveType("double_")
          case db.SqliteType.Float  => primitiveType("float_")

          // NUMERIC affinity
          case db.SqliteType.Decimal(_, _) => primitiveType("numeric")

          // TEXT affinity
          case db.SqliteType.Text       => primitiveType("text")
          case db.SqliteType.VarChar(_) => primitiveType("varchar")
          case db.SqliteType.Char(_)    => primitiveType("char_")
          case db.SqliteType.Clob       => primitiveType("clob")

          // BLOB affinity
          case db.SqliteType.Blob      => primitiveType("blob")
          case db.SqliteType.Binary    => primitiveType("binary")
          case db.SqliteType.VarBinary => primitiveType("varbinary")

          // Date/time
          case db.SqliteType.Date      => primitiveType("date")
          case db.SqliteType.Time      => primitiveType("time")
          case db.SqliteType.Timestamp => primitiveType("timestamp")
          case db.SqliteType.Instant   => primitiveType("instant")

          // Convenience
          case db.SqliteType.Uuid => primitiveType("uuid")
          case db.SqliteType.Json => primitiveType("json")

          case db.Unknown(_) => primitiveType("unknown")
        }
      case _ =>
        sys.error("SqliteAdapter.lookupByDbType: Cannot lookup type from another database")
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // LAYER 3: Capabilities
  // ═══════════════════════════════════════════════════════════════════════════

  val supportsArrays: Boolean = false // SQLite has no native array type
  val supportsReturning: Boolean = true // since SQLite 3.35 (2021)
  val supportsCopyStreaming: Boolean = false
  val supportsDefaultInCopy: Boolean = false

  def returningStrategy(cols: NonEmptyList[ComputedColumn], rowType: jvm.Type, maybeId: Option[IdComputed]): ReturningStrategy =
    ReturningStrategy.SqlReturning(rowType)

  def upsertStrategy(rowType: jvm.Type): UpsertStrategy =
    UpsertStrategy.Returning(rowType)

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
  ): Code =
    returning match {
      case Some(cols) =>
        code"""|INSERT INTO $tableName($columns)
               |VALUES ($values)
               |ON CONFLICT ($idColumns)
               |$conflictUpdate
               |RETURNING $cols""".stripMargin
      case None =>
        code"""|INSERT INTO $tableName($columns)
               |VALUES ($values)
               |ON CONFLICT ($idColumns)
               |$conflictUpdate""".stripMargin
    }

  def conflictUpdateClause(cols: List[ComputedColumn], quotedColName: ComputedColumn => Code): Code =
    code"""|DO UPDATE SET
           |  ${cols.map(c => code"${quotedColName(c)} = EXCLUDED.${quotedColName(c)}").mkCode(",\n")}""".stripMargin

  def conflictNoOpClause(firstPkCol: ComputedColumn, quotedColName: ComputedColumn => Code): Code =
    code"DO UPDATE SET ${quotedColName(firstPkCol)} = EXCLUDED.${quotedColName(firstPkCol)}"

  override def mergeOnClause(idCols: NonEmptyList[ComputedColumn], quotedColName: ComputedColumn => Code): Code =
    idCols.map(quotedColName).mkCode(", ")

  def streamingInsertSql(tableName: Code, columns: Code): Code =
    code"/* SQLite has no COPY; use batched inserts in a transaction */"

  def createTempTableLike(tempName: String, sourceTable: Code): Code =
    code"CREATE TEMPORARY TABLE $tempName AS SELECT * FROM $sourceTable WHERE 0"

  def returningClause(columns: Code): Code =
    code"RETURNING $columns"

  // ═══════════════════════════════════════════════════════════════════════════
  // LAYER 5: Schema DDL
  // ═══════════════════════════════════════════════════════════════════════════

  // SQLite has no schemas (the "main"/"temp"/attached database namespaces are not what other DBs call schemas).
  // Provide no-ops so the generic pipeline doesn't choke.
  def dropSchemaDdl(schemaName: String, cascade: Boolean): String =
    "-- SQLite has no schemas; nothing to drop"

  def createSchemaDdl(schemaName: String): String =
    "-- SQLite has no schemas; nothing to create"
}
