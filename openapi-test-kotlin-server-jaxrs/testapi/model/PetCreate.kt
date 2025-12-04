package testapi.model

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.util.Optional
import kotlin.collections.List

data class PetCreate(
  @field:JsonProperty("age") @Min(0L) @Max(100L) val age: Optional<Long>,
  @field:JsonProperty("email") @jakarta.validation.constraints.Email val email: Optional<String>,
  @field:JsonProperty("name") @NotNull @Size(min = 1, max = 100) val name: String,
  @field:JsonProperty("status") val status: Optional<PetStatus>,
  @field:JsonProperty("tags") @Size(min = 0, max = 10) val tags: Optional<List<String>>,
  @field:JsonProperty("website") @Pattern(regexp = "^https?://.*") val website: Optional<String>
)