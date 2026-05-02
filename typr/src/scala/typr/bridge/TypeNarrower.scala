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

  /** Normalize a database or external type (Proto/Avro) for comparison */
  def normalizeDbType(dbType: String): String = {
    val upper = dbType.toUpperCase.trim
    val withoutParams = upper.replaceAll("\\(.*\\)", "")
    withoutParams match {
      // Database types
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
      // Proto scalar types
      case "INT32" | "SINT32" | "SFIXED32" | "UINT32" | "FIXED32" => "INTEGER"
      case "INT64" | "SINT64" | "SFIXED64" | "UINT64" | "FIXED64" => "BIGINT"
      // Proto well-known types (uppercase)
      case "GOOGLE.PROTOBUF.TIMESTAMP"                                  => "TIMESTAMPTZ"
      case "GOOGLE.PROTOBUF.DURATION"                                   => "VARCHAR"
      case "GOOGLE.TYPE.DATE"                                           => "DATE"
      case "GOOGLE.PROTOBUF.INT32VALUE" | "GOOGLE.PROTOBUF.UINT32VALUE" => "INTEGER"
      case "GOOGLE.PROTOBUF.INT64VALUE" | "GOOGLE.PROTOBUF.UINT64VALUE" => "BIGINT"
      case "GOOGLE.PROTOBUF.FLOATVALUE"                                 => "REAL"
      case "GOOGLE.PROTOBUF.DOUBLEVALUE"                                => "DOUBLE"
      case "GOOGLE.PROTOBUF.BOOLVALUE"                                  => "BOOLEAN"
      case "GOOGLE.PROTOBUF.STRINGVALUE"                                => "VARCHAR"
      case "GOOGLE.PROTOBUF.BYTESVALUE"                                 => "BYTEA"
      // Avro types (uppercase)
      case "NULL"   => "NULL"
      case "BYTES"  => "BYTEA"
      case "STRING" => "VARCHAR"
      case other    => other
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

  // ═══════════════════════════════════════════════════════════════════════════════
  // Avro Type Support
  // ═══════════════════════════════════════════════════════════════════════════════

  /** Normalize an Avro type string for comparison with domain/database types */
  def normalizeAvroType(avroType: String): String = {
    val lower = avroType.toLowerCase.trim
    lower match {
      case "int"                        => "INTEGER"
      case "long"                       => "BIGINT"
      case "float"                      => "REAL"
      case "double"                     => "DOUBLE"
      case "boolean"                    => "BOOLEAN"
      case "string"                     => "VARCHAR"
      case "bytes"                      => "BYTEA"
      case "null"                       => "NULL"
      case "uuid"                       => "UUID"
      case "date"                       => "DATE"
      case "time-millis"                => "TIME"
      case "time-micros"                => "TIME"
      case "time-nanos"                 => "TIME"
      case "timestamp-millis"           => "TIMESTAMPTZ"
      case "timestamp-micros"           => "TIMESTAMPTZ"
      case "timestamp-nanos"            => "TIMESTAMPTZ"
      case "local-timestamp-millis"     => "TIMESTAMP"
      case "local-timestamp-micros"     => "TIMESTAMP"
      case "local-timestamp-nanos"      => "TIMESTAMP"
      case "duration"                   => "INTERVAL"
      case s if s.startsWith("decimal") => "DECIMAL"
      case s if s.endsWith("?")         => normalizeAvroType(s.dropRight(1))
      case s if s.startsWith("[")       => "ARRAY"
      case s if s.startsWith("map<")    => "JSON"
      case other                        => other.toUpperCase
    }
  }

  /** Map an Avro type to canonical/JVM types */
  def mapAvroTypeToCanonical(avroType: String): (String, String) = {
    val normalized = normalizeAvroType(avroType)
    normalized match {
      case "INTEGER"     => ("Int", "Int")
      case "BIGINT"      => ("Long", "Long")
      case "REAL"        => ("Float", "Float")
      case "DOUBLE"      => ("Double", "Double")
      case "BOOLEAN"     => ("Boolean", "Boolean")
      case "VARCHAR"     => ("String", "String")
      case "DECIMAL"     => ("BigDecimal", "BigDecimal")
      case "TIMESTAMP"   => ("LocalDateTime", "LocalDateTime")
      case "TIMESTAMPTZ" => ("Instant", "Instant")
      case "DATE"        => ("LocalDate", "LocalDate")
      case "TIME"        => ("LocalTime", "LocalTime")
      case "UUID"        => ("UUID", "UUID")
      case "BYTEA"       => ("ByteArray", "Array[Byte]")
      case "ARRAY"       => ("List", "List[_]")
      case "JSON"        => ("Json", "Json")
      case "INTERVAL"    => ("Duration", "java.time.Duration")
      case "NULL"        => ("Unit", "Unit")
      case other         => (other, "Any")
    }
  }

  /** Check if an Avro type is compatible with a domain/database type */
  def isAvroTypeCompatible(avroType: String, domainType: String): Boolean = {
    val normAvro = normalizeAvroType(avroType)
    val normDomain = normalizeDbType(domainType)

    if (normAvro == normDomain) true
    else {
      val integerFamily = Set("INTEGER", "BIGINT", "SMALLINT")
      val floatFamily = Set("REAL", "DOUBLE", "DECIMAL")
      val timestampFamily = Set("TIMESTAMP", "TIMESTAMPTZ")
      val timeFamily = Set("TIME", "TIMETZ")
      val stringFamily = Set("VARCHAR", "UUID")

      (integerFamily.contains(normAvro) && integerFamily.contains(normDomain)) ||
      (floatFamily.contains(normAvro) && floatFamily.contains(normDomain)) ||
      (timestampFamily.contains(normAvro) && timestampFamily.contains(normDomain)) ||
      (timeFamily.contains(normAvro) && timeFamily.contains(normDomain)) ||
      (stringFamily.contains(normAvro) && stringFamily.contains(normDomain))
    }
  }

  /** Check if there's a narrowing conversion needed (Avro to Domain) */
  def isAvroNarrowing(avroType: String, domainType: String): Boolean = {
    val normAvro = normalizeAvroType(avroType)
    val normDomain = normalizeDbType(domainType)

    if (normAvro == normDomain) false
    else {
      val narrowings = Set(
        ("BIGINT", "INTEGER"),
        ("BIGINT", "SMALLINT"),
        ("INTEGER", "SMALLINT"),
        ("DOUBLE", "REAL"),
        ("DECIMAL", "DOUBLE"),
        ("DECIMAL", "REAL"),
        ("TIMESTAMPTZ", "TIMESTAMP"),
        ("VARCHAR", "UUID")
      )
      narrowings.contains((normAvro, normDomain))
    }
  }

  /** Check if there's a widening conversion needed (Domain to Avro) */
  def isAvroWidening(domainType: String, avroType: String): Boolean = {
    isAvroNarrowing(avroType, domainType)
  }

  /** Create AvroTypeInfo for use in type analysis */
  case class AvroTypeInfo(
      sourceName: String,
      avroType: String,
      nullable: Boolean
  )

  /** Find canonical type considering Avro sources */
  def findCanonicalTypeWithAvro(dbTypes: List[DbTypeInfo], avroTypes: List[AvroTypeInfo]): CanonicalTypeResult = {
    if (dbTypes.isEmpty && avroTypes.isEmpty) {
      return CanonicalTypeResult(
        canonicalType = "String",
        jvmType = "String",
        nullable = true,
        warnings = List("No source types provided"),
        comment = "default"
      )
    }

    if (dbTypes.isEmpty && avroTypes.size == 1) {
      val avro = avroTypes.head
      val (canonical, jvm) = mapAvroTypeToCanonical(avro.avroType)
      return CanonicalTypeResult(
        canonicalType = canonical,
        jvmType = jvm,
        nullable = avro.nullable,
        warnings = Nil,
        comment = "exact match (Avro)"
      )
    }

    if (avroTypes.isEmpty) {
      return findCanonicalType(dbTypes)
    }

    val warnings = List.newBuilder[String]
    val allNullable = dbTypes.exists(_.nullable) || avroTypes.exists(_.nullable)

    if (allNullable && (dbTypes.exists(!_.nullable) || avroTypes.exists(!_.nullable))) {
      warnings += "Nullability differs across sources - using nullable"
    }

    val dbNormalized = dbTypes.map(t => normalizeDbType(t.dbType)).toSet
    val avroNormalized = avroTypes.map(t => normalizeAvroType(t.avroType)).toSet
    val allNormalized = (dbNormalized ++ avroNormalized).toList.sorted

    if (allNormalized.size == 1) {
      val (canonical, jvm) = if (dbTypes.nonEmpty) {
        mapDbTypeToCanonical(dbTypes.head.dbType)
      } else {
        mapAvroTypeToCanonical(avroTypes.head.avroType)
      }
      return CanonicalTypeResult(
        canonicalType = canonical,
        jvmType = jvm,
        nullable = allNullable,
        warnings = warnings.result(),
        comment = "exact match"
      )
    }

    findWidestType(allNormalized) match {
      case Some((canonical, jvm, widenComment)) =>
        val dbInfo = dbTypes.map(t => s"${t.sourceName}:${t.dbType}")
        val avroInfo = avroTypes.map(t => s"${t.sourceName}(avro):${t.avroType}")
        val sourceInfo = (dbInfo ++ avroInfo).mkString(", ")
        warnings += s"$widenComment ($sourceInfo)"
        CanonicalTypeResult(
          canonicalType = canonical,
          jvmType = jvm,
          nullable = allNullable,
          warnings = warnings.result(),
          comment = widenComment
        )

      case None =>
        val dbInfo = dbTypes.map(t => s"${t.sourceName}:${t.dbType}")
        val avroInfo = avroTypes.map(t => s"${t.sourceName}(avro):${t.avroType}")
        val sourceInfo = (dbInfo ++ avroInfo).mkString(", ")
        CanonicalTypeResult(
          canonicalType = "String",
          jvmType = "String",
          nullable = true,
          warnings = warnings.result() :+ s"Incompatible types ($sourceInfo) - defaulting to String",
          comment = s"incompatible: ${allNormalized.mkString(", ")}"
        )
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════════
  // Proto Type Support
  // ═══════════════════════════════════════════════════════════════════════════════

  /** Normalize a Protobuf type string for comparison with domain/database types */
  def normalizeProtoType(protoType: String): String = {
    val lower = protoType.toLowerCase.trim
    lower match {
      case "int32" | "sint32" | "sfixed32"           => "INTEGER"
      case "int64" | "sint64" | "sfixed64"           => "BIGINT"
      case "uint32" | "fixed32"                      => "INTEGER"
      case "uint64" | "fixed64"                      => "BIGINT"
      case "float"                                   => "REAL"
      case "double"                                  => "DOUBLE"
      case "bool"                                    => "BOOLEAN"
      case "string"                                  => "VARCHAR"
      case "bytes"                                   => "BYTEA"
      case "timestamp"                               => "TIMESTAMPTZ"
      case "duration"                                => "INTERVAL"
      case "stringvalue"                             => "VARCHAR"
      case "int32value"                              => "INTEGER"
      case "int64value"                              => "BIGINT"
      case "uint32value"                             => "INTEGER"
      case "uint64value"                             => "BIGINT"
      case "floatvalue"                              => "REAL"
      case "doublevalue"                             => "DOUBLE"
      case "boolvalue"                               => "BOOLEAN"
      case "bytesvalue"                              => "BYTEA"
      case "any"                                     => "JSON"
      case "struct"                                  => "JSON"
      case "empty"                                   => "NULL"
      case s if s.endsWith("?")                      => normalizeProtoType(s.dropRight(1))
      case s if s.startsWith("[") && s.endsWith("]") => "ARRAY"
      case s if s.startsWith("map<")                 => "JSON"
      case other                                     => other.toUpperCase
    }
  }

  /** Map a Protobuf type to canonical/JVM types */
  def mapProtoTypeToCanonical(protoType: String): (String, String) = {
    val normalized = normalizeProtoType(protoType)
    normalized match {
      case "INTEGER"     => ("Int", "Int")
      case "BIGINT"      => ("Long", "Long")
      case "REAL"        => ("Float", "Float")
      case "DOUBLE"      => ("Double", "Double")
      case "BOOLEAN"     => ("Boolean", "Boolean")
      case "VARCHAR"     => ("String", "String")
      case "DECIMAL"     => ("BigDecimal", "BigDecimal")
      case "TIMESTAMP"   => ("LocalDateTime", "LocalDateTime")
      case "TIMESTAMPTZ" => ("Instant", "Instant")
      case "DATE"        => ("LocalDate", "LocalDate")
      case "TIME"        => ("LocalTime", "LocalTime")
      case "UUID"        => ("UUID", "UUID")
      case "BYTEA"       => ("ByteArray", "Array[Byte]")
      case "ARRAY"       => ("List", "List[_]")
      case "JSON"        => ("Json", "Json")
      case "INTERVAL"    => ("Duration", "java.time.Duration")
      case "NULL"        => ("Unit", "Unit")
      case other         => (other, "Any")
    }
  }

  /** Check if a Protobuf type is compatible with a domain/database type */
  def isProtoTypeCompatible(protoType: String, domainType: String): Boolean = {
    val normProto = normalizeProtoType(protoType)
    val normDomain = normalizeDbType(domainType)

    if (normProto == normDomain) true
    else {
      val integerFamily = Set("INTEGER", "BIGINT", "SMALLINT")
      val floatFamily = Set("REAL", "DOUBLE", "DECIMAL")
      val timestampFamily = Set("TIMESTAMP", "TIMESTAMPTZ")
      val timeFamily = Set("TIME", "TIMETZ")
      val stringFamily = Set("VARCHAR", "UUID")

      (integerFamily.contains(normProto) && integerFamily.contains(normDomain)) ||
      (floatFamily.contains(normProto) && floatFamily.contains(normDomain)) ||
      (timestampFamily.contains(normProto) && timestampFamily.contains(normDomain)) ||
      (timeFamily.contains(normProto) && timeFamily.contains(normDomain)) ||
      (stringFamily.contains(normProto) && stringFamily.contains(normDomain))
    }
  }

  /** Check if there's a narrowing conversion needed (Proto to Domain) */
  def isProtoNarrowing(protoType: String, domainType: String): Boolean = {
    val normProto = normalizeProtoType(protoType)
    val normDomain = normalizeDbType(domainType)

    if (normProto == normDomain) false
    else {
      val narrowings = Set(
        ("BIGINT", "INTEGER"),
        ("BIGINT", "SMALLINT"),
        ("INTEGER", "SMALLINT"),
        ("DOUBLE", "REAL"),
        ("DECIMAL", "DOUBLE"),
        ("DECIMAL", "REAL"),
        ("TIMESTAMPTZ", "TIMESTAMP"),
        ("VARCHAR", "UUID")
      )
      narrowings.contains((normProto, normDomain))
    }
  }

  /** Check if there's a widening conversion needed (Domain to Proto) */
  def isProtoWidening(domainType: String, protoType: String): Boolean = {
    isProtoNarrowing(protoType, domainType)
  }

  /** Create ProtoTypeInfo for use in type analysis */
  case class ProtoTypeInfo(
      sourceName: String,
      protoType: String,
      nullable: Boolean
  )

  /** Find canonical type considering Proto sources */
  def findCanonicalTypeWithProto(dbTypes: List[DbTypeInfo], protoTypes: List[ProtoTypeInfo]): CanonicalTypeResult = {
    if (dbTypes.isEmpty && protoTypes.isEmpty) {
      return CanonicalTypeResult(
        canonicalType = "String",
        jvmType = "String",
        nullable = true,
        warnings = List("No source types provided"),
        comment = "default"
      )
    }

    if (dbTypes.isEmpty && protoTypes.size == 1) {
      val proto = protoTypes.head
      val (canonical, jvm) = mapProtoTypeToCanonical(proto.protoType)
      return CanonicalTypeResult(
        canonicalType = canonical,
        jvmType = jvm,
        nullable = proto.nullable,
        warnings = Nil,
        comment = "exact match (Proto)"
      )
    }

    if (protoTypes.isEmpty) {
      return findCanonicalType(dbTypes)
    }

    val warnings = List.newBuilder[String]
    val allNullable = dbTypes.exists(_.nullable) || protoTypes.exists(_.nullable)

    if (allNullable && (dbTypes.exists(!_.nullable) || protoTypes.exists(!_.nullable))) {
      warnings += "Nullability differs across sources - using nullable"
    }

    val dbNormalized = dbTypes.map(t => normalizeDbType(t.dbType)).toSet
    val protoNormalized = protoTypes.map(t => normalizeProtoType(t.protoType)).toSet
    val allNormalized = (dbNormalized ++ protoNormalized).toList.sorted

    if (allNormalized.size == 1) {
      val (canonical, jvm) = if (dbTypes.nonEmpty) {
        mapDbTypeToCanonical(dbTypes.head.dbType)
      } else {
        mapProtoTypeToCanonical(protoTypes.head.protoType)
      }
      return CanonicalTypeResult(
        canonicalType = canonical,
        jvmType = jvm,
        nullable = allNullable,
        warnings = warnings.result(),
        comment = "exact match"
      )
    }

    findWidestType(allNormalized) match {
      case Some((canonical, jvm, widenComment)) =>
        val dbInfo = dbTypes.map(t => s"${t.sourceName}:${t.dbType}")
        val protoInfo = protoTypes.map(t => s"${t.sourceName}(proto):${t.protoType}")
        val sourceInfo = (dbInfo ++ protoInfo).mkString(", ")
        warnings += s"$widenComment ($sourceInfo)"
        CanonicalTypeResult(
          canonicalType = canonical,
          jvmType = jvm,
          nullable = allNullable,
          warnings = warnings.result(),
          comment = widenComment
        )

      case None =>
        val dbInfo = dbTypes.map(t => s"${t.sourceName}:${t.dbType}")
        val protoInfo = protoTypes.map(t => s"${t.sourceName}(proto):${t.protoType}")
        val sourceInfo = (dbInfo ++ protoInfo).mkString(", ")
        CanonicalTypeResult(
          canonicalType = "String",
          jvmType = "String",
          nullable = true,
          warnings = warnings.result() :+ s"Incompatible types ($sourceInfo) - defaulting to String",
          comment = s"incompatible: ${allNormalized.mkString(", ")}"
        )
    }
  }
}
