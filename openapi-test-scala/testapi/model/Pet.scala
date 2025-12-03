package testapi.model

import io.circe.Decoder
import io.circe.Encoder
import java.time.OffsetDateTime

case class Pet(
  tags: Option[List[String]],
  id: String,
  status: PetStatus,
  createdAt: OffsetDateTime,
  metadata: Option[Map[String, String]],
  /** Pet name */
  name: String,
  updatedAt: Option[OffsetDateTime]
)

object Pet {
  implicit val decoder: Decoder[Pet] = io.circe.generic.semiauto.deriveDecoder[testapi.model.Pet]

  implicit val encoder: Encoder[Pet] = io.circe.generic.semiauto.deriveEncoder[testapi.model.Pet]
}