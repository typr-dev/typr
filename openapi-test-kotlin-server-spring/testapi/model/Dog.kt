package testapi.model

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotNull
import java.time.OffsetDateTime
import java.util.Optional

data class Dog(
  @field:JsonProperty("id") @NotNull val id: PetId,
  @field:JsonProperty("name") @NotNull val name: String,
  @field:JsonProperty("updatedAt") val updatedAt: Optional<OffsetDateTime>,
  @field:JsonProperty("breed") @NotNull val breed: String,
  @field:JsonProperty("createdAt") @NotNull val createdAt: OffsetDateTime,
  @field:JsonProperty("barkVolume") val barkVolume: Optional<Integer>
) : Animal {
  override fun animal_type(): String = "dog"
}