package testapi.model

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotNull
import java.time.OffsetDateTime
import java.util.Optional
import kotlin.collections.List
import kotlin.collections.Map

data class Pet(
  @field:JsonProperty("tags") val tags: Optional<List<String>>,
  @field:JsonProperty("id") @NotNull val id: String,
  @field:JsonProperty("status") @NotNull val status: PetStatus,
  @field:JsonProperty("createdAt") @NotNull val createdAt: OffsetDateTime,
  @field:JsonProperty("metadata") val metadata: Optional<Map<String, String>>,
  /** Pet name */
  @field:JsonProperty("name") @NotNull val name: String,
  @field:JsonProperty("updatedAt") val updatedAt: Optional<OffsetDateTime>
)