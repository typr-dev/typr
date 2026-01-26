package com.example.events.consumer;

import com.example.events.LinkedListNode;
import com.example.events.header.StandardHeaders;
import java.time.Duration;
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

  /** Poll for messages and dispatch to handler */
  public void poll(Duration timeout) {
    ConsumerRecords<String, LinkedListNode> records = consumer.poll(timeout);
    records.forEach(
        record -> {
          String key = record.key();
          LinkedListNode value = record.value();
          StandardHeaders headers = StandardHeaders.fromHeaders(record.headers());
          handler.handle(key, value, headers);
        });
  }
}
