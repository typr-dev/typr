package com.example.events.consumer;

import com.example.events.common.Money;
import com.example.events.header.StandardHeaders;
import java.time.Duration;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;

/** Type-safe consumer for money topic */
public record MoneyConsumer(Consumer<String, Money> consumer, MoneyHandler handler, String topic)
    implements AutoCloseable {
  public MoneyConsumer(Consumer<String, Money> consumer, MoneyHandler handler) {
    this(consumer, handler, "money");
  }

  public MoneyConsumer withConsumer(Consumer<String, Money> consumer) {
    return new MoneyConsumer(consumer, handler, topic);
  }

  public MoneyConsumer withHandler(MoneyHandler handler) {
    return new MoneyConsumer(consumer, handler, topic);
  }

  public MoneyConsumer withTopic(String topic) {
    return new MoneyConsumer(consumer, handler, topic);
  }

  /** Close the consumer */
  @Override
  public void close() {
    consumer.close();
  }

  /** Poll for messages and dispatch to handler */
  public void poll(Duration timeout) {
    ConsumerRecords<String, Money> records = consumer.poll(timeout);
    records.forEach(
        record -> {
          String key = record.key();
          Money value = record.value();
          StandardHeaders headers = StandardHeaders.fromHeaders(record.headers());
          handler.handle(key, value, headers);
        });
  }
}
