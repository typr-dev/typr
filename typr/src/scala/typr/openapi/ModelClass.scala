package typr.openapi

/** Represents different kinds of model types extracted from OpenAPI schemas */
sealed trait ModelClass {
  def name: String
  def description: Option[String]
}

object ModelClass {

  /** Object type with properties - maps to case class / record */
  case class ObjectType(
      name: String,
      description: Option[String],
      properties: List[Property],
      /** Parent sum types this object belongs to (for discriminated unions) */
      sumTypeParents: List[String],
      /** Discriminator values this type uses to identify itself */
      discriminatorValues: Map[String, String]
  ) extends ModelClass

  /** Enum type - maps to sealed trait + case objects / Java enum */
  case class EnumType(
      name: String,
      description: Option[String],
      values: List[String],
      underlyingType: PrimitiveType
  ) extends ModelClass

  /** Wrapper/newtype - value class wrapping a primitive */
  case class WrapperType(
      name: String,
      description: Option[String],
      underlying: TypeInfo
  ) extends ModelClass

  /** Type alias - just a reference to another type */
  case class AliasType(
      name: String,
      description: Option[String],
      underlying: TypeInfo
  ) extends ModelClass
}

/** A property within an object type */
case class Property(
    name: String,
    originalName: String, // Original name from spec (before escaping)
    description: Option[String],
    typeInfo: TypeInfo,
    required: Boolean,
    nullable: Boolean,
    deprecated: Boolean,
    validation: ValidationConstraints
)

/** Validation constraints extracted from OpenAPI schema */
case class ValidationConstraints(
    minLength: Option[Int],
    maxLength: Option[Int],
    pattern: Option[String],
    minimum: Option[BigDecimal],
    maximum: Option[BigDecimal],
    exclusiveMinimum: Option[BigDecimal],
    exclusiveMaximum: Option[BigDecimal],
    minItems: Option[Int],
    maxItems: Option[Int],
    uniqueItems: Boolean,
    email: Boolean
)

object ValidationConstraints {
  val empty: ValidationConstraints = ValidationConstraints(
    minLength = None,
    maxLength = None,
    pattern = None,
    minimum = None,
    maximum = None,
    exclusiveMinimum = None,
    exclusiveMaximum = None,
    minItems = None,
    maxItems = None,
    uniqueItems = false,
    email = false
  )
}
