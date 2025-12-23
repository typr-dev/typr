package typr.openapi

/** Represents types extracted from OpenAPI schemas. These are intermediate representations that get mapped to jvm.Type during code generation.
  */
sealed trait TypeInfo

object TypeInfo {

  /** Primitive types with their OpenAPI format */
  case class Primitive(primitiveType: PrimitiveType) extends TypeInfo

  /** List/array type */
  case class ListOf(itemType: TypeInfo) extends TypeInfo

  /** Optional type (wraps nullable or non-required fields) */
  case class Optional(underlying: TypeInfo) extends TypeInfo

  /** Map type (from additionalProperties) */
  case class MapOf(keyType: TypeInfo, valueType: TypeInfo) extends TypeInfo

  /** Reference to a named type (schema $ref) */
  case class Ref(name: String) extends TypeInfo

  /** Any/unknown type (for untyped schemas) */
  case object Any extends TypeInfo

  /** Enum type defined inline */
  case class InlineEnum(values: List[String]) extends TypeInfo
}

/** OpenAPI primitive types with format information */
sealed trait PrimitiveType

object PrimitiveType {
  case object String extends PrimitiveType
  case object Int32 extends PrimitiveType
  case object Int64 extends PrimitiveType
  case object Float extends PrimitiveType
  case object Double extends PrimitiveType
  case object Boolean extends PrimitiveType

  // String formats
  case object Date extends PrimitiveType // format: date -> LocalDate
  case object DateTime extends PrimitiveType // format: date-time -> OffsetDateTime
  case object Time extends PrimitiveType // format: time -> LocalTime
  case object UUID extends PrimitiveType // format: uuid -> UUID
  case object URI extends PrimitiveType // format: uri -> URI
  case object Email extends PrimitiveType // format: email -> String (with validation)
  case object Binary extends PrimitiveType // format: binary -> Array[Byte]
  case object Byte extends PrimitiveType // format: byte -> Base64 encoded String

  // Number formats
  case object BigDecimal extends PrimitiveType // format: decimal
}
