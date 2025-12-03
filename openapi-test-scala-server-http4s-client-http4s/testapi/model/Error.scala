package testapi.model

import io.circe.Decoder
import io.circe.Encoder
import io.circe.Json

case class Error(
    code: String,
    details: Option[Map[String, Json]],
    message: String
)

object Error {
  implicit val decoder: Decoder[Error] = io.circe.generic.semiauto.deriveDecoder[testapi.model.Error]

  implicit val encoder: Encoder[Error] = io.circe.generic.semiauto.deriveEncoder[testapi.model.Error]
}
