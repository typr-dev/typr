package com.example.events

import com.fasterxml.jackson.annotation.JsonProperty

/** Order with wrapper types for type-safe IDs */
data class CustomerOrder(
  /** Unique order identifier */
  @field:JsonProperty("orderId") val orderId: OrderId,
  /** Customer identifier */
  @field:JsonProperty("customerId") val customerId: CustomerId,
  /** Customer email address */
  @field:JsonProperty("email") val email: Email?,
  /** Order amount in cents (no wrapper) */
  @field:JsonProperty("amount") val amount: kotlin.Long
)