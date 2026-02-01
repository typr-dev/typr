package com.example.events.consumer;

import com.example.events.DynamicValue;
import com.example.events.header.StandardHeaders;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.stream.StreamSupport;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;

/** Type-safe consumer for dynamic-value topic */
public record DynamicValueConsumer(
    Consumer<String, DynamicValue> consumer, DynamicValueHandler handler, String topic)
    implements AutoCloseable {
  public DynamicValueConsumer(
      Consumer<String, DynamicValue> consumer, DynamicValueHandler handler) {
    this(consumer, handler, "dynamic-value");
  }

  public DynamicValueConsumer withConsumer(Consumer<String, DynamicValue> consumer) {
    return new DynamicValueConsumer(consumer, handler, topic);
  }

  public DynamicValueConsumer withHandler(DynamicValueHandler handler) {
    return new DynamicValueConsumer(consumer, handler, topic);
  }

  public DynamicValueConsumer withTopic(String topic) {
    return new DynamicValueConsumer(consumer, handler, topic);
  }

  /** Close the consumer */
  @Override
  public void close() {
    consumer.close();
  }

  /** Poll for messages and dispatch to handler, returning composed effect */
  public CompletableFuture<Void> poll(Duration timeout) {
    ConsumerRecords<String, DynamicValue> records = consumer.poll(timeout);
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
