package testapi.model

import io.circe.Decoder
import io.circe.Encoder

case class PetCreate(
    age: Option[Long],
    email: Option[String],
    name: String,
    status: Option[PetStatus],
    tags: Option[List[String]],
    website: Option[String]
)

object PetCreate {
  implicit val decoder: Decoder[PetCreate] = io.circe.generic.semiauto.deriveDecoder[testapi.model.PetCreate]

  implicit val encoder: Encoder[PetCreate] = io.circe.generic.semiauto.deriveEncoder[testapi.model.PetCreate]
}
