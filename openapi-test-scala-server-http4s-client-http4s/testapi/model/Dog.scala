package testapi.model

import java.time.OffsetDateTime

case class Dog(
  id: String,
  name: String,
  updatedAt: Option[OffsetDateTime],
  breed: String,
  createdAt: OffsetDateTime,
  barkVolume: Option[Int]
) extends Animal {
  override lazy val animal_type: String = "dog"
}