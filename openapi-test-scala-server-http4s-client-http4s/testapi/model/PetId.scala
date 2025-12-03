package testapi.model

import io.circe.Decoder
import io.circe.Encoder

/** Unique pet identifier */
case class PetId(value: String) extends AnyVal

object PetId {
  implicit val decoder: Decoder[PetId] = io.circe.Decoder[java.lang.String].map(PetId.apply)

  implicit val encoder: Encoder[PetId] = io.circe.Encoder[java.lang.String].contramap(_.value)
}
