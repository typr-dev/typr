package com.example.events

import com.example.events.precisetypes.Decimal10_2
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant
import java.util.UUID
import kotlin.collections.List

/** Event emitted when an order is placed */
data class OrderPlaced(
  /** Unique identifier for the order */
  @field:JsonProperty("orderId") val orderId: UUID,
  /** Customer who placed the order */
  @field:JsonProperty("customerId") val customerId: kotlin.Long,
  /** Total amount of the order */
  @field:JsonProperty("totalAmount") val totalAmount: Decimal10_2,
  /** When the order was placed */
  @field:JsonProperty("placedAt") val placedAt: Instant,
  /** List of item IDs in the order */
  @field:JsonProperty("items") val items: List<kotlin.String>,
  /** Optional shipping address */
  @field:JsonProperty("shippingAddress") val shippingAddress: kotlin.String?
) : OrderEvents