package typr.config.generated

import io.circe.Decoder
import io.circe.Encoder

case class JsonschemaBoundary(
    /** Path or URL to JSON Schema file */
    spec: Option[String],
    /** Multiple JSON Schema paths (glob patterns supported) */
    specs: Option[List[String]],
    `type`: Option[String],
    /** Boundary-level type definitions */
    types: Option[Map[String, FieldType]]
)

object JsonschemaBoundary {
  implicit val decoder: Decoder[JsonschemaBoundary] = io.circe.generic.semiauto.deriveDecoder[typr.config.generated.JsonschemaBoundary]

  implicit val encoder: Encoder[JsonschemaBoundary] = io.circe.generic.semiauto.deriveEncoder[typr.config.generated.JsonschemaBoundary]
}
