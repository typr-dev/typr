package typr.config.generated

import io.circe.Decoder
import io.circe.Encoder

/** OpenAPI boundary for REST API types */
case class OpenapiBoundary(
    /** Path or URL to OpenAPI spec */
    spec: Option[String],
    /** Multiple OpenAPI spec paths (glob patterns supported) */
    specs: Option[List[String]],
    `type`: Option[String],
    /** Generate model classes from schemas */
    generate_models: Option[Boolean],
    /** Generate server interfaces */
    generate_server: Option[Boolean],
    /** Generate client implementations */
    generate_client: Option[Boolean],
    /** Boundary-level type definitions */
    types: Option[Map[String, FieldType]]
)

object OpenapiBoundary {
  implicit val decoder: Decoder[OpenapiBoundary] = io.circe.generic.semiauto.deriveDecoder[typr.config.generated.OpenapiBoundary]

  implicit val encoder: Encoder[OpenapiBoundary] = io.circe.generic.semiauto.deriveEncoder[typr.config.generated.OpenapiBoundary]
}
