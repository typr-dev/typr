package typr.openapi

/** Represents a discriminated union type (oneOf with discriminator) - maps to sealed interface */
case class SumType(
    name: String,
    description: Option[String],
    discriminator: Discriminator,
    /** Common properties shared by all subtypes (from allOf flattening) */
    commonProperties: List[Property],
    /** Names of the subtypes (references to ModelClass or nested SumType) */
    subtypeNames: List[String],
    /** Parent sum types if this is a nested discriminated union */
    parentSumTypes: List[String]
)

/** Discriminator configuration for a sum type */
case class Discriminator(
    /** Property name used for discrimination */
    propertyName: String,
    /** Explicit mapping from discriminator value to type name. If empty, uses type name as discriminator value. */
    mapping: Map[String, String]
)
