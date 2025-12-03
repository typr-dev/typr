package testapi.model

import io.circe.Decoder
import io.circe.Encoder

case class OptionalInfo(
    optional_field: Option[String],
    optional_with_default: Option[String],
    required_field: Option[String]
)

object OptionalInfo {
  implicit val decoder: Decoder[OptionalInfo] = io.circe.generic.semiauto.deriveDecoder[testapi.model.OptionalInfo]

  implicit val encoder: Encoder[OptionalInfo] = io.circe.generic.semiauto.deriveEncoder[testapi.model.OptionalInfo]
}
