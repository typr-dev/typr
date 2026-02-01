package com.example.events.consumer;

import com.example.events.Address;
import com.example.events.header.StandardHeaders;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.stream.StreamSupport;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;

/** Type-safe consumer for address topic */
public record AddressConsumer(
    Consumer<String, Address> consumer, AddressHandler handler, String topic)
    implements AutoCloseable {
  public AddressConsumer(Consumer<String, Address> consumer, AddressHandler handler) {
    this(consumer, handler, "address");
  }

  public AddressConsumer withConsumer(Consumer<String, Address> consumer) {
    return new AddressConsumer(consumer, handler, topic);
  }

  public AddressConsumer withHandler(AddressHandler handler) {
    return new AddressConsumer(consumer, handler, topic);
  }

  public AddressConsumer withTopic(String topic) {
    return new AddressConsumer(consumer, handler, topic);
  }

  /** Close the consumer */
  @Override
  public void close() {
    consumer.close();
  }

  /** Poll for messages and dispatch to handler, returning composed effect */
  public CompletableFuture<Void> poll(Duration timeout) {
    ConsumerRecords<String, Address> records = consumer.poll(timeout);
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
