package com.example.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Event emitted when an order status changes */
public record OrderUpdated(
    /** Unique identifier for the order */
    @JsonProperty("orderId") UUID orderId,
    /** Previous status of the order */
    @JsonProperty("previousStatus") OrderStatus previousStatus,
    /** New status of the order */
    @JsonProperty("newStatus") OrderStatus newStatus,
    /** When the status was updated */
    @JsonProperty("updatedAt") Instant updatedAt,
    /** Shipping address if status is SHIPPED */
    @JsonProperty("shippingAddress") Optional<Address> shippingAddress)
    implements OrderEvents {
  /** Unique identifier for the order */
  public OrderUpdated withOrderId(UUID orderId) {
    return new OrderUpdated(orderId, previousStatus, newStatus, updatedAt, shippingAddress);
  }

  /** Previous status of the order */
  public OrderUpdated withPreviousStatus(OrderStatus previousStatus) {
    return new OrderUpdated(orderId, previousStatus, newStatus, updatedAt, shippingAddress);
  }

  /** New status of the order */
  public OrderUpdated withNewStatus(OrderStatus newStatus) {
    return new OrderUpdated(orderId, previousStatus, newStatus, updatedAt, shippingAddress);
  }

  /** When the status was updated */
  public OrderUpdated withUpdatedAt(Instant updatedAt) {
    return new OrderUpdated(orderId, previousStatus, newStatus, updatedAt, shippingAddress);
  }

  /** Shipping address if status is SHIPPED */
  public OrderUpdated withShippingAddress(Optional<Address> shippingAddress) {
    return new OrderUpdated(orderId, previousStatus, newStatus, updatedAt, shippingAddress);
  }
}
