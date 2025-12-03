package typo
package internal
package codegen

import typo.jvm.Code

object PostgresAdapter extends DbAdapter {
  val dbType: DbType = DbType.PostgreSQL

  // ═══════════════════════════════════════════════════════════════════════════
  // LAYER 1: SQL Syntax
  // ═══════════════════════════════════════════════════════════════════════════

  def quoteIdent(name: String): String = s""""$name""""

  def typeCast(value: Code, typeName: String): Code =
    if (typeName.isEmpty) value else code"$value::$typeName"

  def columnReadCast(col: ComputedColumn): Code =
    SqlCast.fromPgCode(col)

  def columnWriteCast(col: ComputedColumn): Code =
    SqlCast.toPgCode(col)

  // ═══════════════════════════════════════════════════════════════════════════
  // LAYER 2: Runtime Type System
  // ═══════════════════════════════════════════════════════════════════════════

  val Types: jvm.Type.Qualified = jvm.Type.Qualified("typo.runtime.PgTypes")
  val TypeClass: jvm.Type.Qualified = jvm.Type.Qualified("typo.runtime.PgType")
  val TextClass: jvm.Type.Qualified = jvm.Type.Qualified("typo.runtime.PgText")
  val typeFieldName: jvm.Ident = jvm.Ident("pgType")
  val textFieldName: jvm.Ident = jvm.Ident("pgText")
  val dialectRef: Code = code"${jvm.Type.dsl.Dialect}.POSTGRESQL"

  def lookupType(tpe: jvm.Type, pkg: jvm.QIdent, lang: Lang): Code =
    jvm.Type.base(tpe) match {
      case TypesJava.BigDecimal                    => code"$Types.numeric"
      case TypesJava.BigInteger                    => code"$Types.numeric"
      case TypesJava.Boolean | TypesKotlin.Boolean => code"$Types.bool"
      case TypesJava.Double | TypesKotlin.Double   => code"$Types.float8"
      case TypesJava.Float | TypesKotlin.Float     => code"$Types.float4"
      case TypesJava.Byte | TypesKotlin.Byte       => code"$Types.tinyint"
      case TypesJava.Short | TypesKotlin.Short     => code"$Types.int2"
      case TypesJava.Integer | TypesKotlin.Int     => code"$Types.int4"
      case TypesJava.Long | TypesKotlin.Long       => code"$Types.int8"
      case TypesJava.String | TypesKotlin.String   => code"$Types.text"
      case TypesJava.UUID                          => code"$Types.uuid"
      case TypesJava.LocalDate                     => code"$Types.date"
      case TypesJava.LocalTime                     => code"$Types.time"
      case TypesJava.LocalDateTime                 => code"$Types.timestamp"
      case TypesJava.runtime.Json                  => code"$Types.json"
      case lang.Optional(targ)                     => code"${lookupType(targ, pkg, lang)}.opt()"
      case lang.ByteArrayType                      => code"$Types.bytea"
      // generated type
      case x: jvm.Type.Qualified if x.value.idents.startsWith(pkg.idents) =>
        code"$tpe.$typeFieldName"
      // generated array type
      case jvm.Type.ArrayOf(targ: jvm.Type.Qualified) =>
        lookupType(targ, pkg, lang).code ++ code"Array"
      case other => sys.error(s"PostgresAdapter.lookupType: Unsupported type: $other")
    }

  def lookupTypeByDbType(dbType: db.Type, Types: jvm.Type.Qualified, naming: Naming): Code =
    dbType match {
      case db.PgType.Boolean       => code"$Types.bool"
      case db.PgType.Bytea         => code"$Types.bytea"
      case db.PgType.Bpchar(_)     => code"$Types.bpchar"
      case db.PgType.Char          => code"$Types.char"
      case db.PgType.Date          => code"$Types.date"
      case db.PgType.Float4        => code"$Types.float4"
      case db.PgType.Float8        => code"$Types.float8"
      case db.PgType.Hstore        => code"$Types.hstore"
      case db.PgType.Inet          => code"$Types.inet"
      case db.PgType.Int2          => code"$Types.int2"
      case db.PgType.Int4          => code"$Types.int4"
      case db.PgType.Int8          => code"$Types.int8"
      case db.PgType.Json          => code"$Types.json"
      case db.PgType.Jsonb         => code"$Types.jsonb"
      case db.PgType.Name          => code"$Types.name"
      case db.PgType.Numeric       => code"$Types.numeric"
      case db.PgType.Oid           => code"$Types.oid"
      case db.PgType.PGInterval    => code"$Types.interval"
      case db.PgType.PGbox         => code"$Types.box"
      case db.PgType.PGcircle      => code"$Types.circle"
      case db.PgType.PGline        => code"$Types.line"
      case db.PgType.PGlsn         => code"$Types.lsn"
      case db.PgType.PGlseg        => code"$Types.lseg"
      case db.PgType.PGmoney       => code"$Types.money"
      case db.PgType.PGpath        => code"$Types.path"
      case db.PgType.PGpoint       => code"$Types.point"
      case db.PgType.PGpolygon     => code"$Types.polygon"
      case db.PgType.aclitem       => code"$Types.aclitem"
      case db.PgType.anyarray      => code"$Types.anyarray"
      case db.PgType.int2vector    => code"$Types.int2vector"
      case db.PgType.oidvector     => code"$Types.oidvector"
      case db.PgType.pg_node_tree  => code"$Types.pg_node_tree"
      case db.PgType.record        => code"$Types.record"
      case db.PgType.regclass      => code"$Types.regclass"
      case db.PgType.regconfig     => code"$Types.regconfig"
      case db.PgType.regdictionary => code"$Types.regdictionary"
      case db.PgType.regnamespace  => code"$Types.regnamespace"
      case db.PgType.regoper       => code"$Types.regoper"
      case db.PgType.regoperator   => code"$Types.regoperator"
      case db.PgType.regproc       => code"$Types.regproc"
      case db.PgType.regprocedure  => code"$Types.regprocedure"
      case db.PgType.regrole       => code"$Types.regrole"
      case db.PgType.regtype       => code"$Types.regtype"
      case db.PgType.xid           => code"$Types.xid"
      case db.PgType.Text          => code"$Types.text"
      case db.PgType.Time          => code"$Types.time"
      case db.PgType.TimeTz        => code"$Types.timetz"
      case db.PgType.Timestamp     => code"$Types.timestamp"
      case db.PgType.TimestampTz   => code"$Types.timestamptz"
      case db.PgType.UUID          => code"$Types.uuid"
      case db.PgType.Xml           => code"$Types.xml"
      case db.PgType.VarChar(_)    => code"$Types.text"
      case db.PgType.Vector        => code"$Types.vector"

      case db.PgType.DomainRef(name, _, _) =>
        code"${jvm.Type.Qualified(naming.domainName(name))}.$typeFieldName"
      case db.PgType.EnumRef(enm) =>
        code"${jvm.Type.Qualified(naming.enumName(enm.name))}.$typeFieldName"
      case db.PgType.Array(inner) =>
        code"${lookupTypeByDbType(inner, Types, naming)}Array"
      case db.Unknown(sqlType) =>
        sys.error(s"PostgresAdapter.lookupTypeByDbType: Cannot lookup for unknown type: $sqlType")
      case _: db.MariaType =>
        sys.error(s"PostgresAdapter.lookupTypeByDbType: Cannot lookup MariaDB type in PostgreSQL adapter")
    }

  // ═══════════════════════════════════════════════════════════════════════════
  // LAYER 3: Capabilities
  // ═══════════════════════════════════════════════════════════════════════════

  val supportsArrays: Boolean = true
  val supportsReturning: Boolean = true
  val supportsCopyStreaming: Boolean = true
  val supportsDefaultInCopy: Boolean = true

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
        code"""|insert into $tableName($columns)
               |values ($values)
               |on conflict ($idColumns)
               |$conflictUpdate
               |returning $cols""".stripMargin
      case None =>
        code"""|insert into $tableName($columns)
               |values ($values)
               |on conflict ($idColumns)
               |$conflictUpdate""".stripMargin
    }

  def conflictUpdateClause(cols: List[ComputedColumn], quotedColName: ComputedColumn => Code): Code =
    code"""|do update set
           |  ${cols.map(c => code"${quotedColName(c)} = EXCLUDED.${quotedColName(c)}").mkCode(",\n")}""".stripMargin

  def conflictNoOpClause(firstPkCol: ComputedColumn, quotedColName: ComputedColumn => Code): Code =
    code"do update set ${quotedColName(firstPkCol)} = EXCLUDED.${quotedColName(firstPkCol)}"

  def streamingInsertSql(tableName: Code, columns: Code): Code =
    code"COPY $tableName($columns) FROM STDIN"

  def createTempTableLike(tempName: String, sourceTable: Code): Code =
    code"create temporary table $tempName (like $sourceTable) on commit drop"
}
