package com.example.events.consumer;

import com.example.events.LinkedListNode;
import com.example.events.header.StandardHeaders;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.stream.StreamSupport;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;

/** Type-safe consumer for linked-list-node topic */
public record LinkedListNodeConsumer(
    Consumer<String, LinkedListNode> consumer, LinkedListNodeHandler handler, String topic)
    implements AutoCloseable {
  public LinkedListNodeConsumer(
      Consumer<String, LinkedListNode> consumer, LinkedListNodeHandler handler) {
    this(consumer, handler, "linked-list-node");
  }

  public LinkedListNodeConsumer withConsumer(Consumer<String, LinkedListNode> consumer) {
    return new LinkedListNodeConsumer(consumer, handler, topic);
  }

  public LinkedListNodeConsumer withHandler(LinkedListNodeHandler handler) {
    return new LinkedListNodeConsumer(consumer, handler, topic);
  }

  public LinkedListNodeConsumer withTopic(String topic) {
    return new LinkedListNodeConsumer(consumer, handler, topic);
  }

  /** Close the consumer */
  @Override
  public void close() {
    consumer.close();
  }

  /** Poll for messages and dispatch to handler, returning composed effect */
  public CompletableFuture<Void> poll(Duration timeout) {
    ConsumerRecords<String, LinkedListNode> records = consumer.poll(timeout);
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
