package com.example.events;

import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Metadata;

/** Event listener interface for order-events topic. Implement this interface to handle events. */
public interface OrderEventsListener {
  /** Receive and dispatch events to handler methods */
  @Incoming("order-events")
  default Uni<Void> receive(Message<Object> record) {
    return switch (record.getPayload()) {
      case null -> onUnknown(record);
      case OrderCancelled e -> onOrderCancelled(e, record.getMetadata());
      case OrderPlaced e -> onOrderPlaced(e, record.getMetadata());
      case OrderUpdated e -> onOrderUpdated(e, record.getMetadata());
      default -> onUnknown(record);
    };
  }

  /** Handle OrderCancelled event */
  Uni<Void> onOrderCancelled(OrderCancelled event, Metadata metadata);

  /** Handle OrderPlaced event */
  Uni<Void> onOrderPlaced(OrderPlaced event, Metadata metadata);

  /** Handle OrderUpdated event */
  Uni<Void> onOrderUpdated(OrderUpdated event, Metadata metadata);

  /** Handle unknown event types. Override to customize behavior. */
  default Uni<Void> onUnknown(Message<Object> record) {
    return Uni.createFrom().voidItem();
  }
}
