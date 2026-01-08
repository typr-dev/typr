package typr
package internal
package codegen

import typr.jvm.Code

object DuckDbAdapter extends DbAdapter {
  val dbType: DbType = DbType.DuckDB

  // ═══════════════════════════════════════════════════════════════════════════
  // LAYER 1: SQL Syntax
  // ═══════════════════════════════════════════════════════════════════════════

  def quoteIdent(name: String): String = s""""$name""""

  def typeCast(value: Code, typeName: String): Code =
    if (typeName.isEmpty) value else code"$value::$typeName"

  // DuckDB uses PostgreSQL-style casting
  def columnReadCast(col: ComputedColumn): Code = Code.Empty
  def columnWriteCast(col: ComputedColumn): Code = Code.Empty
  def writeCastTypeName(col: ComputedColumn): Option[String] = None
  def writeCast(dbType: db.Type, udtName: Option[String]): Option[SqlCastValue] = None
  def readCast(dbType: db.Type): Option[SqlCastValue] = None

  // ═══════════════════════════════════════════════════════════════════════════
  // LAYER 2: Runtime Type System
  // ═══════════════════════════════════════════════════════════════════════════

  val KotlinDbTypes: jvm.Type.Qualified = jvm.Type.Qualified("dev.typr.foundations.kotlin.KotlinDbTypes")
  val ScalaDbTypes: jvm.Type.Qualified = jvm.Type.Qualified("dev.typr.foundations.scala.ScalaDbTypes")
  val Types: jvm.Type.Qualified = jvm.Type.Qualified("dev.typr.foundations.DuckDbTypes")
  val TypeClass: jvm.Type.Qualified = jvm.Type.Qualified("dev.typr.foundations.DuckDbType")
  val TextClass: jvm.Type.Qualified = jvm.Type.Qualified("dev.typr.foundations.DuckDbText")
  val typeFieldName: jvm.Ident = jvm.Ident("duckDbType")
  val textFieldName: jvm.Ident = jvm.Ident("duckDbText")
  def dialectRef(lang: Lang): Code = code"${lang.dsl.Dialect}.DUCKDB"

  def lookupType(typoType: TypoType, naming: Naming, typeSupport: TypeSupport): Code =
    typoType match {
      case TypoType.Standard(_, dbType) =>
        lookupByDbType(dbType, naming, typeSupport)

      case TypoType.Nullable(_, inner) =>
        nullableType(lookupType(inner, naming, typeSupport), typeSupport)

      case TypoType.Generated(_, _, qualifiedType) =>
        code"$qualifiedType.$typeFieldName"

      // Precise types - wrapper types with constraints, all have their own duckDbType field
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
            // Qualified user types must provide their own duckDbType field
            code"$qualifiedType.$typeFieldName"
          case Right(primitive) =>
            // Well-known primitives use the adapter's lookup
            lookupPrimitive(primitive, typeSupport)
        }

      case TypoType.Array(_, element) =>
        // For Scala, use unboxed primitive arrays for better performance
        // For Java/Kotlin, use boxed arrays
        typeSupport match {
          case TypeSupportScala =>
            element match {
              case TypoType.Standard(_, dbType) =>
                dbType match {
                  case db.DuckDbType.Boolean  => code"$ScalaDbTypes.DuckDbTypes.booleanArrayUnboxed"
                  case db.DuckDbType.TinyInt  => code"$ScalaDbTypes.DuckDbTypes.tinyintArrayUnboxed"
                  case db.DuckDbType.SmallInt => code"$ScalaDbTypes.DuckDbTypes.smallintArrayUnboxed"
                  case db.DuckDbType.Integer  => code"$ScalaDbTypes.DuckDbTypes.integerArrayUnboxed"
                  case db.DuckDbType.BigInt   => code"$ScalaDbTypes.DuckDbTypes.bigintArrayUnboxed"
                  case db.DuckDbType.Float    => code"$ScalaDbTypes.DuckDbTypes.floatArrayUnboxed"
                  case db.DuckDbType.Double   => code"$ScalaDbTypes.DuckDbTypes.doubleArrayUnboxed"
                  case _                      => code"${lookupType(element, naming, TypeSupportJava)}.array()"
                }
              // For Scala, Generated and UserDefined types have a dbTypeArray given
              case TypoType.Generated(_, _, qualifiedType) =>
                code"$qualifiedType.dbTypeArray"
              case TypoType.UserDefined(_, _, Left(qualifiedType)) =>
                code"$qualifiedType.dbTypeArray"
              case _ => code"${lookupType(element, naming, TypeSupportJava)}.array()"
            }
          case _ =>
            // Java and Kotlin use boxed arrays - use pre-defined array types
            element match {
              case TypoType.Standard(_, dbType) =>
                dbType match {
                  case db.DuckDbType.TinyInt       => code"$Types.tinyintArray"
                  case db.DuckDbType.SmallInt      => code"$Types.smallintArray"
                  case db.DuckDbType.Integer       => code"$Types.integerArray"
                  case db.DuckDbType.BigInt        => code"$Types.bigintArray"
                  case db.DuckDbType.HugeInt       => code"$Types.hugeintArray"
                  case db.DuckDbType.UTinyInt      => code"$Types.utinyintArray"
                  case db.DuckDbType.USmallInt     => code"$Types.usmallintArray"
                  case db.DuckDbType.UInteger      => code"$Types.uintegerArray"
                  case db.DuckDbType.UBigInt       => code"$Types.ubigintArray"
                  case db.DuckDbType.Float         => code"$Types.floatArray"
                  case db.DuckDbType.Double        => code"$Types.doubleArray"
                  case db.DuckDbType.Decimal(_, _) => code"$Types.decimalArray"
                  case db.DuckDbType.Boolean       => code"$Types.booleanArray"
                  case db.DuckDbType.VarChar(_)    => code"$Types.varcharArray"
                  case db.DuckDbType.Text          => code"$Types.varcharArray"
                  case db.DuckDbType.Blob          => code"$Types.blobArray"
                  case db.DuckDbType.Date          => code"$Types.dateArray"
                  case db.DuckDbType.Time          => code"$Types.timeArray"
                  case db.DuckDbType.Timestamp     => code"$Types.timestampArray"
                  case db.DuckDbType.TimestampTz   => code"$Types.timestamptzArray"
                  case db.DuckDbType.Interval      => code"$Types.intervalArray"
                  case db.DuckDbType.UUID          => code"$Types.uuidArray"
                  case db.DuckDbType.Json          => code"$Types.jsonArray"
                  case _                           => code"${lookupType(element, naming, TypeSupportJava)}.array()"
                }
              // For Generated and UserDefined types, use the pre-defined dbTypeArray field
              case TypoType.Generated(_, _, qualifiedType) =>
                code"$qualifiedType.dbTypeArray"
              case TypoType.UserDefined(_, _, Left(qualifiedType)) =>
                code"$qualifiedType.dbTypeArray"
              case _ => code"${lookupType(element, naming, TypeSupportJava)}.array()"
            }
        }
    }

  def lookupPrimitive(primitive: analysis.WellKnownPrimitive, typeSupport: TypeSupport): Code = {
    // Helper to get primitive type code based on language
    def primitiveType(name: String): Code = {
      val nameIdent = jvm.Ident(name)
      typeSupport match {
        case TypeSupportScala    => code"$ScalaDbTypes.DuckDbTypes.$nameIdent"
        case TypeSupportKotlin   => code"$KotlinDbTypes.DuckDbTypes.$nameIdent"
        case TypeSupportJava | _ => code"$Types.$nameIdent"
      }
    }

    primitive match {
      case analysis.WellKnownPrimitive.String     => code"$Types.varchar"
      case analysis.WellKnownPrimitive.Boolean    => primitiveType("boolean_")
      case analysis.WellKnownPrimitive.Byte       => primitiveType("tinyint")
      case analysis.WellKnownPrimitive.Short      => primitiveType("smallint")
      case analysis.WellKnownPrimitive.Int        => primitiveType("integer")
      case analysis.WellKnownPrimitive.Long       => primitiveType("bigint")
      case analysis.WellKnownPrimitive.Float      => primitiveType("float_")
      case analysis.WellKnownPrimitive.Double     => primitiveType("double_")
      case analysis.WellKnownPrimitive.BigDecimal =>
        // BigDecimal uses java.math.BigDecimal in Kotlin, so use base DuckDbTypes
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

  def textType: db.Type = db.DuckDbType.Text

  /** Public interface for looking up types by db.Type - delegates to lookupByDbType */
  def lookupTypeByDbType(dbType: db.Type, Types: jvm.Type.Qualified, naming: Naming, typeSupport: TypeSupport): Code =
    lookupByDbType(dbType, naming, typeSupport)

  private def lookupByDbType(dbType: db.Type, naming: Naming, typeSupport: TypeSupport): Code = {
    // Helper to get primitive type code based on language
    def primitiveType(name: String): Code = {
      val nameIdent = jvm.Ident(name)
      typeSupport match {
        case TypeSupportScala    => code"$ScalaDbTypes.DuckDbTypes.$nameIdent"
        case TypeSupportKotlin   => code"$KotlinDbTypes.DuckDbTypes.$nameIdent"
        case TypeSupportJava | _ => code"$Types.$nameIdent"
      }
    }

    dbType match {
      case dbType: db.DuckDbType =>
        dbType match {
          // Primitive types with language-specific overrides
          case db.DuckDbType.Boolean       => primitiveType("boolean_")
          case db.DuckDbType.TinyInt       => primitiveType("tinyint")
          case db.DuckDbType.SmallInt      => primitiveType("smallint")
          case db.DuckDbType.Integer       => primitiveType("integer")
          case db.DuckDbType.BigInt        => primitiveType("bigint")
          case db.DuckDbType.Float         => primitiveType("float_")
          case db.DuckDbType.Double        => primitiveType("double_")
          case db.DuckDbType.Decimal(_, _) =>
            // BigDecimal uses java.math.BigDecimal in Kotlin, so use base DuckDbTypes
            typeSupport match {
              case TypeSupportKotlin => code"$Types.numeric"
              case _                 => primitiveType("numeric")
            }

          // Non-primitive types use base Types
          case db.DuckDbType.HugeInt     => code"$Types.hugeint"
          case db.DuckDbType.UTinyInt    => code"$Types.utinyint"
          case db.DuckDbType.USmallInt   => code"$Types.usmallint"
          case db.DuckDbType.UInteger    => code"$Types.uinteger"
          case db.DuckDbType.UBigInt     => code"$Types.ubigint"
          case db.DuckDbType.UHugeInt    => code"$Types.uhugeint"
          case db.DuckDbType.VarChar(_)  => code"$Types.varchar"
          case db.DuckDbType.Char(_)     => code"$Types.char_"
          case db.DuckDbType.Text        => code"$Types.text"
          case db.DuckDbType.Blob        => code"$Types.blob"
          case db.DuckDbType.Bit(_)      => code"$Types.bit"
          case db.DuckDbType.Date        => code"$Types.date"
          case db.DuckDbType.Time        => code"$Types.time"
          case db.DuckDbType.Timestamp   => code"$Types.timestamp"
          case db.DuckDbType.TimestampTz => code"$Types.timestamptz"
          case db.DuckDbType.TimestampS  => code"$Types.timestamp"
          case db.DuckDbType.TimestampMS => code"$Types.timestamp"
          case db.DuckDbType.TimestampNS => code"$Types.timestamp"
          case db.DuckDbType.TimeTz      => code"$Types.timetz"
          case db.DuckDbType.Interval    => code"$Types.interval"
          case db.DuckDbType.UUID        => code"$Types.uuid"
          case db.DuckDbType.Json        => code"$Types.json"

          // Enum
          case db.DuckDbType.Enum(name, _) =>
            code"${jvm.Type.Qualified(naming.enumName(db.RelationName(None, name)))}.$typeFieldName"

          // Composite types - use pre-defined array types for primitives
          case db.DuckDbType.ListType(elementType) =>
            elementType match {
              case db.DuckDbType.TinyInt       => code"$Types.tinyintArray"
              case db.DuckDbType.SmallInt      => code"$Types.smallintArray"
              case db.DuckDbType.Integer       => code"$Types.integerArray"
              case db.DuckDbType.BigInt        => code"$Types.bigintArray"
              case db.DuckDbType.HugeInt       => code"$Types.hugeintArray"
              case db.DuckDbType.UTinyInt      => code"$Types.utinyintArray"
              case db.DuckDbType.USmallInt     => code"$Types.usmallintArray"
              case db.DuckDbType.UInteger      => code"$Types.uintegerArray"
              case db.DuckDbType.UBigInt       => code"$Types.ubigintArray"
              case db.DuckDbType.Float         => code"$Types.floatArray"
              case db.DuckDbType.Double        => code"$Types.doubleArray"
              case db.DuckDbType.Decimal(_, _) => code"$Types.decimalArray"
              case db.DuckDbType.Boolean       => code"$Types.booleanArray"
              case db.DuckDbType.VarChar(_)    => code"$Types.varcharArray"
              case db.DuckDbType.Text          => code"$Types.varcharArray"
              case db.DuckDbType.Blob          => code"$Types.blobArray"
              case db.DuckDbType.Date          => code"$Types.dateArray"
              case db.DuckDbType.Time          => code"$Types.timeArray"
              case db.DuckDbType.Timestamp     => code"$Types.timestampArray"
              case db.DuckDbType.TimestampTz   => code"$Types.timestamptzArray"
              case db.DuckDbType.Interval      => code"$Types.intervalArray"
              case db.DuckDbType.UUID          => code"$Types.uuidArray"
              case db.DuckDbType.Json          => code"$Types.jsonArray"
              case _                           => code"${lookupByDbType(elementType, naming, TypeSupportJava)}.array()"
            }
          case db.DuckDbType.ArrayType(elementType, _) =>
            elementType match {
              case db.DuckDbType.TinyInt       => code"$Types.tinyintArray"
              case db.DuckDbType.SmallInt      => code"$Types.smallintArray"
              case db.DuckDbType.Integer       => code"$Types.integerArray"
              case db.DuckDbType.BigInt        => code"$Types.bigintArray"
              case db.DuckDbType.HugeInt       => code"$Types.hugeintArray"
              case db.DuckDbType.UTinyInt      => code"$Types.utinyintArray"
              case db.DuckDbType.USmallInt     => code"$Types.usmallintArray"
              case db.DuckDbType.UInteger      => code"$Types.uintegerArray"
              case db.DuckDbType.UBigInt       => code"$Types.ubigintArray"
              case db.DuckDbType.Float         => code"$Types.floatArray"
              case db.DuckDbType.Double        => code"$Types.doubleArray"
              case db.DuckDbType.Decimal(_, _) => code"$Types.decimalArray"
              case db.DuckDbType.Boolean       => code"$Types.booleanArray"
              case db.DuckDbType.VarChar(_)    => code"$Types.varcharArray"
              case db.DuckDbType.Text          => code"$Types.varcharArray"
              case db.DuckDbType.Blob          => code"$Types.blobArray"
              case db.DuckDbType.Date          => code"$Types.dateArray"
              case db.DuckDbType.Time          => code"$Types.timeArray"
              case db.DuckDbType.Timestamp     => code"$Types.timestampArray"
              case db.DuckDbType.TimestampTz   => code"$Types.timestamptzArray"
              case db.DuckDbType.Interval      => code"$Types.intervalArray"
              case db.DuckDbType.UUID          => code"$Types.uuidArray"
              case db.DuckDbType.Json          => code"$Types.jsonArray"
              case _                           => code"${lookupByDbType(elementType, naming, TypeSupportJava)}.array()"
            }
          case db.DuckDbType.MapType(keyType, valueType) =>
            code"${lookupByDbType(keyType, naming, TypeSupportJava)}.mapTo(${lookupByDbType(valueType, naming, TypeSupportJava)})"
          case db.DuckDbType.StructType(_) =>
            // StructType should be mapped via TypeMapperJvmNew using the struct lookup
            // This path is only reached for nested structs in lists/maps which aren't supported yet
            sys.error("DuckDbAdapter.lookupByDbType: StructType in nested context not yet supported")
          case db.DuckDbType.UnionType(_) =>
            // UNION types are read/written as JSON strings for now
            code"$Types.varchar" // Fallback to varchar (JSON serialization)

          case db.Unknown(_) => code"$Types.unknown"
        }
      case other =>
        sys.error(s"DuckDbAdapter.lookupByDbType: Cannot lookup from other database: $other")
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // LAYER 3: Capabilities
  // ═══════════════════════════════════════════════════════════════════════════

  val supportsArrays: Boolean = true // Uses encodeArray helper to avoid Scala 3 variance issues
  val supportsReturning: Boolean = true
  val supportsCopyStreaming: Boolean = false // DuckDB uses different COPY mechanism
  val supportsDefaultInCopy: Boolean = false

  /** DuckDB uses SQL RETURNING clause for all inserts */
  def returningStrategy(cols: NonEmptyList[ComputedColumn], rowType: jvm.Type, maybeId: Option[IdComputed]): ReturningStrategy =
    ReturningStrategy.SqlReturning(rowType)

  /** DuckDB supports RETURNING with INSERT OR REPLACE */
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

  /** DuckDB uses ON CONFLICT (colname) which expects just the column names, not a join condition */
  override def mergeOnClause(idCols: NonEmptyList[ComputedColumn], quotedColName: ComputedColumn => Code): Code =
    idCols.map(quotedColName).mkCode(", ")

  def streamingInsertSql(tableName: Code, columns: Code): Code =
    // DuckDB doesn't support streaming COPY in the same way as PostgreSQL
    code"/* DuckDB batch insert */"

  def createTempTableLike(tempName: String, sourceTable: Code): Code =
    code"CREATE TEMPORARY TABLE $tempName AS SELECT * FROM $sourceTable WHERE FALSE"

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
