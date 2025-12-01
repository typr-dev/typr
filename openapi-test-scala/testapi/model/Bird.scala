package testapi.model

import java.time.OffsetDateTime

case class Bird(
  id: String,
  createdAt: OffsetDateTime,
  name: String,
  updatedAt: Option[OffsetDateTime],
  wingSpan: Option[Double],
  canFly: Boolean
) extends Animal {
  override lazy val animal_type: String = "bird"
}