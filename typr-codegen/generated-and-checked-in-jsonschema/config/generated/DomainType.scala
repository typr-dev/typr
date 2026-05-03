package typr.config.generated

import io.circe.Decoder
import io.circe.Encoder

/** A domain type - grounded in a primary boundary entity, with optional alignment to other boundaries */
case class DomainType(
    /** Other boundary entities that align with this domain type. Keys are 'boundaryName:entityPath' (e.g., 'mariadb:customers', 'api:Customer') */
    alignedSources: Option[Map[String, AlignedSource]],
    /** Human-readable description of this type */
    description: Option[String],
    /** Named fields of this domain type (selected from primary boundary). Keys are field names, values are types. */
    fields: Map[String, FieldSpec],
    generate: Option[DomainGenerateOptions],
    /** The primary boundary entity this domain type is based on. Format: 'boundaryName:entityPath' (e.g., 'mydb:sales.customer') */
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
