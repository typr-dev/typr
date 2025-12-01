package testapi.model

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