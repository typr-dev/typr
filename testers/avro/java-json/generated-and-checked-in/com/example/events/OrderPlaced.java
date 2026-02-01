package com.example.events;

import com.example.events.precisetypes.Decimal10_2;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Event emitted when an order is placed */
public record OrderPlaced(
    /** Unique identifier for the order */
    @JsonProperty("orderId") UUID orderId,
    /** Customer who placed the order */
    @JsonProperty("customerId") Long customerId,
    /** Total amount of the order */
    @JsonProperty("totalAmount") Decimal10_2 totalAmount,
    /** When the order was placed */
    @JsonProperty("placedAt") Instant placedAt,
    /** List of item IDs in the order */
    @JsonProperty("items") List<String> items,
    /** Optional shipping address */
    @JsonProperty("shippingAddress") Optional<String> shippingAddress)
    implements OrderEvents {
  /** Unique identifier for the order */
  public OrderPlaced withOrderId(UUID orderId) {
    return new OrderPlaced(orderId, customerId, totalAmount, placedAt, items, shippingAddress);
  }

  /** Customer who placed the order */
  public OrderPlaced withCustomerId(Long customerId) {
    return new OrderPlaced(orderId, customerId, totalAmount, placedAt, items, shippingAddress);
  }

  /** Total amount of the order */
  public OrderPlaced withTotalAmount(Decimal10_2 totalAmount) {
    return new OrderPlaced(orderId, customerId, totalAmount, placedAt, items, shippingAddress);
  }

  /** When the order was placed */
  public OrderPlaced withPlacedAt(Instant placedAt) {
    return new OrderPlaced(orderId, customerId, totalAmount, placedAt, items, shippingAddress);
  }

  /** List of item IDs in the order */
  public OrderPlaced withItems(List<String> items) {
    return new OrderPlaced(orderId, customerId, totalAmount, placedAt, items, shippingAddress);
  }

  /** Optional shipping address */
  public OrderPlaced withShippingAddress(Optional<String> shippingAddress) {
    return new OrderPlaced(orderId, customerId, totalAmount, placedAt, items, shippingAddress);
  }
}
