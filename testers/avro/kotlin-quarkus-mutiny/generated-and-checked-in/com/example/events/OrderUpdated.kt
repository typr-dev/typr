package com.example.events

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant
import java.util.UUID

/** Event emitted when an order status changes */
data class OrderUpdated(
  /** Unique identifier for the order */
  @field:JsonProperty("orderId") val orderId: UUID,
  /** Previous status of the order */
  @field:JsonProperty("previousStatus") val previousStatus: OrderStatus,
  /** New status of the order */
  @field:JsonProperty("newStatus") val newStatus: OrderStatus,
  /** When the status was updated */
  @field:JsonProperty("updatedAt") val updatedAt: Instant,
  /** Shipping address if status is SHIPPED */
  @field:JsonProperty("shippingAddress") val shippingAddress: Address?
) : OrderEvents