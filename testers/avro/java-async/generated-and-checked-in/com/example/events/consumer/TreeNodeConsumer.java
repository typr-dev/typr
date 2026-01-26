package com.example.events.consumer;

import com.example.events.TreeNode;
import com.example.events.header.StandardHeaders;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.stream.StreamSupport;
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

  /** Poll for messages and dispatch to handler, returning composed effect */
  public CompletableFuture<Void> poll(Duration timeout) {
    ConsumerRecords<String, TreeNode> records = consumer.poll(timeout);
    return CompletableFuture.allOf(
        StreamSupport.stream(records.spliterator(), false)
            .map(
                record ->
                    handler.handle(
                        record.key(),
                        record.value(),
                        StandardHeaders.fromHeaders(record.headers())))
            .toArray(CompletableFuture[]::new));
  }
}
