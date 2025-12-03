package testapi.model

import io.circe.Decoder
import io.circe.Encoder

case class Address(
  city: Option[String],
  country: Option[String],
  street: Option[String],
  zipCode: Option[String]
)

object Address {
  implicit val decoder: Decoder[Address] = io.circe.generic.semiauto.deriveDecoder[testapi.model.Address]

  implicit val encoder: Encoder[Address] = io.circe.generic.semiauto.deriveEncoder[testapi.model.Address]
}