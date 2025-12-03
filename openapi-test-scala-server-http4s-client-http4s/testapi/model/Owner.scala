package testapi.model

import io.circe.Decoder
import io.circe.Encoder

case class Owner(
    address: Option[Address],
    email: Option[String],
    id: String,
    name: String
)

object Owner {
  implicit val decoder: Decoder[Owner] = io.circe.generic.semiauto.deriveDecoder[testapi.model.Owner]

  implicit val encoder: Encoder[Owner] = io.circe.generic.semiauto.deriveEncoder[testapi.model.Owner]
}
