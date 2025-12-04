package testapi.model

import io.circe.Decoder
import io.circe.Encoder
import java.time.OffsetDateTime

case class Cat(
  meowVolume: Option[Int],
  id: PetId,
  createdAt: OffsetDateTime,
  /** Whether the cat is an indoor cat */
  indoor: Boolean,
  name: String,
  updatedAt: Option[OffsetDateTime]
) extends Animal {
  override lazy val animal_type: String = "cat"
}

object Cat {
  implicit val decoder: Decoder[Cat] = io.circe.generic.semiauto.deriveDecoder[testapi.model.Cat]

  implicit val encoder: Encoder[Cat] = io.circe.generic.semiauto.deriveEncoder[testapi.model.Cat]
}