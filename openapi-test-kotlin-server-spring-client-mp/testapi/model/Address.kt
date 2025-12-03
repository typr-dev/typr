package testapi.model

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.Optional

data class Address(
  @field:JsonProperty("city") val city: Optional<String>,
  @field:JsonProperty("country") val country: Optional<String>,
  @field:JsonProperty("street") val street: Optional<String>,
  @field:JsonProperty("zipCode") val zipCode: Optional<String>
)