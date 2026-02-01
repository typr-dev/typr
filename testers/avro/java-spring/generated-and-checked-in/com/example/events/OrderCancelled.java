package com.example.events;

import com.example.events.precisetypes.Decimal10_2;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Event emitted when an order is cancelled */
public record OrderCancelled(
    /** Unique identifier for the order */
    @JsonProperty("orderId") UUID orderId,
    /** Customer who placed the order */
    @JsonProperty("customerId") Long customerId,
    /** Optional cancellation reason */
    @JsonProperty("reason") Optional<String> reason,
    /** When the order was cancelled */
    @JsonProperty("cancelledAt") Instant cancelledAt,
    /** Amount to be refunded, if applicable */
    @JsonProperty("refundAmount") Optional<Decimal10_2> refundAmount)
    implements OrderEvents {
  /** Unique identifier for the order */
  public OrderCancelled withOrderId(UUID orderId) {
    return new OrderCancelled(orderId, customerId, reason, cancelledAt, refundAmount);
  }

  /** Customer who placed the order */
  public OrderCancelled withCustomerId(Long customerId) {
    return new OrderCancelled(orderId, customerId, reason, cancelledAt, refundAmount);
  }

  /** Optional cancellation reason */
  public OrderCancelled withReason(Optional<String> reason) {
    return new OrderCancelled(orderId, customerId, reason, cancelledAt, refundAmount);
  }

  /** When the order was cancelled */
  public OrderCancelled withCancelledAt(Instant cancelledAt) {
    return new OrderCancelled(orderId, customerId, reason, cancelledAt, refundAmount);
  }

  /** Amount to be refunded, if applicable */
  public OrderCancelled withRefundAmount(Optional<Decimal10_2> refundAmount) {
    return new OrderCancelled(orderId, customerId, reason, cancelledAt, refundAmount);
  }
}
