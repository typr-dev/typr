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

  val KotlinDbTypes: jvm.Type.Qualified = jvm.Type.Qualified("typr.kotlindsl.KotlinDbTypes")
  val KotlinNullableExtension: jvm.Type.Qualified = jvm.Type.Qualified("typr.kotlindsl.nullable")
  val ScalaDbTypes: jvm.Type.Qualified = jvm.Type.Qualified("typr.scaladsl.ScalaDbTypes")
  val ScalaDbTypeOps: jvm.Type.Qualified = jvm.Type.Qualified("typr.scaladsl.MariaTypeOps")
  val Types: jvm.Type.Qualified = jvm.Type.Qualified("typr.runtime.MariaTypes")
  val TypeClass: jvm.Type.Qualified = jvm.Type.Qualified("typr.runtime.MariaType")
  val TextClass: jvm.Type.Qualified = jvm.Type.Qualified("typr.runtime.MariaText")
  val typeFieldName: jvm.Ident = jvm.Ident("pgType") // Keep same name for compatibility
  val textFieldName: jvm.Ident = jvm.Ident("mariaText")
  def dialectRef(lang: Lang): Code = code"${lang.dsl.Dialect}.MARIADB"

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
            // Qualified user types must provide their own pgType/mariaType field
            code"$qualifiedType.$typeFieldName"
          case Right(primitive) =>
            // Well-known primitives use the adapter's lookup
            lookupPrimitive(primitive, typeSupport)
        }

      case TypoType.Array(_, _) =>
        sys.error("MariaDbAdapter.lookupType: MariaDB does not support array types")
    }

  def lookupPrimitive(primitive: analysis.WellKnownPrimitive, typeSupport: TypeSupport): Code = {
    // Helper to get primitive type code based on language
    def primitiveType(name: String): Code = {
      val nameIdent = jvm.Ident(name)
      typeSupport match {
        case TypeSupportScala    => code"$ScalaDbTypes.MariaTypes.$nameIdent"
        case TypeSupportKotlin   => code"$KotlinDbTypes.MariaTypes.$nameIdent"
        case TypeSupportJava | _ => code"$Types.$nameIdent"
      }
    }

    primitive match {
      case analysis.WellKnownPrimitive.String        => code"$Types.varchar"
      case analysis.WellKnownPrimitive.Boolean       => primitiveType("bool")
      case analysis.WellKnownPrimitive.Byte          => primitiveType("tinyint")
      case analysis.WellKnownPrimitive.Short         => primitiveType("smallint")
      case analysis.WellKnownPrimitive.Int           => primitiveType("int_")
      case analysis.WellKnownPrimitive.Long          => primitiveType("bigint")
      case analysis.WellKnownPrimitive.Float         => primitiveType("float_")
      case analysis.WellKnownPrimitive.Double        => primitiveType("double_")
      case analysis.WellKnownPrimitive.BigDecimal    => primitiveType("numeric")
      case analysis.WellKnownPrimitive.LocalDate     => code"$Types.date"
      case analysis.WellKnownPrimitive.LocalTime     => code"$Types.time"
      case analysis.WellKnownPrimitive.LocalDateTime => code"$Types.datetime"
      case analysis.WellKnownPrimitive.Instant       => code"$Types.timestamp"
      case analysis.WellKnownPrimitive.UUID          => code"$Types.char_" // UUID as CHAR(36) in MariaDB
    }
  }

  def textType: db.Type = db.MariaType.Text

  private def lookupByDbType(dbType: db.Type, typeSupport: TypeSupport): Code = {
    // Helper to get primitive type code based on language
    def primitiveType(name: String): Code = {
      val nameIdent = jvm.Ident(name)
      typeSupport match {
        case TypeSupportScala    => code"$ScalaDbTypes.MariaTypes.$nameIdent"
        case TypeSupportKotlin   => code"$KotlinDbTypes.MariaTypes.$nameIdent"
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
          case db.MariaType.TinyIntUnsigned   => primitiveType("tinyintUnsigned")
          case db.MariaType.SmallIntUnsigned  => primitiveType("smallintUnsigned")
          case db.MariaType.MediumIntUnsigned => primitiveType("mediumintUnsigned")
          case db.MariaType.IntUnsigned       => primitiveType("intUnsigned")
          case db.MariaType.Float             => primitiveType("float_")
          case db.MariaType.Double            => primitiveType("double_")
          case db.MariaType.Decimal(_, _)     => primitiveType("numeric")
          case db.MariaType.Boolean           => primitiveType("bool")
          case db.MariaType.Bit(Some(1))      => primitiveType("bit1")

          // Non-primitive types use base Types
          case db.MariaType.BigIntUnsigned     => code"$Types.bigintUnsigned"
          case db.MariaType.Bit(_)             => code"$Types.bit"
          case db.MariaType.Char(_)            => code"$Types.char_"
          case db.MariaType.VarChar(_)         => code"$Types.varchar"
          case db.MariaType.TinyText           => code"$Types.tinytext"
          case db.MariaType.Text               => code"$Types.text"
          case db.MariaType.MediumText         => code"$Types.mediumtext"
          case db.MariaType.LongText           => code"$Types.longtext"
          case db.MariaType.Binary(_)          => code"$Types.binary"
          case db.MariaType.VarBinary(_)       => code"$Types.varbinary"
          case db.MariaType.TinyBlob           => code"$Types.tinyblob"
          case db.MariaType.Blob               => code"$Types.blob"
          case db.MariaType.MediumBlob         => code"$Types.mediumblob"
          case db.MariaType.LongBlob           => code"$Types.longblob"
          case db.MariaType.Date               => code"$Types.date"
          case db.MariaType.Time(_)            => code"$Types.time"
          case db.MariaType.DateTime(_)        => code"$Types.datetime"
          case db.MariaType.Timestamp(_)       => code"$Types.timestamp"
          case db.MariaType.Year               => code"$Types.year"
          case db.MariaType.Enum(_)            => code"$Types.text" // MariaDB inline ENUMs stored as strings
          case db.MariaType.Set(_)             => code"$Types.set"
          case db.MariaType.Json               => code"$Types.json"
          case db.MariaType.Inet4              => code"$Types.inet4"
          case db.MariaType.Inet6              => code"$Types.inet6"
          case db.MariaType.Geometry           => code"$Types.geometry"
          case db.MariaType.Point              => code"$Types.point"
          case db.MariaType.LineString         => code"$Types.linestring"
          case db.MariaType.Polygon            => code"$Types.polygon"
          case db.MariaType.MultiPoint         => code"$Types.multipoint"
          case db.MariaType.MultiLineString    => code"$Types.multilinestring"
          case db.MariaType.MultiPolygon       => code"$Types.multipolygon"
          case db.MariaType.GeometryCollection => code"$Types.geometrycollection"
          case db.Unknown(_)                   => code"$Types.unknown"
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
}
