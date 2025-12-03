package testapi.model

import io.circe.Decoder
import io.circe.Encoder

/** Email address wrapper */
case class Email(value: String) extends AnyVal

object Email {
  implicit val decoder: Decoder[Email] = io.circe.Decoder[java.lang.String].map(Email.apply)

  implicit val encoder: Encoder[Email] = io.circe.Encoder[java.lang.String].contramap(_.value)
}