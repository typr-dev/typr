package testapi.model

import io.circe.Decoder
import io.circe.Encoder
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

object Bird {
  implicit val decoder: Decoder[Bird] = io.circe.generic.semiauto.deriveDecoder[testapi.model.Bird]

  implicit val encoder: Encoder[Bird] = io.circe.generic.semiauto.deriveEncoder[testapi.model.Bird]
}