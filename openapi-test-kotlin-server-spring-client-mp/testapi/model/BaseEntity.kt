package testapi.model

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotNull
import java.time.OffsetDateTime
import java.util.Optional

data class BaseEntity(
  @field:JsonProperty("createdAt") @NotNull val createdAt: OffsetDateTime,
  @field:JsonProperty("id") @NotNull val id: String,
  @field:JsonProperty("updatedAt") val updatedAt: Optional<OffsetDateTime>
)