package typr
package internal
package codegen

import typr.jvm.Code

object MariaDbAdapter extends DbAdapter {
  val dbType: DbType = DbType.MariaDB

  // ═══════════════════════════════════════════════════════════════════════════
  // LAYER 1: SQL Syntax
  // ═══════════════════════════════════════════════════════════════════════════

  def quoteIdent(name: String): String = s"`$name`"

  def typeCast(value: Code, typeName: String): Code =
    if (typeName.isEmpty) value else code"CAST($value AS $typeName)"

  // MariaDB doesn't need PostgreSQL-style casts
  def columnReadCast(col: ComputedColumn): Code = Code.Empty
  def columnWriteCast(col: ComputedColumn): Code = Code.Empty
  def writeCastTypeName(col: ComputedColumn): Option[String] = None
  def writeCast(dbType: db.Type, udtName: Option[String]): Option[SqlCastValue] = None
  def readCast(dbType: db.Type): Option[SqlCastValue] = None

  // ═══════════════════════════════════════════════════════════════════════════
  // LAYER 2: Runtime Type System
  // ═══════════════════════════════════════════════════════════════════════════

  override val KotlinTypes: jvm.Type.Qualified = jvm.Type.Qualified("dev.typr.foundationskt.MariaTypes")
  override val ScalaTypes: jvm.Type.Qualified = jvm.Type.Qualified("dev.typr.foundationssc.MariaTypes")
  val Types: jvm.Type.Qualified = jvm.Type.Qualified("dev.typr.foundations.MariaTypes")
  val TypeClass: jvm.Type.Qualified = jvm.Type.Qualified("dev.typr.foundations.MariaType")
  val TextClass: jvm.Type.Qualified = jvm.Type.Qualified("dev.typr.foundations.MariaText")
  val typeFieldName: jvm.Ident = jvm.Ident("mariaType")
  val textFieldName: jvm.Ident = jvm.Ident("mariaText")
  def dialectRef(lang: Lang): Code = code"${lang.dsl.Dialect}.MARIADB"

  def lookupType(typoType: TypoType, naming: Naming, typeSupport: TypeSupport): Code =
    typoType match {
      case TypoType.Standard(_, dbType) =>
        lookupByDbType(dbType, typeSupport)

      case TypoType.Nullable(_, inner) =>
        nullableType(lookupType(inner, naming, typeSupport))

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
            // Qualified user types must provide their own dbType field
            code"$qualifiedType.$typeFieldName"
          case Right(primitive) =>
            // Well-known primitives use the adapter's lookup
            lookupPrimitive(primitive, typeSupport)
        }

      case TypoType.Array(_, _) =>
        sys.error("MariaDbAdapter.lookupType: MariaDB does not support array types")

      case TypoType.Aligned(_, sourceType, _, _) =>
        lookupType(sourceType, naming, typeSupport)
    }

  def lookupPrimitive(primitive: analysis.WellKnownPrimitive, typeSupport: TypeSupport): Code = {
    // Helper to get primitive type code based on language
    def primitiveType(name: String): Code = {
      val nameIdent = jvm.Ident(name)
      typeSupport match {
        case TypeSupportScala    => code"$ScalaTypes.$nameIdent"
        case TypeSupportKotlin   => code"$KotlinTypes.$nameIdent"
        case TypeSupportJava | _ => code"$Types.$nameIdent"
      }
    }

    primitive match {
      case analysis.WellKnownPrimitive.String        => primitiveType("varchar")
      case analysis.WellKnownPrimitive.Boolean       => primitiveType("bool")
      case analysis.WellKnownPrimitive.Byte          => primitiveType("tinyint")
      case analysis.WellKnownPrimitive.Short         => primitiveType("smallint")
      case analysis.WellKnownPrimitive.Int           => primitiveType("int_")
      case analysis.WellKnownPrimitive.Long          => primitiveType("bigint")
      case analysis.WellKnownPrimitive.Float         => primitiveType("float_")
      case analysis.WellKnownPrimitive.Double        => primitiveType("double_")
      case analysis.WellKnownPrimitive.BigDecimal    => primitiveType("numeric")
      case analysis.WellKnownPrimitive.LocalDate     => primitiveType("date")
      case analysis.WellKnownPrimitive.LocalTime     => primitiveType("time")
      case analysis.WellKnownPrimitive.LocalDateTime => primitiveType("datetime")
      case analysis.WellKnownPrimitive.Instant       => primitiveType("timestamp")
      case analysis.WellKnownPrimitive.UUID          => primitiveType("char_") // UUID as CHAR(36) in MariaDB
    }
  }

  def textType: db.Type = db.MariaType.Text

  private def lookupByDbType(dbType: db.Type, typeSupport: TypeSupport): Code = {
    // Helper to get primitive type code based on language
    def primitiveType(name: String): Code = {
      val nameIdent = jvm.Ident(name)
      typeSupport match {
        case TypeSupportScala    => code"$ScalaTypes.$nameIdent"
        case TypeSupportKotlin   => code"$KotlinTypes.$nameIdent"
        case TypeSupportJava | _ => code"$Types.$nameIdent"
      }
    }

    dbType match {
      case dbType: db.MariaType =>
        dbType match {
          // Primitive types with language-specific overrides
          case db.MariaType.TinyInt           => primitiveType("tinyint")
          case db.MariaType.SmallInt          => primitiveType("smallint")
          case db.MariaType.MediumInt         => primitiveType("mediumint")
          case db.MariaType.Int               => primitiveType("int_")
          case db.MariaType.BigInt            => primitiveType("bigint")
          case db.MariaType.TinyIntUnsigned   => primitiveType("tinyintUnsigned") // Uint1 (0-255)
          case db.MariaType.SmallIntUnsigned  => primitiveType("smallintUnsigned") // Uint2 (0-65535)
          case db.MariaType.MediumIntUnsigned => primitiveType("mediumintUnsigned") // Uint4 (0-16777215)
          case db.MariaType.IntUnsigned       => primitiveType("intUnsigned") // Uint4 (0-4294967295)
          case db.MariaType.Float             => primitiveType("float_")
          case db.MariaType.Double            => primitiveType("double_")
          case db.MariaType.Decimal(_, _)     => primitiveType("numeric")
          case db.MariaType.Boolean           => primitiveType("bool")
          case db.MariaType.Bit(Some(1))      => primitiveType("bit1")

          case db.MariaType.BigIntUnsigned     => primitiveType("bigintUnsigned")
          case db.MariaType.Bit(_)             => primitiveType("bit")
          case db.MariaType.Char(_)            => primitiveType("char_")
          case db.MariaType.VarChar(_)         => primitiveType("varchar")
          case db.MariaType.TinyText           => primitiveType("tinytext")
          case db.MariaType.Text               => primitiveType("text")
          case db.MariaType.MediumText         => primitiveType("mediumtext")
          case db.MariaType.LongText           => primitiveType("longtext")
          case db.MariaType.Binary(_)          => primitiveType("binary")
          case db.MariaType.VarBinary(_)       => primitiveType("varbinary")
          case db.MariaType.TinyBlob           => primitiveType("tinyblob")
          case db.MariaType.Blob               => primitiveType("blob")
          case db.MariaType.MediumBlob         => primitiveType("mediumblob")
          case db.MariaType.LongBlob           => primitiveType("longblob")
          case db.MariaType.Date               => primitiveType("date")
          case db.MariaType.Time(_)            => primitiveType("time")
          case db.MariaType.DateTime(_)        => primitiveType("datetime")
          case db.MariaType.Timestamp(_)       => primitiveType("timestamp")
          case db.MariaType.Year               => primitiveType("year")
          case db.MariaType.Enum(_)            => primitiveType("text") // MariaDB inline ENUMs stored as strings
          case db.MariaType.Set(_)             => primitiveType("set")
          case db.MariaType.Json               => primitiveType("json")
          case db.MariaType.Inet4              => primitiveType("inet4")
          case db.MariaType.Inet6              => primitiveType("inet6")
          case db.MariaType.Geometry           => primitiveType("geometry")
          case db.MariaType.Point              => primitiveType("point")
          case db.MariaType.LineString         => primitiveType("linestring")
          case db.MariaType.Polygon            => primitiveType("polygon")
          case db.MariaType.MultiPoint         => primitiveType("multipoint")
          case db.MariaType.MultiLineString    => primitiveType("multilinestring")
          case db.MariaType.MultiPolygon       => primitiveType("multipolygon")
          case db.MariaType.GeometryCollection => primitiveType("geometrycollection")
          case db.Unknown(_)                   => primitiveType("unknown")
        }
      case _ =>
        sys.error(s"MariaDbAdapter.lookupByDbType: Cannot lookup db type from another database")
    }
  }

  /** Public interface for looking up types by db.Type - delegates to lookupByDbType */
  def lookupTypeByDbType(dbType: db.Type, Types: jvm.Type.Qualified, naming: Naming, typeSupport: TypeSupport): Code =
    lookupByDbType(dbType, typeSupport)

  // ═══════════════════════════════════════════════════════════════════════════
  // LAYER 3: Capabilities
  // ═══════════════════════════════════════════════════════════════════════════

  val supportsArrays: Boolean = false
  val supportsReturning: Boolean = true // MariaDB 10.5+
  val supportsCopyStreaming: Boolean = false
  val supportsDefaultInCopy: Boolean = false

  /** MariaDB uses SQL RETURNING clause for all inserts */
  def returningStrategy(cols: NonEmptyList[ComputedColumn], rowType: jvm.Type, maybeId: Option[IdComputed]): ReturningStrategy =
    ReturningStrategy.SqlReturning(rowType)

  /** MariaDB supports RETURNING with ON DUPLICATE KEY UPDATE */
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
               |ON DUPLICATE KEY UPDATE $conflictUpdate
               |RETURNING $cols""".stripMargin
      case None =>
        code"""|INSERT INTO $tableName($columns)
               |VALUES ($values)
               |ON DUPLICATE KEY UPDATE $conflictUpdate""".stripMargin
    }

  def conflictUpdateClause(cols: List[ComputedColumn], quotedColName: ComputedColumn => Code): Code =
    cols.map(c => code"${quotedColName(c)} = VALUES(${quotedColName(c)})").mkCode(",\n")

  def conflictNoOpClause(firstPkCol: ComputedColumn, quotedColName: ComputedColumn => Code): Code =
    code"${quotedColName(firstPkCol)} = VALUES(${quotedColName(firstPkCol)})"

  def streamingInsertSql(tableName: Code, columns: Code): Code =
    // MariaDB doesn't support COPY, use batch inserts instead
    code"/* LOAD DATA not directly supported, use batch inserts */"

  def createTempTableLike(tempName: String, sourceTable: Code): Code =
    code"CREATE TEMPORARY TABLE $tempName LIKE $sourceTable"

  def returningClause(columns: Code): Code =
    code"RETURNING $columns"

  /** MariaDB doesn't support DEFAULT VALUES syntax. Use () VALUES () instead */
  override def insertDefaultValuesReturning(
      tableName: Code,
      returningCols: NonEmptyList[ComputedColumn]
  ): Code = {
    val returning = returningClause(returningColumns(returningCols))
    code"""|insert into $tableName() values ()
           |$returning
           |""".stripMargin
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // LAYER 5: Schema DDL
  // ═══════════════════════════════════════════════════════════════════════════

  def dropSchemaDdl(schemaName: String, cascade: Boolean): String =
    s"DROP DATABASE IF EXISTS $schemaName"

  def createSchemaDdl(schemaName: String): String =
    s"CREATE DATABASE IF NOT EXISTS $schemaName"
}
