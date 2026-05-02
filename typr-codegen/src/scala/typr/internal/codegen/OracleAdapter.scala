package typr
package internal
package codegen

import typr.jvm.Code

object OracleAdapter extends DbAdapter {
  val dbType: DbType = DbType.Oracle

  // ═══════════════════════════════════════════════════════════════════════════
  // LAYER 1: SQL Syntax
  // ═══════════════════════════════════════════════════════════════════════════

  def quoteIdent(name: String): String = s""""$name""""

  def typeCast(value: Code, typeName: String): Code =
    if (typeName.isEmpty) value else code"CAST($value AS $typeName)"

  // Oracle doesn't need PostgreSQL-style casts
  def columnReadCast(col: ComputedColumn): Code = Code.Empty
  def columnWriteCast(col: ComputedColumn): Code = Code.Empty
  def writeCastTypeName(col: ComputedColumn): Option[String] = None
  def writeCast(dbType: db.Type, udtName: Option[String]): Option[SqlCastValue] = None
  def readCast(dbType: db.Type): Option[SqlCastValue] = None

  // ═══════════════════════════════════════════════════════════════════════════
  // LAYER 2: Runtime Type System
  // ═══════════════════════════════════════════════════════════════════════════

  val Types: jvm.Type.Qualified = jvm.Type.Qualified("dev.typr.foundations.OracleTypes")
  val TypeClass: jvm.Type.Qualified = jvm.Type.Qualified("dev.typr.foundations.OracleType")
  // Oracle doesn't support text-based streaming inserts (COPY), so TextClass throws if accessed
  def TextClass: jvm.Type.Qualified = sys.error("Oracle doesn't support text-based streaming inserts")

  // Oracle uses NUMBER, not NUMERIC
  override def numericTypeName: String = "number"
  override val KotlinTypes: jvm.Type.Qualified = jvm.Type.Qualified("dev.typr.foundationskt.OracleTypes")
  override val ScalaTypes: jvm.Type.Qualified = jvm.Type.Qualified("dev.typr.foundationssc.OracleTypes")

  val typeFieldName: jvm.Ident = jvm.Ident("oracleType")
  // Oracle doesn't support text-based streaming inserts (COPY), so textFieldName throws if accessed
  def textFieldName: jvm.Ident = sys.error("Oracle doesn't support text-based streaming inserts")
  def dialectRef(lang: Lang): Code = code"${lang.dsl.Dialect}.ORACLE"

  def lookupType(typoType: TypoType, naming: Naming, typeSupport: TypeSupport): Code = typoType match {
    case TypoType.Standard(_, dbType) =>
      lookupByDbType(dbType, naming, typeSupport)

    case TypoType.Nullable(_, inner) =>
      nullableType(lookupType(inner, naming, typeSupport))

    case TypoType.Generated(_, _, qualifiedType) =>
      code"$qualifiedType.$typeFieldName"

    // Precise types - wrapper types with constraints, all have their own oracleType field
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
      sys.error("OracleAdapter.lookupType: Oracle does not support array types in the same way as PostgreSQL")

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
      case analysis.WellKnownPrimitive.String        => primitiveType("varchar2")
      case analysis.WellKnownPrimitive.Boolean       => primitiveType("boolean_")
      case analysis.WellKnownPrimitive.Byte          => primitiveType("numberInt")
      case analysis.WellKnownPrimitive.Short         => primitiveType("numberInt")
      case analysis.WellKnownPrimitive.Int           => primitiveType("numberInt")
      case analysis.WellKnownPrimitive.Long          => primitiveType("numberLong")
      case analysis.WellKnownPrimitive.Float         => primitiveType("binaryFloat")
      case analysis.WellKnownPrimitive.Double        => primitiveType("binaryDouble")
      case analysis.WellKnownPrimitive.BigDecimal    => primitiveType("number")
      case analysis.WellKnownPrimitive.LocalDate     => primitiveType("date")
      case analysis.WellKnownPrimitive.LocalTime     => primitiveType("date") // Oracle DATE includes time
      case analysis.WellKnownPrimitive.LocalDateTime => primitiveType("date")
      case analysis.WellKnownPrimitive.Instant       => primitiveType("timestampWithTimeZone")
      case analysis.WellKnownPrimitive.UUID          => primitiveType("varchar2") // UUID as VARCHAR2(36) in Oracle
    }
  }

  private def lookupByDbType(dbType: db.Type, naming: Naming, typeSupport: TypeSupport): Code = {
    def languageSpecificType(name: String): Code = {
      val nameIdent = jvm.Ident(name)
      typeSupport match {
        case TypeSupportScala    => code"$ScalaTypes.$nameIdent"
        case TypeSupportKotlin   => code"$KotlinTypes.$nameIdent"
        case TypeSupportJava | _ => code"$Types.$nameIdent"
      }
    }

    dbType match {
      case dbType: db.OracleType =>
        dbType match {
          // Oracle types
          case db.OracleType.Number(_, _)                  => languageSpecificType("number")
          case db.OracleType.BinaryFloat                   => languageSpecificType("binaryFloat")
          case db.OracleType.BinaryDouble                  => languageSpecificType("binaryDouble")
          case db.OracleType.Float(_)                      => languageSpecificType("float_")
          case db.OracleType.Varchar2(_)                   => languageSpecificType("varchar2")
          case db.OracleType.NVarchar2(_)                  => languageSpecificType("nvarchar2")
          case db.OracleType.Char(_)                       => languageSpecificType("char_")
          case db.OracleType.NChar(_)                      => languageSpecificType("nchar")
          case db.OracleType.Clob                          => languageSpecificType("clob")
          case db.OracleType.NClob                         => languageSpecificType("nclob")
          case db.OracleType.Long                          => languageSpecificType("long_")
          case db.OracleType.Raw(_)                        => languageSpecificType("raw")
          case db.OracleType.Blob                          => languageSpecificType("blob")
          case db.OracleType.LongRaw                       => languageSpecificType("longRaw")
          case db.OracleType.Date                          => languageSpecificType("date")
          case db.OracleType.Timestamp(_)                  => languageSpecificType("timestamp")
          case db.OracleType.TimestampWithTimeZone(_)      => languageSpecificType("timestampWithTimeZone")
          case db.OracleType.TimestampWithLocalTimeZone(_) => languageSpecificType("timestampWithLocalTimeZone")
          case db.OracleType.IntervalYearToMonth(_)        => languageSpecificType("intervalYearToMonth")
          case db.OracleType.IntervalDayToSecond(_, _)     => languageSpecificType("intervalDayToSecond")
          case db.OracleType.RowId                         => languageSpecificType("rowId")
          case db.OracleType.URowId(_)                     => languageSpecificType("uRowId")
          case db.OracleType.XmlType                       => languageSpecificType("xmlType")
          case db.OracleType.Json                          => languageSpecificType("json")
          case db.OracleType.Boolean                       => languageSpecificType("boolean_")
          // Object-relational types
          case db.OracleType.ObjectType(name, _, _, _, _) =>
            val typeName = naming.objectTypeName(name)
            code"$typeName.$typeFieldName"
          case db.OracleType.VArray(name, _, _) =>
            val typeName = naming.objectTypeName(name)
            code"$typeName.$typeFieldName"
          case db.OracleType.NestedTable(name, _, _) =>
            val typeName = naming.objectTypeName(name)
            code"$typeName.$typeFieldName"
          case db.OracleType.RefType(_)  => languageSpecificType("varchar2") // REF types as strings for now
          case db.OracleType.SdoGeometry => languageSpecificType("varchar2") // Spatial types as strings for now
          case db.OracleType.SdoPoint    => languageSpecificType("varchar2")
          case db.OracleType.AnyData     => languageSpecificType("varchar2") // ANYDATA as string for now
          case db.Unknown(sqlType)       => languageSpecificType("unknown")
        }
      case _ =>
        sys.error(s"OracleAdapter.lookupByDbType: Cannot lookup MariaDB type in PostgreSQL adapter")
    }
  }

  def lookupTypeByDbType(dbType: db.Type, Types: jvm.Type.Qualified, naming: Naming, typeSupport: TypeSupport): Code =
    // Delegate to the language-aware private method
    lookupByDbType(dbType, naming, typeSupport)

  def textType: db.Type = db.OracleType.Varchar2(Some(4000))

  // ═══════════════════════════════════════════════════════════════════════════
  // LAYER 3: Capabilities
  // ═══════════════════════════════════════════════════════════════════════════

  val supportsArrays: Boolean = false // Oracle has VARRAY but different semantics
  val supportsReturning: Boolean = true // Oracle supports RETURNING INTO
  val supportsCopyStreaming: Boolean = false // Oracle uses SQL*Loader, not COPY
  val supportsDefaultInCopy: Boolean = false

  def returningStrategy(cols: NonEmptyList[ComputedColumn], rowType: jvm.Type, maybeId: Option[IdComputed]): ReturningStrategy = {
    // Oracle JDBC doesn't support SQL-level RETURNING clause - must use getGeneratedKeys()
    // However, getGeneratedKeys() fails with ORA-17041 when trying to return STRUCT/ARRAY columns
    val hasStructOrArray = cols.exists { col =>
      col.dbCol.tpe match {
        case _: db.OracleType.ObjectType  => true
        case _: db.OracleType.VArray      => true
        case _: db.OracleType.NestedTable => true
        case _                            => false
      }
    }

    (hasStructOrArray, maybeId) match {
      case (true, Some(id)) =>
        // Can only return ID columns to avoid ORA-17041
        ReturningStrategy.GeneratedKeysIdOnly(id)
      case (true, None) =>
        // Tables with STRUCT/ARRAY but no PK - cannot safely return anything
        // Fall back to returning all columns and hope for the best (may fail at runtime)
        ReturningStrategy.GeneratedKeysAllColumns(rowType, cols)
      case (false, _) =>
        // Can return all columns
        ReturningStrategy.GeneratedKeysAllColumns(rowType, cols)
    }
  }

  /** Oracle MERGE doesn't support RETURNING */
  def upsertStrategy(rowType: jvm.Type): UpsertStrategy =
    UpsertStrategy.NotSupported

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
    // Oracle uses MERGE statement for upsert
    // idColumns is already a proper join condition like "t.col = s.col"
    // Note: RETURNING with MERGE is complex in Oracle, omitted for now
    code"""|MERGE INTO $tableName t
           |USING (SELECT $values FROM DUAL) s
           |ON ($idColumns)
           |WHEN MATCHED THEN UPDATE SET $conflictUpdate
           |WHEN NOT MATCHED THEN INSERT ($columns) VALUES ($values)""".stripMargin

  def conflictUpdateClause(cols: List[ComputedColumn], quotedColName: ComputedColumn => Code): Code =
    cols.map(c => code"t.${quotedColName(c)} = s.${quotedColName(c)}").mkCode(",\n")

  def conflictNoOpClause(firstPkCol: ComputedColumn, quotedColName: ComputedColumn => Code): Code =
    code"t.${quotedColName(firstPkCol)} = t.${quotedColName(firstPkCol)}"

  def streamingInsertSql(tableName: Code, columns: Code): Code =
    // Oracle doesn't support COPY, use batch inserts instead
    code"/* SQL*Loader not directly supported, use batch inserts */"

  def createTempTableLike(tempName: String, sourceTable: Code): Code =
    code"CREATE GLOBAL TEMPORARY TABLE $tempName AS SELECT * FROM $sourceTable WHERE 1=0"

  def returningClause(columns: Code): Code =
    code"RETURNING $columns INTO ?"

  // ═══════════════════════════════════════════════════════════════════════════
  // LAYER 5: Schema DDL
  // ═══════════════════════════════════════════════════════════════════════════

  def dropSchemaDdl(schemaName: String, cascade: Boolean): String =
    throw new UnsupportedOperationException("Oracle schemas are users - use database administration tools")

  def createSchemaDdl(schemaName: String): String =
    throw new UnsupportedOperationException("Oracle schemas are users - use database administration tools")
}
