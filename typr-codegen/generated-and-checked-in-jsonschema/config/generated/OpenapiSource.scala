package typr.config.generated

import io.circe.Decoder
import io.circe.Encoder

case class OpenapiSource(
    /** Path or URL to OpenAPI spec */
    spec: Option[String],
    /** Multiple OpenAPI spec paths (glob patterns supported) */
    specs: Option[List[String]],
    `type`: Option[String],
    /** Source-level type definitions */
    types: Option[Map[String, FieldType]]
)

object OpenapiSource {
  implicit val decoder: Decoder[OpenapiSource] = io.circe.generic.semiauto.deriveDecoder[typr.config.generated.OpenapiSource]

  implicit val encoder: Encoder[OpenapiSource] = io.circe.generic.semiauto.deriveEncoder[typr.config.generated.OpenapiSource]
}
