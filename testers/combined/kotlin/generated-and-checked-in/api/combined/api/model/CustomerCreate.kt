package combined.api.model

import com.fasterxml.jackson.annotation.JsonProperty

data class CustomerCreate(
  /** Customer's email address (matches Email type) */
  @field:JsonProperty("email") val email: kotlin.String,
  /** Customer's first name (matches FirstName type) */
  @field:JsonProperty("firstName") val firstName: kotlin.String,
  /** Customer's last name (matches LastName type) */
  @field:JsonProperty("lastName") val lastName: kotlin.String,
  @field:JsonProperty("tier") val tier: kotlin.String?
)