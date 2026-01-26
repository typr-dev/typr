package com.example.events

import com.example.events.precisetypes.Decimal10_2
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant
import java.util.UUID

/** Event emitted when an order is cancelled */
data class OrderCancelled(
  /** Unique identifier for the order */
  @field:JsonProperty("orderId") val orderId: UUID,
  /** Customer who placed the order */
  @field:JsonProperty("customerId") val customerId: kotlin.Long,
  /** Optional cancellation reason */
  @field:JsonProperty("reason") val reason: kotlin.String?,
  /** When the order was cancelled */
  @field:JsonProperty("cancelledAt") val cancelledAt: Instant,
  /** Amount to be refunded, if applicable */
  @field:JsonProperty("refundAmount") val refundAmount: Decimal10_2?
) : OrderEvents