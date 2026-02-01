package typr.bridge

/** Type compatibility and widening logic for Domain Types.
  *
  * When a domain type field exists across multiple sources, we need to determine the "safest" canonical type. This module handles:
  *   - Type widening (INT + BIGINT -> BIGINT)
  *   - Precision widening (VARCHAR(100) + VARCHAR(255) -> VARCHAR(255))
  *   - Nullability merging (nullable + non-null -> nullable)
  *   - Incompatibility detection with explanations
  */
object TypeNarrower {

  /** Result of finding a canonical type for a field across sources */
  case class CanonicalTypeResult(
      canonicalType: String,
      jvmType: String,
      nullable: Boolean,
      warnings: List[String],
      comment: String
  )

  /** Database type info from a source */
  case class DbTypeInfo(
      sourceName: String,
      dbType: String,
      nullable: Boolean,
      precision: Option[Int],
      scale: Option[Int]
  )

  /** Find the safest canonical type from multiple source types.
    *
    * Rules (pick widest/most permissive):
    *   - INT + BIGINT -> BIGINT (wider wins)
    *   - VARCHAR(100) + VARCHAR(255) -> VARCHAR(255) (wider wins)
    *   - nullable + non-null -> nullable (permissive wins)
    *   - incompatible types -> error with explanation
    */
  def findCanonicalType(types: List[DbTypeInfo]): CanonicalTypeResult = {
    if (types.isEmpty) {
      return CanonicalTypeResult(
        canonicalType = "String",
        jvmType = "String",
        nullable = true,
        warnings = List("No source types provided"),
        comment = "default"
      )
    }

    if (types.size == 1) {
      val single = types.head
      val (canonical, jvm) = mapDbTypeToCanonical(single.dbType)
      return CanonicalTypeResult(
        canonicalType = canonical,
        jvmType = jvm,
        nullable = single.nullable,
        warnings = Nil,
        comment = "exact match"
      )
    }

    val warnings = List.newBuilder[String]
    val nullable = types.exists(_.nullable)

    if (nullable && types.exists(!_.nullable)) {
      warnings += s"Nullability differs across sources - using nullable"
    }

    val typeGroups = types.groupBy(t => normalizeDbType(t.dbType))

    if (typeGroups.size == 1) {
      val (canonical, jvm) = mapDbTypeToCanonical(types.head.dbType)
      val maxPrecision = types.flatMap(_.precision).maxOption
      val comment = if (types.map(_.dbType).distinct.size > 1) {
        s"exact (${types.map(t => s"${t.sourceName}:${t.dbType}").mkString(", ")})"
      } else {
        "exact match"
      }
      return CanonicalTypeResult(
        canonicalType = canonical,
        jvmType = jvm,
        nullable = nullable,
        warnings = warnings.result(),
        comment = comment
      )
    }

    val normalizedTypes = typeGroups.keys.toList.sorted
    findWidestType(normalizedTypes) match {
      case Some((canonical, jvm, widenComment)) =>
        val sourceInfo = types.map(t => s"${t.sourceName}:${t.dbType}").mkString(", ")
        warnings += s"$widenComment ($sourceInfo)"
        CanonicalTypeResult(
          canonicalType = canonical,
          jvmType = jvm,
          nullable = nullable,
          warnings = warnings.result(),
          comment = widenComment
        )

      case None =>
        val sourceInfo = types.map(t => s"${t.sourceName}:${t.dbType}").mkString(", ")
        CanonicalTypeResult(
          canonicalType = "String",
          jvmType = "String",
          nullable = true,
          warnings = warnings.result() :+ s"Incompatible types ($sourceInfo) - defaulting to String",
          comment = s"incompatible: ${normalizedTypes.mkString(", ")}"
        )
    }
  }

  /** Map canonical domain type names to normalized DB type names */
  def mapCanonicalToNormalized(canonical: String): String = canonical match {
    case "Int"            => "INTEGER"
    case "Long"           => "BIGINT"
    case "Short"          => "SMALLINT"
    case "Byte"           => "SMALLINT"
    case "Float"          => "REAL"
    case "Double"         => "DOUBLE"
    case "Boolean"        => "BOOLEAN"
    case "String"         => "VARCHAR"
    case "BigDecimal"     => "DECIMAL"
    case "BigInteger"     => "BIGINT"
    case "LocalDateTime"  => "TIMESTAMP"
    case "OffsetDateTime" => "TIMESTAMPTZ"
    case "ZonedDateTime"  => "TIMESTAMPTZ"
    case "Instant"        => "TIMESTAMPTZ"
    case "LocalDate"      => "DATE"
    case "LocalTime"      => "TIME"
    case "UUID"           => "UUID"
    case "ByteArray"      => "BYTEA"
    case "Json"           => "JSON"
    case other            => other.toUpperCase
  }

  /** Normalize a database type for comparison */
  def normalizeDbType(dbType: String): String = {
    val upper = dbType.toUpperCase.trim
    val withoutParams = upper.replaceAll("\\(.*\\)", "")
    withoutParams match {
      case "INT4" | "INTEGER" | "INT" | "SERIAL"                                                             => "INTEGER"
      case "INT8" | "BIGINT" | "BIGSERIAL" | "LONG"                                                          => "BIGINT"
      case "INT2" | "SMALLINT" | "TINYINT" | "SHORT"                                                         => "SMALLINT"
      case "FLOAT4" | "REAL" | "FLOAT"                                                                       => "REAL"
      case "FLOAT8" | "DOUBLE PRECISION" | "DOUBLE"                                                          => "DOUBLE"
      case "BOOL" | "BOOLEAN"                                                                                => "BOOLEAN"
      case "VARCHAR" | "CHARACTER VARYING" | "TEXT" | "CHAR" | "CHARACTER" | "BPCHAR" | "NVARCHAR" | "NCHAR" => "VARCHAR"
      case "NUMERIC" | "DECIMAL" | "DEC"                                                                     => "DECIMAL"
      case "TIMESTAMP" | "TIMESTAMP WITHOUT TIME ZONE" | "DATETIME"                                          => "TIMESTAMP"
      case "TIMESTAMPTZ" | "TIMESTAMP WITH TIME ZONE"                                                        => "TIMESTAMPTZ"
      case "DATE"                                                                                            => "DATE"
      case "TIME" | "TIME WITHOUT TIME ZONE"                                                                 => "TIME"
      case "TIMETZ" | "TIME WITH TIME ZONE"                                                                  => "TIMETZ"
      case "UUID"                                                                                            => "UUID"
      case "BYTEA" | "BLOB" | "BINARY" | "VARBINARY" | "RAW"                                                 => "BYTEA"
      case "JSON" | "JSONB"                                                                                  => "JSON"
      case other                                                                                             => other
    }
  }

  /** Map a database type to canonical/JVM types */
  private def mapDbTypeToCanonical(dbType: String): (String, String) = {
    val normalized = normalizeDbType(dbType)
    normalized match {
      case "INTEGER"     => ("Int", "Int")
      case "BIGINT"      => ("Long", "Long")
      case "SMALLINT"    => ("Short", "Short")
      case "REAL"        => ("Float", "Float")
      case "DOUBLE"      => ("Double", "Double")
      case "BOOLEAN"     => ("Boolean", "Boolean")
      case "VARCHAR"     => ("String", "String")
      case "DECIMAL"     => ("BigDecimal", "BigDecimal")
      case "TIMESTAMP"   => ("LocalDateTime", "LocalDateTime")
      case "TIMESTAMPTZ" => ("OffsetDateTime", "OffsetDateTime")
      case "DATE"        => ("LocalDate", "LocalDate")
      case "TIME"        => ("LocalTime", "LocalTime")
      case "TIMETZ"      => ("OffsetTime", "OffsetTime")
      case "UUID"        => ("UUID", "UUID")
      case "BYTEA"       => ("ByteArray", "Array[Byte]")
      case "JSON"        => ("Json", "Json")
      case other         => (other, "Any")
    }
  }

  /** Try to find the widest compatible type from a list of normalized types */
  private def findWidestType(types: List[String]): Option[(String, String, String)] = {
    val typeSet = types.toSet

    if (typeSet.subsetOf(Set("INTEGER", "BIGINT", "SMALLINT"))) {
      if (typeSet.contains("BIGINT")) {
        Some(("Long", "Long", "INT widened to BIGINT"))
      } else if (typeSet.contains("INTEGER")) {
        Some(("Int", "Int", "SMALLINT widened to INT"))
      } else {
        Some(("Short", "Short", "matched"))
      }
    } else if (typeSet.subsetOf(Set("REAL", "DOUBLE", "DECIMAL"))) {
      if (typeSet.contains("DECIMAL")) {
        Some(("BigDecimal", "BigDecimal", "widened to DECIMAL"))
      } else if (typeSet.contains("DOUBLE")) {
        Some(("Double", "Double", "FLOAT widened to DOUBLE"))
      } else {
        Some(("Float", "Float", "matched"))
      }
    } else if (typeSet.subsetOf(Set("VARCHAR"))) {
      Some(("String", "String", "matched"))
    } else if (typeSet.subsetOf(Set("TIMESTAMP", "TIMESTAMPTZ"))) {
      if (typeSet.contains("TIMESTAMPTZ")) {
        Some(("OffsetDateTime", "OffsetDateTime", "TIMESTAMP widened to TIMESTAMPTZ"))
      } else {
        Some(("LocalDateTime", "LocalDateTime", "matched"))
      }
    } else if (typeSet.subsetOf(Set("TIME", "TIMETZ"))) {
      if (typeSet.contains("TIMETZ")) {
        Some(("OffsetTime", "OffsetTime", "TIME widened to TIMETZ"))
      } else {
        Some(("LocalTime", "LocalTime", "matched"))
      }
    } else {
      None
    }
  }

  /** Check if two database types are compatible */
  def areTypesCompatible(type1: String, type2: String): Boolean = {
    val norm1 = normalizeDbType(type1)
    val norm2 = normalizeDbType(type2)

    if (norm1 == norm2) true
    else {
      val integerFamily = Set("INTEGER", "BIGINT", "SMALLINT")
      val floatFamily = Set("REAL", "DOUBLE", "DECIMAL")
      val timestampFamily = Set("TIMESTAMP", "TIMESTAMPTZ")
      val timeFamily = Set("TIME", "TIMETZ")

      (integerFamily.contains(norm1) && integerFamily.contains(norm2)) ||
      (floatFamily.contains(norm1) && floatFamily.contains(norm2)) ||
      (timestampFamily.contains(norm1) && timestampFamily.contains(norm2)) ||
      (timeFamily.contains(norm1) && timeFamily.contains(norm2))
    }
  }

  /** Generate a comment explaining a field mapping */
  def generateMappingComment(
      canonicalField: String,
      sourceField: String,
      canonicalType: String,
      sourceType: String,
      wasWidened: Boolean,
      autoMapped: Boolean
  ): String = {
    val parts = List.newBuilder[String]

    if (autoMapped && canonicalField != sourceField) {
      parts += s"matched via name alignment: $sourceField -> $canonicalField"
    }

    if (wasWidened) {
      parts += s"$sourceType widened to $canonicalType"
    } else if (normalizeDbType(sourceType) == normalizeDbType(canonicalType)) {
      parts += "exact"
    }

    val result = parts.result()
    if (result.isEmpty) "exact" else result.mkString(", ")
  }
}
