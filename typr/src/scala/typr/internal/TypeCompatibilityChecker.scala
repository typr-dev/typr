package typr
package internal

/** Checks that all sources matching a TypeEntry have compatible underlying types.
  *
  * When a TypeEntry matches multiple columns and API fields, they must all have compatible types (i.e., they can all be represented by the same JVM type).
  */
object TypeCompatibilityChecker {

  /** Result of checking type compatibility for a TypeEntry */
  sealed trait CheckResult {
    def entry: TypeEntry
  }

  object CheckResult {

    /** All matched sources have compatible types */
    case class Compatible(
        entry: TypeEntry,
        matches: List[TypeMatcher.MatchResult],
        canonicalType: db.Type
    ) extends CheckResult

    /** No sources matched this entry */
    case class NoMatches(entry: TypeEntry) extends CheckResult

    /** Matched sources have incompatible types */
    case class Incompatible(
        entry: TypeEntry,
        matches: List[TypeMatcher.MatchResult],
        typeGroups: Map[CompatibilityClass, List[TypeMatcher.MatchResult]]
    ) extends CheckResult {
      def errorMessage: String = {
        val groupDescriptions = typeGroups
          .map { case (cls, results) =>
            val sources = results.map(r => sourceDescription(r.source)).mkString(", ")
            s"  - ${cls.description}: $sources"
          }
          .mkString("\n")
        s"""Type '${entry.name}' has incompatible underlying types:
           |$groupDescriptions""".stripMargin
      }

      private def sourceDescription(source: TypeMatcher.MatchSource): String = source match {
        case TypeMatcher.MatchSource.DbColumn(db, schema, table, col) =>
          s"$db.$schema.$table.$col"
        case TypeMatcher.MatchSource.ApiField(spec, path, field, _) =>
          s"$spec:$path.$field"
      }
    }
  }

  /** A compatibility class groups types that map to the same JVM type */
  case class CompatibilityClass(description: String)

  /** Check compatibility for a single TypeEntry given its matches */
  def check(entry: TypeEntry, matches: List[TypeMatcher.MatchResult]): CheckResult = {
    if (matches.isEmpty) {
      CheckResult.NoMatches(entry)
    } else {
      val grouped = matches.groupBy(m => compatibilityClass(m.dbType))
      if (grouped.size == 1) {
        val (_, results) = grouped.head
        CheckResult.Compatible(entry, matches, results.head.dbType)
      } else {
        CheckResult.Incompatible(entry, matches, grouped)
      }
    }
  }

  /** Check compatibility for all entries in TypeDefinitions */
  def checkAll(
      definitions: TypeDefinitions,
      allMatches: Map[TypeEntry, List[TypeMatcher.MatchResult]]
  ): List[CheckResult] =
    definitions.entries.map { entry =>
      check(entry, allMatches.getOrElse(entry, Nil))
    }

  /** Determine the compatibility class for a database type. Types in the same class can be represented by the same JVM type.
    */
  def compatibilityClass(tpe: db.Type): CompatibilityClass = tpe match {
    // Text types
    case db.PgType.Text | db.PgType.VarChar(_) | db.PgType.Bpchar(_) | db.PgType.Char | db.PgType.Name =>
      CompatibilityClass("String")
    case db.MariaType.Text | db.MariaType.TinyText | db.MariaType.MediumText | db.MariaType.LongText | db.MariaType.VarChar(_) | db.MariaType.Char(_) =>
      CompatibilityClass("String")
    case db.DuckDbType.Text | db.DuckDbType.VarChar(_) | db.DuckDbType.Char(_) =>
      CompatibilityClass("String")
    case db.OracleType.Varchar2(_) | db.OracleType.NVarchar2(_) | db.OracleType.Char(_) | db.OracleType.NChar(_) | db.OracleType.Clob | db.OracleType.NClob | db.OracleType.Long =>
      CompatibilityClass("String")
    case db.SqlServerType.VarChar(_) | db.SqlServerType.NVarChar(_) | db.SqlServerType.Char(_) | db.SqlServerType.NChar(_) | db.SqlServerType.Text | db.SqlServerType.NText =>
      CompatibilityClass("String")
    case db.DB2Type.VarChar(_) | db.DB2Type.Char(_) | db.DB2Type.Clob | db.DB2Type.Long | db.DB2Type.Graphic(_) | db.DB2Type.VarGraphic(_) | db.DB2Type.DbClob | db.DB2Type.LongVarGraphic =>
      CompatibilityClass("String")

    // Boolean types
    case db.PgType.Boolean     => CompatibilityClass("Boolean")
    case db.MariaType.Boolean  => CompatibilityClass("Boolean")
    case db.DuckDbType.Boolean => CompatibilityClass("Boolean")
    case db.OracleType.Boolean => CompatibilityClass("Boolean")
    case db.SqlServerType.Bit  => CompatibilityClass("Boolean")
    case db.DB2Type.Boolean    => CompatibilityClass("Boolean")

    // 32-bit integer types
    case db.PgType.Int4        => CompatibilityClass("Int")
    case db.MariaType.Int      => CompatibilityClass("Int")
    case db.DuckDbType.Integer => CompatibilityClass("Int")
    case db.SqlServerType.Int  => CompatibilityClass("Int")
    case db.DB2Type.Integer    => CompatibilityClass("Int")

    // 64-bit integer types
    case db.PgType.Int8                                             => CompatibilityClass("Long")
    case db.MariaType.BigInt                                        => CompatibilityClass("Long")
    case db.DuckDbType.BigInt                                       => CompatibilityClass("Long")
    case db.SqlServerType.BigInt                                    => CompatibilityClass("Long")
    case db.DB2Type.BigInt                                          => CompatibilityClass("Long")
    case db.OracleType.Number(Some(p), Some(0)) if p > 9 && p <= 18 => CompatibilityClass("Long")

    // 16-bit integer types
    case db.PgType.Int2            => CompatibilityClass("Short")
    case db.MariaType.SmallInt     => CompatibilityClass("Short")
    case db.DuckDbType.SmallInt    => CompatibilityClass("Short")
    case db.SqlServerType.SmallInt => CompatibilityClass("Short")
    case db.DB2Type.SmallInt       => CompatibilityClass("Short")

    // Single-precision float
    case db.PgType.Float4          => CompatibilityClass("Float")
    case db.MariaType.Float        => CompatibilityClass("Float")
    case db.DuckDbType.Float       => CompatibilityClass("Float")
    case db.SqlServerType.Real     => CompatibilityClass("Float")
    case db.DB2Type.Real           => CompatibilityClass("Float")
    case db.OracleType.BinaryFloat => CompatibilityClass("Float")

    // Double-precision float
    case db.PgType.Float8           => CompatibilityClass("Double")
    case db.MariaType.Double        => CompatibilityClass("Double")
    case db.DuckDbType.Double       => CompatibilityClass("Double")
    case db.SqlServerType.Float     => CompatibilityClass("Double")
    case db.DB2Type.Double          => CompatibilityClass("Double")
    case db.OracleType.BinaryDouble => CompatibilityClass("Double")

    // Decimal/Numeric types -> BigDecimal
    case db.PgType.Numeric                                                                                                      => CompatibilityClass("BigDecimal")
    case db.MariaType.Decimal(_, _)                                                                                             => CompatibilityClass("BigDecimal")
    case db.DuckDbType.Decimal(_, _)                                                                                            => CompatibilityClass("BigDecimal")
    case db.OracleType.Number(_, _)                                                                                             => CompatibilityClass("BigDecimal")
    case db.OracleType.Float(_)                                                                                                 => CompatibilityClass("BigDecimal")
    case db.SqlServerType.Decimal(_, _) | db.SqlServerType.Numeric(_, _) | db.SqlServerType.Money | db.SqlServerType.SmallMoney => CompatibilityClass("BigDecimal")
    case db.DB2Type.Decimal(_, _) | db.DB2Type.DecFloat(_)                                                                      => CompatibilityClass("BigDecimal")

    // Date types
    case db.PgType.Date        => CompatibilityClass("LocalDate")
    case db.MariaType.Date     => CompatibilityClass("LocalDate")
    case db.DuckDbType.Date    => CompatibilityClass("LocalDate")
    case db.OracleType.Date    => CompatibilityClass("LocalDate")
    case db.SqlServerType.Date => CompatibilityClass("LocalDate")
    case db.DB2Type.Date       => CompatibilityClass("LocalDate")

    // Time types
    case db.PgType.Time           => CompatibilityClass("LocalTime")
    case db.MariaType.Time(_)     => CompatibilityClass("LocalTime")
    case db.DuckDbType.Time       => CompatibilityClass("LocalTime")
    case db.SqlServerType.Time(_) => CompatibilityClass("LocalTime")
    case db.DB2Type.Time          => CompatibilityClass("LocalTime")

    // Timestamp with timezone
    case db.PgType.TimestampTz                  => CompatibilityClass("Instant")
    case db.DuckDbType.TimestampTz              => CompatibilityClass("Instant")
    case db.OracleType.TimestampWithTimeZone(_) => CompatibilityClass("Instant")
    case db.SqlServerType.DateTimeOffset(_)     => CompatibilityClass("Instant")

    // Timestamp without timezone
    case db.PgType.Timestamp                                                                        => CompatibilityClass("LocalDateTime")
    case db.MariaType.DateTime(_) | db.MariaType.Timestamp(_)                                       => CompatibilityClass("LocalDateTime")
    case db.DuckDbType.Timestamp                                                                    => CompatibilityClass("LocalDateTime")
    case db.OracleType.Timestamp(_) | db.OracleType.TimestampWithLocalTimeZone(_)                   => CompatibilityClass("LocalDateTime")
    case db.SqlServerType.DateTime | db.SqlServerType.DateTime2(_) | db.SqlServerType.SmallDateTime => CompatibilityClass("LocalDateTime")
    case db.DB2Type.Timestamp(_)                                                                    => CompatibilityClass("LocalDateTime")

    // UUID
    case db.PgType.UUID                    => CompatibilityClass("UUID")
    case db.DuckDbType.UUID                => CompatibilityClass("UUID")
    case db.SqlServerType.UniqueIdentifier => CompatibilityClass("UUID")

    // Binary types
    case db.PgType.Bytea                                                                                                                                  => CompatibilityClass("ByteArray")
    case db.MariaType.Blob | db.MariaType.TinyBlob | db.MariaType.MediumBlob | db.MariaType.LongBlob | db.MariaType.Binary(_) | db.MariaType.VarBinary(_) => CompatibilityClass("ByteArray")
    case db.DuckDbType.Blob                                                                                                                               => CompatibilityClass("ByteArray")
    case db.OracleType.Blob | db.OracleType.Raw(_) | db.OracleType.LongRaw                                                                                => CompatibilityClass("ByteArray")
    case db.SqlServerType.Binary(_) | db.SqlServerType.VarBinary(_) | db.SqlServerType.Image                                                              => CompatibilityClass("ByteArray")
    case db.DB2Type.Blob | db.DB2Type.Binary(_) | db.DB2Type.VarBinary(_)                                                                                 => CompatibilityClass("ByteArray")

    // JSON types
    case db.PgType.Json | db.PgType.Jsonb => CompatibilityClass("Json")
    case db.MariaType.Json                => CompatibilityClass("Json")
    case db.DuckDbType.Json               => CompatibilityClass("Json")
    case db.OracleType.Json               => CompatibilityClass("Json")
    case db.SqlServerType.Json            => CompatibilityClass("Json")

    // Array types - compatible if element types are compatible
    case db.PgType.Array(inner)            => CompatibilityClass(s"Array[${compatibilityClass(inner).description}]")
    case db.DuckDbType.ListType(inner)     => CompatibilityClass(s"Array[${compatibilityClass(inner).description}]")
    case db.DuckDbType.ArrayType(inner, _) => CompatibilityClass(s"Array[${compatibilityClass(inner).description}]")

    // Enums - each enum is its own class
    case db.PgType.EnumRef(enm)      => CompatibilityClass(s"Enum[${enm.name.value}]")
    case db.MariaType.Enum(values)   => CompatibilityClass(s"Enum[${values.mkString(",")}]")
    case db.DuckDbType.Enum(name, _) => CompatibilityClass(s"Enum[$name]")

    // Domain/Alias types - use underlying type's class
    case db.PgType.DomainRef(_, _, underlyingType)              => compatibilityClass(underlyingType)
    case db.SqlServerType.AliasTypeRef(_, _, underlyingType, _) => compatibilityClass(underlyingType)
    case db.DB2Type.DistinctType(_, sourceType)                 => compatibilityClass(sourceType)

    // Fallback - each unknown type is its own class
    case other => CompatibilityClass(typeName(other))
  }

  /** Get a descriptive name for a db.Type */
  private def typeName(tpe: db.Type): String = tpe match {
    case db.Unknown(sqlType) => sqlType
    case other               => other.toString
  }
}
