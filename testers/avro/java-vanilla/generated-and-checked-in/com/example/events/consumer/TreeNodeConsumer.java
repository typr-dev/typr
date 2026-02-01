package com.example.events.consumer;

import com.example.events.TreeNode;
import com.example.events.header.StandardHeaders;
import java.time.Duration;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;

/** Type-safe consumer for tree-node topic */
public record TreeNodeConsumer(
    Consumer<String, TreeNode> consumer, TreeNodeHandler handler, String topic)
    implements AutoCloseable {
  public TreeNodeConsumer(Consumer<String, TreeNode> consumer, TreeNodeHandler handler) {
    this(consumer, handler, "tree-node");
  }

  public TreeNodeConsumer withConsumer(Consumer<String, TreeNode> consumer) {
    return new TreeNodeConsumer(consumer, handler, topic);
  }

  public TreeNodeConsumer withHandler(TreeNodeHandler handler) {
    return new TreeNodeConsumer(consumer, handler, topic);
  }

  public TreeNodeConsumer withTopic(String topic) {
    return new TreeNodeConsumer(consumer, handler, topic);
  }

  /** Close the consumer */
  @Override
  public void close() {
    consumer.close();
  }

  /** Poll for messages and dispatch to handler */
  public void poll(Duration timeout) {
    ConsumerRecords<String, TreeNode> records = consumer.poll(timeout);
    records.forEach(
        record -> {
          String key = record.key();
          TreeNode value = record.value();
          StandardHeaders headers = StandardHeaders.fromHeaders(record.headers());
          handler.handle(key, value, headers);
        });
  }
}
