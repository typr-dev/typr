package com.example.events.consumer;

import com.example.events.common.Money;
import com.example.events.header.StandardHeaders;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.stream.StreamSupport;
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

  /** Poll for messages and dispatch to handler, returning composed effect */
  public CompletableFuture<Void> poll(Duration timeout) {
    ConsumerRecords<String, Money> records = consumer.poll(timeout);
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
