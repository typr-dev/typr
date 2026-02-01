package typr.config.generated

import io.circe.Decoder
import io.circe.Encoder

/** A domain type - grounded in a primary source entity, with optional alignment to other sources */
case class DomainType(
    /** Other source entities that align with this domain type. Keys are 'sourceName:entityPath' (e.g., 'mariadb:customers', 'api:Customer') */
    alignedSources: Option[Map[String, AlignedSource]],
    /** Human-readable description of this type */
    description: Option[String],
    /** Named fields of this domain type (selected from primary source). Keys are field names, values are types. */
    fields: Map[String, FieldSpec],
    generate: Option[DomainGenerateOptions],
    /** The primary source entity this domain type is based on. Format: 'sourceName:entityPath' (e.g., 'mydb:sales.customer') */
    primary: Option[String],
    /** [DEPRECATED: use alignedSources] Legacy name for alignedSources */
    projections: Option[Map[String, AlignedSource]]
) extends BridgeType {
  override lazy val kind: String = "domain"
}

object DomainType {
  implicit val decoder: Decoder[DomainType] = io.circe.generic.semiauto.deriveDecoder[typr.config.generated.DomainType]

  implicit val encoder: Encoder[DomainType] = io.circe.generic.semiauto.deriveEncoder[typr.config.generated.DomainType]
}
