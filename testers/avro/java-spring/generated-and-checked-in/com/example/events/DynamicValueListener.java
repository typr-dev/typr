package com.example.events;

import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Headers;
import org.springframework.kafka.annotation.KafkaListener;

/** Event listener interface for dynamic-value topic. Implement this interface to handle events. */
public interface DynamicValueListener {
  /** Receive and dispatch events to handler methods */
  @KafkaListener(topics = "dynamic-value")
  default CompletableFuture<Void> receive(ConsumerRecord<String, Object> record) {
    return switch (record.value()) {
      case null -> onUnknown(record);
      case DynamicValue e -> onDynamicValue(e, record.headers());
      default -> onUnknown(record);
    };
  }

  /** Handle DynamicValue event */
  CompletableFuture<Void> onDynamicValue(DynamicValue event, Headers metadata);

  /** Handle unknown event types. Override to customize behavior. */
  default CompletableFuture<Void> onUnknown(ConsumerRecord<String, Object> record) {
    return CompletableFuture.completedFuture(null);
  }
}
