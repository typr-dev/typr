package typr

/** A composite Bridge type definition used for code generation. This is the internal representation extracted from config.
  */
case class BridgeCompositeType(
    name: String,
    description: Option[String],
    fields: List[BridgeCompositeField],
    projections: Map[String, BridgeProjection],
    generateCanonical: Boolean,
    generateMappers: Boolean,
    generateInterface: Boolean,
    generateBuilder: Boolean,
    generateCopy: Boolean
)

case class BridgeCompositeField(
    name: String,
    typeName: String,
    nullable: Boolean,
    array: Boolean,
    description: Option[String]
)

case class BridgeProjection(
    sourceName: String,
    entityPath: String,
    mode: BridgeProjectionMode,
    mappings: Map[String, String],
    exclude: Set[String],
    includeExtra: List[String],
    readonly: Boolean
)

sealed trait BridgeProjectionMode
object BridgeProjectionMode {
  case object Exact extends BridgeProjectionMode
  case object Superset extends BridgeProjectionMode
  case object Subset extends BridgeProjectionMode

  def fromString(s: String): BridgeProjectionMode = s.toLowerCase match {
    case "exact"    => Exact
    case "subset"   => Subset
    case "superset" => Superset
    case _          => Superset
  }
}
