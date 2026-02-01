package typr.config.generated

import io.circe.Decoder
import io.circe.Encoder

/** Code generation options for domain types */
case class DomainGenerateOptions(
    /** Generate a builder for the domain type */
    builder: Option[Boolean],
    /** [DEPRECATED: use domainType] Legacy alias for domainType */
    canonical: Option[Boolean],
    /** Generate copy/with methods for immutable updates */
    copy: Option[Boolean],
    /** Generate the domain type record */
    domainType: Option[Boolean],
    /** Generate a shared interface that domain type and source types implement */
    interface: Option[Boolean],
    /** Generate mapper functions between domain type and aligned sources */
    mappers: Option[Boolean]
)

object DomainGenerateOptions {
  implicit val decoder: Decoder[DomainGenerateOptions] = io.circe.generic.semiauto.deriveDecoder[typr.config.generated.DomainGenerateOptions]

  implicit val encoder: Encoder[DomainGenerateOptions] = io.circe.generic.semiauto.deriveEncoder[typr.config.generated.DomainGenerateOptions]
}
