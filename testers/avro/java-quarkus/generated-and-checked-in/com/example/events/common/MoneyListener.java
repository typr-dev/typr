package com.example.events.common;

import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Metadata;

/** Event listener interface for money topic. Implement this interface to handle events. */
public interface MoneyListener {
  /** Receive and dispatch events to handler methods */
  @Incoming("money")
  default Uni<Void> receive(Message<Object> record) {
    return switch (record.getPayload()) {
      case null -> onUnknown(record);
      case Money e -> onMoney(e, record.getMetadata());
      default -> onUnknown(record);
    };
  }

  /** Handle Money event */
  Uni<Void> onMoney(Money event, Metadata metadata);

  /** Handle unknown event types. Override to customize behavior. */
  default Uni<Void> onUnknown(Message<Object> record) {
    return Uni.createFrom().voidItem();
  }
}
