package testapi.model

import io.circe.Decoder
import io.circe.Encoder

case class Money(
    amount: Double,
    currency: Currency
)

object Money {
  implicit val decoder: Decoder[Money] = io.circe.generic.semiauto.deriveDecoder[testapi.model.Money]

  implicit val encoder: Encoder[Money] = io.circe.generic.semiauto.deriveEncoder[testapi.model.Money]
}
