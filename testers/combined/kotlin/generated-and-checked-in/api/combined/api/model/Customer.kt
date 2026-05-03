package combined.api.model

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.OffsetDateTime

data class Customer(
  /** When the customer was created */
  @field:JsonProperty("createdAt") val createdAt: OffsetDateTime?,
  /** Customer ID */
  @field:JsonProperty("customerId") val customerId: kotlin.Long,
  /** Customer's email address (matches Email type) */
  @field:JsonProperty("email") val email: kotlin.String,
  /** Customer's first name (matches FirstName type) */
  @field:JsonProperty("firstName") val firstName: kotlin.String,
  /** Whether customer account is active (matches IsActive type) */
  @field:JsonProperty("isActive") val isActive: kotlin.Boolean,
  /** Customer's last name (matches LastName type) */
  @field:JsonProperty("lastName") val lastName: kotlin.String,
  /** Customer loyalty tier */
  @field:JsonProperty("tier") val tier: kotlin.String?
)