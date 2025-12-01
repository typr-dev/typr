package testapi.model

import java.time.OffsetDateTime

case class BaseEntity(
  createdAt: OffsetDateTime,
  id: String,
  updatedAt: Option[OffsetDateTime]
)