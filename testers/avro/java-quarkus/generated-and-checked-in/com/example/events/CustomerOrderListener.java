package com.example.events;

import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Metadata;

/** Event listener interface for customer-order topic. Implement this interface to handle events. */
public interface CustomerOrderListener {
  /** Receive and dispatch events to handler methods */
  @Incoming("customer-order")
  default Uni<Void> receive(Message<Object> record) {
    return switch (record.getPayload()) {
      case null -> onUnknown(record);
      case CustomerOrder e -> onCustomerOrder(e, record.getMetadata());
      default -> onUnknown(record);
    };
  }

  /** Handle CustomerOrder event */
  Uni<Void> onCustomerOrder(CustomerOrder event, Metadata metadata);

  /** Handle unknown event types. Override to customize behavior. */
  default Uni<Void> onUnknown(Message<Object> record) {
    return Uni.createFrom().voidItem();
  }
}
