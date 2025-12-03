package testapi.model

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotNull
import java.util.Optional

data class Owner(
  @field:JsonProperty("address") val address: Optional<Address>,
  @field:JsonProperty("email") @jakarta.validation.constraints.Email val email: Optional<String>,
  @field:JsonProperty("id") @NotNull val id: String,
  @field:JsonProperty("name") @NotNull val name: String
)