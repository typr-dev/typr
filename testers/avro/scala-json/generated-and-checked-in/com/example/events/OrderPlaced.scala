package com.example.events

import com.example.events.precisetypes.Decimal10_2
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant
import java.util.UUID

/** Event emitted when an order is placed */
case class OrderPlaced(
  /** Unique identifier for the order */
  @JsonProperty("orderId") orderId: UUID,
  /** Customer who placed the order */
  @JsonProperty("customerId") customerId: Long,
  /** Total amount of the order */
  @JsonProperty("totalAmount") totalAmount: Decimal10_2,
  /** When the order was placed */
  @JsonProperty("placedAt") placedAt: Instant,
  /** List of item IDs in the order */
  @JsonProperty("items") items: List[String],
  /** Optional shipping address */
  @JsonProperty("shippingAddress") shippingAddress: Option[String]
) extends OrderEvents