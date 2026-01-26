package com.example.events

import com.example.events.precisetypes.Decimal10_2
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant
import java.util.UUID

/** Event emitted when an order is cancelled */
case class OrderCancelled(
  /** Unique identifier for the order */
  @JsonProperty("orderId") orderId: UUID,
  /** Customer who placed the order */
  @JsonProperty("customerId") customerId: Long,
  /** Optional cancellation reason */
  @JsonProperty("reason") reason: Option[String],
  /** When the order was cancelled */
  @JsonProperty("cancelledAt") cancelledAt: Instant,
  /** Amount to be refunded, if applicable */
  @JsonProperty("refundAmount") refundAmount: Option[Decimal10_2]
) extends OrderEvents