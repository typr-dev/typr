package typr.config.generated

import io.circe.Decoder
import io.circe.Encoder

/** Pattern-based matching for schema/model properties (OpenAPI, JSON Schema, Avro) */
case class ModelMatch(
    /** Match x-* extension values */
    `extension`: Option[Map[String, String]],
    format: Option[StringOrArray],
    json_path: Option[StringOrArray],
    name: Option[StringOrArray],
    /** Match required status */
    required: Option[Boolean],
    schema: Option[StringOrArray],
    schema_type: Option[StringOrArray],
    source: Option[StringOrArray]
)

object ModelMatch {
  implicit val decoder: Decoder[ModelMatch] = io.circe.generic.semiauto.deriveDecoder[typr.config.generated.ModelMatch]

  implicit val encoder: Encoder[ModelMatch] = io.circe.generic.semiauto.deriveEncoder[typr.config.generated.ModelMatch]
}
