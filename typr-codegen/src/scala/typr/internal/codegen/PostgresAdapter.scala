package typr
package internal
package codegen

import typr.jvm.Code

class PostgresAdapter(needsTimestampCasts: Boolean) extends DbAdapter {
  val dbType: DbType = DbType.PostgreSQL

  // ═══════════════════════════════════════════════════════════════════════════
  // LAYER 1: SQL Syntax
  // ═══════════════════════════════════════════════════════════════════════════

  def quoteIdent(name: String): String = s""""$name""""

  def typeCast(value: Code, typeName: String): Code =
    if (typeName.isEmpty) value else code"$value::$typeName"

  // Cast logic for reading from PostgreSQL
  def columnReadCast(col: ComputedColumn): Code =
    readCast(col.dbCol.tpe).fold(jvm.Code.Empty)(_.asCode)

  // Cast logic for writing to PostgreSQL
  def columnWriteCast(col: ComputedColumn): Code =
    writeCast(col.dbCol.tpe, col.dbCol.udtName).fold(jvm.Code.Empty)(_.asCode)

  // Get the type name for write cast (used for array unnest casts)
  def writeCastTypeName(col: ComputedColumn): Option[String] =
    writeCast(col.dbCol.tpe, col.dbCol.udtName).map(_.typeName)

  // ═══════════════════════════════════════════════════════════════════════════
  // SQL Cast Logic
  // ═══════════════════════════════════════════════════════════════════════════

  /** Cast to correctly insert into PG */
  def writeCast(dbType: db.Type, udtName: Option[String]): Option[SqlCastValue] =
    dbType match {
      case x: db.PgType =>
        x match {
          case db.Unknown(sqlType)                                       => Some(SqlCastValue(sqlType))
          case db.PgType.EnumRef(enm)                                    => Some(SqlCastValue(enm.name.value))
          case db.PgType.Boolean | db.PgType.Text | db.PgType.VarChar(_) => None
          case _: db.PgType =>
            udtName.map {
              case ArrayName(x) => SqlCastValue(x + "[]")
              case other        => SqlCastValue(other)
            }
        }
      case _ => None
    }

  /** Avoid whatever the postgres driver does for these data formats by going through basic data types */
  def readCast(dbType: db.Type): Option[SqlCastValue] =
    dbType match {
      case x: db.PgType =>
        x match {
          case db.Unknown(_) =>
            Some(SqlCastValue("text"))
          case db.PgType.Array(db.Unknown(_)) | db.PgType.Array(db.PgType.DomainRef(_, _, db.Unknown(_))) =>
            Some(SqlCastValue("text[]"))
          case db.PgType.DomainRef(_, _, underlying) =>
            readCast(underlying)
          case db.PgType.PGmoney =>
            Some(SqlCastValue("numeric"))
          case db.PgType.Vector =>
            if (needsTimestampCasts) Some(SqlCastValue("float4[]")) else None
          case db.PgType.Array(db.PgType.PGmoney) =>
            Some(SqlCastValue("numeric[]"))
          case db.PgType.Array(db.PgType.DomainRef(_, underlying, _)) =>
            Some(SqlCastValue(underlying + "[]"))
          case db.PgType.TimestampTz | db.PgType.Timestamp | db.PgType.TimeTz | db.PgType.Time | db.PgType.Date =>
            if (needsTimestampCasts) Some(SqlCastValue("text")) else None
          case db.PgType.Array(db.PgType.TimestampTz | db.PgType.Timestamp | db.PgType.TimeTz | db.PgType.Time | db.PgType.Date) =>
            if (needsTimestampCasts) Some(SqlCastValue("text[]")) else None
          case _ => None
        }
      case _ => None
    }

  // ═══════════════════════════════════════════════════════════════════════════
  // LAYER 2: Runtime Type System
  // ═══════════════════════════════════════════════════════════════════════════

  override val KotlinTypes = jvm.Type.Qualified("dev.typr.foundationskt.PgTypes")
  override val ScalaTypes = jvm.Type.Qualified("dev.typr.foundationssc.PgTypes")
  val Types: jvm.Type.Qualified = jvm.Type.Qualified("dev.typr.foundations.PgTypes")
  val TypeClass: jvm.Type.Qualified = jvm.Type.Qualified("dev.typr.foundations.PgType")
  val TextClass: jvm.Type.Qualified = jvm.Type.Qualified("dev.typr.foundations.PgText")
  val typeFieldName: jvm.Ident = jvm.Ident("pgType")
  val textFieldName: jvm.Ident = jvm.Ident("pgText")

  def dialectRef(lang: Lang): Code = code"${lang.dsl.Dialect}.POSTGRESQL"

  def lookupType(typoType: TypoType, naming: Naming, typeSupport: TypeSupport): Code = {
    def primitiveType(name: String): Code = {
      val nameIdent = jvm.Ident(name)
      typeSupport match {
        case TypeSupportScala    => code"$ScalaTypes.$nameIdent"
        case TypeSupportKotlin   => code"$KotlinTypes.$nameIdent"
        case TypeSupportJava | _ => code"$Types.$nameIdent"
      }
    }

    typoType match {
      case TypoType.Standard(_, dbType) =>
        lookupByDbType(dbType, naming, typeSupport)

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
            code"$qualifiedType.$typeFieldName"
          case Right(primitive) =>
            lookupPrimitive(primitive, typeSupport)
        }

      case TypoType.Array(_, element) =>
        // RC5: pre-defined `xxxArray` PgTypes were removed. Use `.array` (Scala wrapper) / `.array()` (Java/Kotlin)
        // on the element PgType, which yields PgType[List[T]] (java.util.List in Java/Kotlin, immutable.List in Scala).
        val elementCode = lookupType(element, naming, typeSupport)
        typeSupport match {
          case TypeSupportScala => code"$elementCode.array"
          case _                => code"$elementCode.array()"
        }

      case TypoType.Aligned(_, sourceType, _, _) =>
        lookupType(sourceType, naming, typeSupport)
    }
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
      case analysis.WellKnownPrimitive.String        => primitiveType("text")
      case analysis.WellKnownPrimitive.Boolean       => primitiveType("bool")
      case analysis.WellKnownPrimitive.Byte          => primitiveType("int2")
      case analysis.WellKnownPrimitive.Short         => primitiveType("int2")
      case analysis.WellKnownPrimitive.Int           => primitiveType("int4")
      case analysis.WellKnownPrimitive.Long          => primitiveType("int8")
      case analysis.WellKnownPrimitive.Float         => primitiveType("float4")
      case analysis.WellKnownPrimitive.Double        => primitiveType("float8")
      case analysis.WellKnownPrimitive.BigDecimal    => primitiveType("numeric")
      case analysis.WellKnownPrimitive.LocalDate     => primitiveType("date")
      case analysis.WellKnownPrimitive.LocalTime     => primitiveType("time")
      case analysis.WellKnownPrimitive.LocalDateTime => primitiveType("timestamp")
      case analysis.WellKnownPrimitive.Instant       => primitiveType("timestamptz")
      case analysis.WellKnownPrimitive.UUID          => primitiveType("uuid")
    }
  }

  def textType: db.Type = db.PgType.Text

  /** Public interface for looking up types by db.Type - delegates to lookupByDbType */
  def lookupTypeByDbType(dbType: db.Type, Types: jvm.Type.Qualified, naming: Naming, typeSupport: TypeSupport): Code =
    lookupByDbType(dbType, naming, typeSupport)

  private def lookupByDbType(dbType: db.Type, naming: Naming, typeSupport: TypeSupport): Code = {
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
      // Primitive types with language-specific overrides
      case db.PgType.Boolean => primitiveType("bool")
      case db.PgType.Int2    => primitiveType("int2")
      case db.PgType.Int4    => primitiveType("int4")
      case db.PgType.Int8    => primitiveType("int8")
      case db.PgType.Float4  => primitiveType("float4")
      case db.PgType.Float8  => primitiveType("float8")
      case db.PgType.Numeric => primitiveType("numeric")
      case db.PgType.Hstore  => primitiveType("hstore")

      case db.PgType.Bytea         => primitiveType("bytea")
      case db.PgType.Bpchar(_)     => primitiveType("bpchar")
      case db.PgType.Char          => primitiveType("char")
      case db.PgType.Date          => primitiveType("date")
      case db.PgType.Inet          => primitiveType("inet")
      case db.PgType.Cidr          => primitiveType("cidr")
      case db.PgType.MacAddr       => primitiveType("macaddr")
      case db.PgType.MacAddr8      => primitiveType("macaddr8")
      case db.PgType.Json          => primitiveType("json")
      case db.PgType.Jsonb         => primitiveType("jsonb")
      case db.PgType.Name          => primitiveType("name")
      case db.PgType.Oid           => primitiveType("oid")
      case db.PgType.PGInterval    => primitiveType("interval")
      case db.PgType.PGbox         => primitiveType("box")
      case db.PgType.PGcircle      => primitiveType("circle")
      case db.PgType.PGline        => primitiveType("line")
      case db.PgType.PGlsn         => primitiveType("lsn")
      case db.PgType.PGlseg        => primitiveType("lseg")
      case db.PgType.PGmoney       => primitiveType("money")
      case db.PgType.PGpath        => primitiveType("path")
      case db.PgType.PGpoint       => primitiveType("point")
      case db.PgType.PGpolygon     => primitiveType("polygon")
      case db.PgType.aclitem       => primitiveType("aclitem")
      case db.PgType.anyarray      => primitiveType("anyarray")
      case db.PgType.int2vector    => primitiveType("int2vector")
      case db.PgType.oidvector     => primitiveType("oidvector")
      case db.PgType.pg_node_tree  => primitiveType("pg_node_tree")
      case db.PgType.record        => primitiveType("record")
      case db.PgType.regclass      => primitiveType("regclass")
      case db.PgType.regconfig     => primitiveType("regconfig")
      case db.PgType.regdictionary => primitiveType("regdictionary")
      case db.PgType.regnamespace  => primitiveType("regnamespace")
      case db.PgType.regoper       => primitiveType("regoper")
      case db.PgType.regoperator   => primitiveType("regoperator")
      case db.PgType.regproc       => primitiveType("regproc")
      case db.PgType.regprocedure  => primitiveType("regprocedure")
      case db.PgType.regrole       => primitiveType("regrole")
      case db.PgType.regtype       => primitiveType("regtype")
      case db.PgType.xid           => primitiveType("xid")
      case db.PgType.Text          => primitiveType("text")
      case db.PgType.Time          => primitiveType("time")
      case db.PgType.TimeTz        => primitiveType("timetz")
      case db.PgType.Timestamp     => primitiveType("timestamp")
      case db.PgType.TimestampTz   => primitiveType("timestamptz")
      case db.PgType.UUID          => primitiveType("uuid")
      case db.PgType.Xml           => primitiveType("xml")
      case db.PgType.VarChar(_)    => primitiveType("text")
      case db.PgType.Vector        => primitiveType("vector")
      case db.PgType.Bit(_)        => primitiveType("bit")
      case db.PgType.Varbit(_)     => primitiveType("varbit")
      case db.Unknown(_)           => primitiveType("unknown")

      case db.PgType.DomainRef(name, _, _) =>
        code"${jvm.Type.Qualified(naming.domainName(name))}.$typeFieldName"
      case db.PgType.EnumRef(enm) =>
        code"${jvm.Type.Qualified(naming.enumName(enm.name))}.$typeFieldName"
      case db.PgType.CompositeType(name, _) =>
        code"${jvm.Type.Qualified(naming.compositeTypeName(name))}.$typeFieldName"
      case db.PgType.Array(inner) =>
        // RC5: pre-defined `xxxArray` PgTypes were removed. Use `.array` (Scala wrapper) / `.array()` (Java/Kotlin)
        // on the element PgType, which yields PgType[List[T]].
        val innerCode = lookupByDbType(inner, naming, typeSupport)
        typeSupport match {
          case TypeSupportScala => code"$innerCode.array"
          case _                => code"$innerCode.array()"
        }

      case _ =>
        sys.error(s"PostgresAdapter.lookupByDbType: Cannot lookup db type from another database")
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // LAYER 3: Capabilities
  // ═══════════════════════════════════════════════════════════════════════════

  val supportsArrays: Boolean = true
  val supportsReturning: Boolean = true
  val supportsCopyStreaming: Boolean = true
  val supportsDefaultInCopy: Boolean = true

  /** PostgreSQL uses SQL RETURNING clause for all inserts */
  def returningStrategy(cols: NonEmptyList[ComputedColumn], rowType: jvm.Type, maybeId: Option[IdComputed]): ReturningStrategy =
    ReturningStrategy.SqlReturning(rowType)

  /** PostgreSQL supports RETURNING with ON CONFLICT */
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

  /** PostgreSQL uses ON CONFLICT (colname) which expects just the column names, not a join condition */
  override def mergeOnClause(idCols: NonEmptyList[ComputedColumn], quotedColName: ComputedColumn => Code): Code =
    idCols.map(quotedColName).mkCode(", ")

  def streamingInsertSql(tableName: Code, columns: Code): Code =
    code"COPY $tableName($columns) FROM STDIN"

  def createTempTableLike(tempName: String, sourceTable: Code): Code =
    code"create temporary table $tempName (like $sourceTable) on commit drop"

  def returningClause(columns: Code): Code =
    code"RETURNING $columns"

  // ═══════════════════════════════════════════════════════════════════════════
  // LAYER 5: Schema DDL
  // ═══════════════════════════════════════════════════════════════════════════

  def dropSchemaDdl(schemaName: String, cascade: Boolean): String = {
    val cascadeSuffix = if (cascade) " CASCADE" else ""
    s"DROP SCHEMA IF EXISTS $schemaName$cascadeSuffix"
  }

  def createSchemaDdl(schemaName: String): String =
    s"CREATE SCHEMA IF NOT EXISTS $schemaName"
}

object PostgresAdapter {
  val WithTimestampCasts = new PostgresAdapter(needsTimestampCasts = true)
  val NoTimestampCasts = new PostgresAdapter(needsTimestampCasts = false)
}
