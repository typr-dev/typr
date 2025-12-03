package testapi.model

import io.circe.Decoder
import io.circe.Encoder

/** ISO 4217 currency code */
case class Currency(value: String) extends AnyVal

object Currency {
  implicit val decoder: Decoder[Currency] = io.circe.Decoder[java.lang.String].map(Currency.apply)

  implicit val encoder: Encoder[Currency] = io.circe.Encoder[java.lang.String].contramap(_.value)
}