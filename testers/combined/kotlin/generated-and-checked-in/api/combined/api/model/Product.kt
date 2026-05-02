package combined.api.model

import com.fasterxml.jackson.annotation.JsonProperty

data class Product(
  /** Product description */
  @field:JsonProperty("description") val description: kotlin.String?,
  /** Whether product is active/visible (matches IsActive type) */
  @field:JsonProperty("isActive") val isActive: kotlin.Boolean,
  /** Product name */
  @field:JsonProperty("name") val name: kotlin.String,
  /** Product price */
  @field:JsonProperty("price") val price: kotlin.Double?,
  /** Product ID (prefixed with source, e.g., "pg-123" or "maria-456") */
  @field:JsonProperty("productId") val productId: kotlin.String,
  /** Which database this product comes from */
  @field:JsonProperty("source") val source: kotlin.String
)