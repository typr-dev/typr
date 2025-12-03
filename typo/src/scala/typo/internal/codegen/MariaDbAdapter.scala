package typo
package internal
package codegen

import typo.jvm.Code

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

  // ═══════════════════════════════════════════════════════════════════════════
  // LAYER 2: Runtime Type System
  // ═══════════════════════════════════════════════════════════════════════════

  val Types: jvm.Type.Qualified = jvm.Type.Qualified("typo.runtime.MariaTypes")
  val TypeClass: jvm.Type.Qualified = jvm.Type.Qualified("typo.runtime.MariaType")
  val TextClass: jvm.Type.Qualified = jvm.Type.Qualified("typo.runtime.MariaText")
  val typeFieldName: jvm.Ident = jvm.Ident("pgType") // Keep same name for compatibility
  val textFieldName: jvm.Ident = jvm.Ident("mariaText")
  val dialectRef: Code = code"${jvm.Type.dsl.Dialect}.MARIADB"

  def lookupType(tpe: jvm.Type, pkg: jvm.QIdent, lang: Lang): Code =
    jvm.Type.base(tpe) match {
      case TypesJava.BigDecimal                    => code"$Types.numeric"
      case TypesJava.BigInteger                    => code"$Types.bigintUnsigned"
      case TypesJava.Boolean | TypesKotlin.Boolean => code"$Types.bool"
      case TypesJava.Double | TypesKotlin.Double   => code"$Types.double_"
      case TypesJava.Float | TypesKotlin.Float     => code"$Types.float_"
      case TypesJava.Byte | TypesKotlin.Byte       => code"$Types.tinyint"
      case TypesJava.Short | TypesKotlin.Short     => code"$Types.smallint"
      case TypesJava.Integer | TypesKotlin.Int     => code"$Types.int_"
      case TypesJava.Long | TypesKotlin.Long       => code"$Types.bigint"
      case TypesJava.String | TypesKotlin.String   => code"$Types.text"
      case TypesJava.UUID                          => code"$Types.uuid"
      case TypesJava.LocalDate                     => code"$Types.date"
      case TypesJava.LocalTime                     => code"$Types.time"
      case TypesJava.LocalDateTime                 => code"$Types.datetime"
      case TypesJava.Year                          => code"$Types.year"
      case TypesJava.maria.MariaSet                => code"$Types.set"
      case TypesJava.runtime.Json                  => code"$Types.json"
      case TypesJava.maria.Inet4                   => code"$Types.inet4"
      case TypesJava.maria.Inet6                   => code"$Types.inet6"
      case TypesJava.maria.Geometry                => code"$Types.geometry"
      case TypesJava.maria.Point                   => code"$Types.point"
      case TypesJava.maria.LineString              => code"$Types.linestring"
      case TypesJava.maria.Polygon                 => code"$Types.polygon"
      case TypesJava.maria.MultiPoint              => code"$Types.multipoint"
      case TypesJava.maria.MultiLineString         => code"$Types.multilinestring"
      case TypesJava.maria.MultiPolygon            => code"$Types.multipolygon"
      case TypesJava.maria.GeometryCollection      => code"$Types.geometrycollection"
      case lang.Optional(targ)                     => code"${lookupType(targ, pkg, lang)}.opt()"
      case lang.ByteArrayType                      => code"$Types.blob"
      // generated type
      case x: jvm.Type.Qualified if x.value.idents.startsWith(pkg.idents) =>
        code"$tpe.$typeFieldName"
      // generated array type - MariaDB doesn't support arrays but keep for compatibility
      case jvm.Type.ArrayOf(targ: jvm.Type.Qualified) =>
        lookupType(targ, pkg, lang).code ++ code"Array"
      case other => sys.error(s"MariaDbAdapter.lookupType: Unsupported type: $other")
    }

  def lookupTypeByDbType(dbType: db.Type, Types: jvm.Type.Qualified, naming: Naming): Code =
    dbType match {
      // MariaDB types
      case db.MariaType.TinyInt            => code"$Types.tinyint"
      case db.MariaType.SmallInt           => code"$Types.smallint"
      case db.MariaType.MediumInt          => code"$Types.mediumint"
      case db.MariaType.Int                => code"$Types.int_"
      case db.MariaType.BigInt             => code"$Types.bigint"
      case db.MariaType.TinyIntUnsigned    => code"$Types.tinyintUnsigned"
      case db.MariaType.SmallIntUnsigned   => code"$Types.smallintUnsigned"
      case db.MariaType.MediumIntUnsigned  => code"$Types.mediumintUnsigned"
      case db.MariaType.IntUnsigned        => code"$Types.intUnsigned"
      case db.MariaType.BigIntUnsigned     => code"$Types.bigintUnsigned"
      case db.MariaType.Decimal(_, _)      => code"$Types.decimal"
      case db.MariaType.Float              => code"$Types.float_"
      case db.MariaType.Double             => code"$Types.double_"
      case db.MariaType.Boolean            => code"$Types.bool"
      case db.MariaType.Bit(Some(1))       => code"$Types.bit1"
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

      case db.Unknown(sqlType) =>
        sys.error(s"MariaDbAdapter.lookupTypeByDbType: Cannot lookup for unknown type: $sqlType")
      case _: db.PgType =>
        sys.error(s"MariaDbAdapter.lookupTypeByDbType: Cannot lookup PostgreSQL type in MariaDB adapter")
    }

  // ═══════════════════════════════════════════════════════════════════════════
  // LAYER 3: Capabilities
  // ═══════════════════════════════════════════════════════════════════════════

  val supportsArrays: Boolean = false
  val supportsReturning: Boolean = true // MariaDB 10.5+
  val supportsCopyStreaming: Boolean = false
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
}
