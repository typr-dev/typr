package testapi.model

import io.circe.Decoder
import io.circe.Encoder
import java.time.OffsetDateTime

case class Dog(
  id: PetId,
  name: String,
  updatedAt: Option[OffsetDateTime],
  breed: String,
  createdAt: OffsetDateTime,
  barkVolume: Option[Int]
) extends Animal {
  override lazy val animal_type: String = "dog"
}

object Dog {
  implicit val decoder: Decoder[Dog] = io.circe.generic.semiauto.deriveDecoder[testapi.model.Dog]

  implicit val encoder: Encoder[Dog] = io.circe.generic.semiauto.deriveEncoder[testapi.model.Dog]
}