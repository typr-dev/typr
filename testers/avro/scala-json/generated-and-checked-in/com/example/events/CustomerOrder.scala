package com.example.events

import com.fasterxml.jackson.annotation.JsonProperty

/** Order with wrapper types for type-safe IDs */
case class CustomerOrder(
  /** Unique order identifier */
  @JsonProperty("orderId") orderId: OrderId,
  /** Customer identifier */
  @JsonProperty("customerId") customerId: CustomerId,
  /** Customer email address */
  @JsonProperty("email") email: Option[Email],
  /** Order amount in cents (no wrapper) */
  @JsonProperty("amount") amount: Long
)