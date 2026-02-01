package com.example.events.consumer;

import com.example.events.DynamicValue;
import com.example.events.header.StandardHeaders;
import java.time.Duration;
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

  /** Poll for messages and dispatch to handler */
  public void poll(Duration timeout) {
    ConsumerRecords<String, DynamicValue> records = consumer.poll(timeout);
    records.forEach(
        record -> {
          String key = record.key();
          DynamicValue value = record.value();
          StandardHeaders headers = StandardHeaders.fromHeaders(record.headers());
          handler.handle(key, value, headers);
        });
  }
}
