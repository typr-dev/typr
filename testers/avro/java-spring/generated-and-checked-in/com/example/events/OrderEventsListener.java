package com.example.events;

import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Headers;
import org.springframework.kafka.annotation.KafkaListener;

/** Event listener interface for order-events topic. Implement this interface to handle events. */
public interface OrderEventsListener {
  /** Receive and dispatch events to handler methods */
  @KafkaListener(topics = "order-events")
  default CompletableFuture<Void> receive(ConsumerRecord<String, Object> record) {
    return switch (record.value()) {
      case null -> onUnknown(record);
      case OrderCancelled e -> onOrderCancelled(e, record.headers());
      case OrderPlaced e -> onOrderPlaced(e, record.headers());
      case OrderUpdated e -> onOrderUpdated(e, record.headers());
      default -> onUnknown(record);
    };
  }

  /** Handle OrderCancelled event */
  CompletableFuture<Void> onOrderCancelled(OrderCancelled event, Headers metadata);

  /** Handle OrderPlaced event */
  CompletableFuture<Void> onOrderPlaced(OrderPlaced event, Headers metadata);

  /** Handle OrderUpdated event */
  CompletableFuture<Void> onOrderUpdated(OrderUpdated event, Headers metadata);

  /** Handle unknown event types. Override to customize behavior. */
  default CompletableFuture<Void> onUnknown(ConsumerRecord<String, Object> record) {
    return CompletableFuture.completedFuture(null);
  }
}
