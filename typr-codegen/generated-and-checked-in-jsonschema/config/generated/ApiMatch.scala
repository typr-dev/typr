package typr.config.generated

import io.circe.Decoder
import io.circe.Encoder

/** Pattern-based matching for OpenAPI parameters (path, query, header, cookie) */
case class ApiMatch(
    /** Match x-* extension values */
    `extension`: Option[Map[String, String]],
    http_method: Option[StringOrArray],
    /** Match parameter location */
    location: Option[List[String]],
    name: Option[StringOrArray],
    operation_id: Option[StringOrArray],
    path: Option[StringOrArray],
    /** Match required status */
    required: Option[Boolean],
    source: Option[StringOrArray]
)

object ApiMatch {
  implicit val decoder: Decoder[ApiMatch] = io.circe.generic.semiauto.deriveDecoder[typr.config.generated.ApiMatch]

  implicit val encoder: Encoder[ApiMatch] = io.circe.generic.semiauto.deriveEncoder[typr.config.generated.ApiMatch]
}
