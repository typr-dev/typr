package com.example.events;

import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Headers;
import org.springframework.kafka.annotation.KafkaListener;

/** Event listener interface for tree-node topic. Implement this interface to handle events. */
public interface TreeNodeListener {
  /** Receive and dispatch events to handler methods */
  @KafkaListener(topics = "tree-node")
  default CompletableFuture<Void> receive(ConsumerRecord<String, Object> record) {
    return switch (record.value()) {
      case null -> onUnknown(record);
      case TreeNode e -> onTreeNode(e, record.headers());
      default -> onUnknown(record);
    };
  }

  /** Handle TreeNode event */
  CompletableFuture<Void> onTreeNode(TreeNode event, Headers metadata);

  /** Handle unknown event types. Override to customize behavior. */
  default CompletableFuture<Void> onUnknown(ConsumerRecord<String, Object> record) {
    return CompletableFuture.completedFuture(null);
  }
}
