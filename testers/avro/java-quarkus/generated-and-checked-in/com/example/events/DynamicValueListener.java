package com.example.events;

import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Metadata;

/** Event listener interface for dynamic-value topic. Implement this interface to handle events. */
public interface DynamicValueListener {
  /** Receive and dispatch events to handler methods */
  @Incoming("dynamic-value")
  default Uni<Void> receive(Message<Object> record) {
    return switch (record.getPayload()) {
      case null -> onUnknown(record);
      case DynamicValue e -> onDynamicValue(e, record.getMetadata());
      default -> onUnknown(record);
    };
  }

  /** Handle DynamicValue event */
  Uni<Void> onDynamicValue(DynamicValue event, Metadata metadata);

  /** Handle unknown event types. Override to customize behavior. */
  default Uni<Void> onUnknown(Message<Object> record) {
    return Uni.createFrom().voidItem();
  }
}
