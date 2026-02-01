package typr.config.generated

import io.circe.Decoder
import io.circe.Encoder

case class JsonschemaSource(
    /** Path or URL to JSON Schema file */
    spec: Option[String],
    /** Multiple JSON Schema paths (glob patterns supported) */
    specs: Option[List[String]],
    `type`: Option[String],
    /** Source-level type definitions */
    types: Option[Map[String, FieldType]]
)

object JsonschemaSource {
  implicit val decoder: Decoder[JsonschemaSource] = io.circe.generic.semiauto.deriveDecoder[typr.config.generated.JsonschemaSource]

  implicit val encoder: Encoder[JsonschemaSource] = io.circe.generic.semiauto.deriveEncoder[typr.config.generated.JsonschemaSource]
}
