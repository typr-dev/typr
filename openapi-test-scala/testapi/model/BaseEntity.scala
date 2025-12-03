package testapi.model

import io.circe.Decoder
import io.circe.Encoder
import java.time.OffsetDateTime

case class BaseEntity(
  createdAt: OffsetDateTime,
  id: String,
  updatedAt: Option[OffsetDateTime]
)

object BaseEntity {
  implicit val decoder: Decoder[BaseEntity] = io.circe.generic.semiauto.deriveDecoder[testapi.model.BaseEntity]

  implicit val encoder: Encoder[BaseEntity] = io.circe.generic.semiauto.deriveEncoder[testapi.model.BaseEntity]
}