package com.example.events

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant
import java.util.UUID

/** Event emitted when an order status changes */
case class OrderUpdated(
  /** Unique identifier for the order */
  @JsonProperty("orderId") orderId: UUID,
  /** Previous status of the order */
  @JsonProperty("previousStatus") previousStatus: OrderStatus,
  /** New status of the order */
  @JsonProperty("newStatus") newStatus: OrderStatus,
  /** When the status was updated */
  @JsonProperty("updatedAt") updatedAt: Instant,
  /** Shipping address if status is SHIPPED */
  @JsonProperty("shippingAddress") shippingAddress: Option[Address]
) extends OrderEvents