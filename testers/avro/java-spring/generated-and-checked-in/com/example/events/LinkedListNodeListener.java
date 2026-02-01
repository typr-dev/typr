package com.example.events;

import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Headers;
import org.springframework.kafka.annotation.KafkaListener;

/**
 * Event listener interface for linked-list-node topic. Implement this interface to handle events.
 */
public interface LinkedListNodeListener {
  /** Receive and dispatch events to handler methods */
  @KafkaListener(topics = "linked-list-node")
  default CompletableFuture<Void> receive(ConsumerRecord<String, Object> record) {
    return switch (record.value()) {
      case null -> onUnknown(record);
      case LinkedListNode e -> onLinkedListNode(e, record.headers());
      default -> onUnknown(record);
    };
  }

  /** Handle LinkedListNode event */
  CompletableFuture<Void> onLinkedListNode(LinkedListNode event, Headers metadata);

  /** Handle unknown event types. Override to customize behavior. */
  default CompletableFuture<Void> onUnknown(ConsumerRecord<String, Object> record) {
    return CompletableFuture.completedFuture(null);
  }
}
