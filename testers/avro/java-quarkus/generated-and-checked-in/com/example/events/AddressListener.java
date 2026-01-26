package com.example.events;

import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Metadata;

/** Event listener interface for address topic. Implement this interface to handle events. */
public interface AddressListener {
  /** Receive and dispatch events to handler methods */
  @Incoming("address")
  default Uni<Void> receive(Message<Object> record) {
    return switch (record.getPayload()) {
      case null -> onUnknown(record);
      case Address e -> onAddress(e, record.getMetadata());
      default -> onUnknown(record);
    };
  }

  /** Handle Address event */
  Uni<Void> onAddress(Address event, Metadata metadata);

  /** Handle unknown event types. Override to customize behavior. */
  default Uni<Void> onUnknown(Message<Object> record) {
    return Uni.createFrom().voidItem();
  }
}
