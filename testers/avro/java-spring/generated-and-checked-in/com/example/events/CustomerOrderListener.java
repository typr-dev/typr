package com.example.events;

import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Headers;
import org.springframework.kafka.annotation.KafkaListener;

/** Event listener interface for customer-order topic. Implement this interface to handle events. */
public interface CustomerOrderListener {
  /** Receive and dispatch events to handler methods */
  @KafkaListener(topics = "customer-order")
  default CompletableFuture<Void> receive(ConsumerRecord<String, Object> record) {
    return switch (record.value()) {
      case null -> onUnknown(record);
      case CustomerOrder e -> onCustomerOrder(e, record.headers());
      default -> onUnknown(record);
    };
  }

  /** Handle CustomerOrder event */
  CompletableFuture<Void> onCustomerOrder(CustomerOrder event, Headers metadata);

  /** Handle unknown event types. Override to customize behavior. */
  default CompletableFuture<Void> onUnknown(ConsumerRecord<String, Object> record) {
    return CompletableFuture.completedFuture(null);
  }
}
