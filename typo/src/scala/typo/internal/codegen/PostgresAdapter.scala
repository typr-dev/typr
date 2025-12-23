package typo
package internal
package codegen

import typo.jvm.Code

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
          case _: db.MariaType                                           => None
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

  val KotlinDbTypes = jvm.Type.Qualified("typo.kotlindsl.KotlinDbTypes")
  val KotlinNullableExtension = jvm.Type.Qualified("typo.kotlindsl.nullable")
  val ScalaDbTypes = jvm.Type.Qualified("typo.scaladsl.ScalaDbTypes")
  val ScalaDbTypeOps = jvm.Type.Qualified("typo.scaladsl.PgTypeOps")
  val Types: jvm.Type.Qualified = jvm.Type.Qualified("typo.runtime.PgTypes")
  val TypeClass: jvm.Type.Qualified = jvm.Type.Qualified("typo.runtime.PgType")
  val TextClass: jvm.Type.Qualified = jvm.Type.Qualified("typo.runtime.PgText")
  val typeFieldName: jvm.Ident = jvm.Ident("pgType")
  val textFieldName: jvm.Ident = jvm.Ident("pgText")

  def dialectRef(lang: Lang): Code = code"${lang.dsl.Dialect}.POSTGRESQL"

  def lookupType(typoType: TypoType, naming: Naming, typeSupport: TypeSupport): Code = typoType match {
    case TypoType.Standard(_, dbType) =>
      lookupByDbType(dbType, naming, typeSupport)

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
          // Qualified user types must provide their own pgType field
          code"$qualifiedType.$typeFieldName"
        case Right(primitive) =>
          // Well-known primitives use the adapter's lookup
          lookupPrimitive(primitive, typeSupport)
      }

    case TypoType.Array(_, element) =>
      // For Scala, use unboxed primitive arrays (int[], long[], etc.) for better performance
      // For Java/Kotlin, use boxed arrays (Integer[], Long[], etc.)
      typeSupport match {
        case TypeSupportScala =>
          element match {
            case TypoType.Standard(_, dbType) =>
              dbType match {
                case db.PgType.Boolean => code"$Types.boolArrayUnboxed"
                case db.PgType.Int2    => code"$Types.int2ArrayUnboxed"
                case db.PgType.Int4    => code"$Types.int4ArrayUnboxed"
                case db.PgType.Int8    => code"$Types.int8ArrayUnboxed"
                case db.PgType.Float4  => code"$Types.float4ArrayUnboxed"
                case db.PgType.Float8  => code"$Types.float8ArrayUnboxed"
                case db.PgType.Numeric => code"$ScalaDbTypes.PgTypes.numericArray"
                case _                 => code"${lookupType(element, naming, TypeSupportJava)}Array"
              }
            case _ => code"${lookupType(element, naming, TypeSupportJava)}Array"
          }
        case _ =>
          // Java and Kotlin use boxed arrays
          code"${lookupType(element, naming, TypeSupportJava)}Array"
      }
  }

  def lookupPrimitive(primitive: analysis.WellKnownPrimitive, typeSupport: TypeSupport): Code = {
    // Helper to get primitive type code based on language
    def primitiveType(name: String): Code = {
      val nameIdent = jvm.Ident(name)
      typeSupport match {
        case TypeSupportScala    => code"$ScalaDbTypes.PgTypes.$nameIdent"
        case TypeSupportKotlin   => code"$KotlinDbTypes.PgTypes.$nameIdent"
        case TypeSupportJava | _ => code"$Types.$nameIdent"
      }
    }

    primitive match {
      case analysis.WellKnownPrimitive.String     => code"$Types.text"
      case analysis.WellKnownPrimitive.Boolean    => primitiveType("bool")
      case analysis.WellKnownPrimitive.Byte       => primitiveType("int2")
      case analysis.WellKnownPrimitive.Short      => primitiveType("int2")
      case analysis.WellKnownPrimitive.Int        => primitiveType("int4")
      case analysis.WellKnownPrimitive.Long       => primitiveType("int8")
      case analysis.WellKnownPrimitive.Float      => primitiveType("float4")
      case analysis.WellKnownPrimitive.Double     => primitiveType("float8")
      case analysis.WellKnownPrimitive.BigDecimal =>
        // BigDecimal uses java.math.BigDecimal in Kotlin, so use base PgTypes
        typeSupport match {
          case TypeSupportKotlin => code"$Types.numeric"
          case _                 => primitiveType("numeric")
        }
      case analysis.WellKnownPrimitive.LocalDate     => code"$Types.date"
      case analysis.WellKnownPrimitive.LocalTime     => code"$Types.time"
      case analysis.WellKnownPrimitive.LocalDateTime => code"$Types.timestamp"
      case analysis.WellKnownPrimitive.Instant       => code"$Types.timestamptz"
      case analysis.WellKnownPrimitive.UUID          => code"$Types.uuid"
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
        case TypeSupportScala    => code"$ScalaDbTypes.PgTypes.$nameIdent"
        case TypeSupportKotlin   => code"$KotlinDbTypes.PgTypes.$nameIdent"
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
      case db.PgType.Numeric =>
        // BigDecimal uses java.math.BigDecimal in Kotlin, so use base PgTypes
        typeSupport match {
          case TypeSupportKotlin => code"$Types.numeric"
          case _                 => primitiveType("numeric")
        }
      case db.PgType.Hstore => primitiveType("hstore")

      // Non-primitive types use base Types
      case db.PgType.Bytea         => code"$Types.bytea"
      case db.PgType.Bpchar(_)     => code"$Types.bpchar"
      case db.PgType.Char          => code"$Types.char"
      case db.PgType.Date          => code"$Types.date"
      case db.PgType.Inet          => code"$Types.inet"
      case db.PgType.Json          => code"$Types.json"
      case db.PgType.Jsonb         => code"$Types.jsonb"
      case db.PgType.Name          => code"$Types.name"
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
      case db.Unknown(_)           => code"$Types.unknown"

      case db.PgType.DomainRef(name, _, _) =>
        code"${jvm.Type.Qualified(naming.domainName(name))}.$typeFieldName"
      case db.PgType.EnumRef(enm) =>
        code"${jvm.Type.Qualified(naming.enumName(enm.name))}.$typeFieldName"
      case db.PgType.Array(inner) =>
        // For Scala, use unboxed primitive arrays for better performance
        // For Java/Kotlin, use boxed arrays
        typeSupport match {
          case TypeSupportScala =>
            inner match {
              case db.PgType.Boolean => code"$Types.boolArrayUnboxed"
              case db.PgType.Int2    => code"$Types.int2ArrayUnboxed"
              case db.PgType.Int4    => code"$Types.int4ArrayUnboxed"
              case db.PgType.Int8    => code"$Types.int8ArrayUnboxed"
              case db.PgType.Float4  => code"$Types.float4ArrayUnboxed"
              case db.PgType.Float8  => code"$Types.float8ArrayUnboxed"
              case db.PgType.Numeric => code"$ScalaDbTypes.PgTypes.numericArray"
              case _                 => code"${lookupByDbType(inner, naming, TypeSupportJava)}Array"
            }
          case _ =>
            // Java and Kotlin use boxed arrays
            code"${lookupByDbType(inner, naming, TypeSupportJava)}Array"
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

  def returningClause(columns: Code): Code =
    code"RETURNING $columns"
}

object PostgresAdapter {
  val WithTimestampCasts = new PostgresAdapter(needsTimestampCasts = true)
  val NoTimestampCasts = new PostgresAdapter(needsTimestampCasts = false)
}
