package testapi.model

import java.time.OffsetDateTime

case class Cat(
  meowVolume: Option[Int],
  id: String,
  createdAt: OffsetDateTime,
  /** Whether the cat is an indoor cat */
  indoor: Boolean,
  name: String,
  updatedAt: Option[OffsetDateTime]
) extends Animal {
  override lazy val animal_type: String = "cat"
}