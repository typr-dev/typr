package com.example.events.common;

import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Headers;
import org.springframework.kafka.annotation.KafkaListener;

/** Event listener interface for money topic. Implement this interface to handle events. */
public interface MoneyListener {
  /** Receive and dispatch events to handler methods */
  @KafkaListener(topics = "money")
  default CompletableFuture<Void> receive(ConsumerRecord<String, Object> record) {
    return switch (record.value()) {
      case null -> onUnknown(record);
      case Money e -> onMoney(e, record.headers());
      default -> onUnknown(record);
    };
  }

  /** Handle Money event */
  CompletableFuture<Void> onMoney(Money event, Headers metadata);

  /** Handle unknown event types. Override to customize behavior. */
  default CompletableFuture<Void> onUnknown(ConsumerRecord<String, Object> record) {
    return CompletableFuture.completedFuture(null);
  }
}
