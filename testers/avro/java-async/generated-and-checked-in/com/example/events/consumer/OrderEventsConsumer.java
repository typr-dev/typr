package com.example.events.consumer;

import com.example.events.OrderCancelled;
import com.example.events.OrderEvents;
import com.example.events.OrderPlaced;
import com.example.events.OrderUpdated;
import com.example.events.header.StandardHeaders;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.stream.StreamSupport;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;

/** Type-safe consumer for order-events topic */
public record OrderEventsConsumer(
    Consumer<String, OrderEvents> consumer, OrderEventsHandler handler, String topic)
    implements AutoCloseable {
  public OrderEventsConsumer(Consumer<String, OrderEvents> consumer, OrderEventsHandler handler) {
    this(consumer, handler, "order-events");
  }

  public OrderEventsConsumer withConsumer(Consumer<String, OrderEvents> consumer) {
    return new OrderEventsConsumer(consumer, handler, topic);
  }

  public OrderEventsConsumer withHandler(OrderEventsHandler handler) {
    return new OrderEventsConsumer(consumer, handler, topic);
  }

  public OrderEventsConsumer withTopic(String topic) {
    return new OrderEventsConsumer(consumer, handler, topic);
  }

  /** Close the consumer */
  @Override
  public void close() {
    consumer.close();
  }

  /** Poll for messages and dispatch to handler, returning composed effect */
  public CompletableFuture<Void> poll(Duration timeout) {
    ConsumerRecords<String, OrderEvents> records = consumer.poll(timeout);
    return CompletableFuture.allOf(
        StreamSupport.stream(records.spliterator(), false)
            .map(
                record ->
                    switch (record.value()) {
                      case OrderCancelled e ->
                          handler.handleOrderCancelled(
                              record.key(), e, StandardHeaders.fromHeaders(record.headers()));
                      case OrderPlaced e ->
                          handler.handleOrderPlaced(
                              record.key(), e, StandardHeaders.fromHeaders(record.headers()));
                      case OrderUpdated e ->
                          handler.handleOrderUpdated(
                              record.key(), e, StandardHeaders.fromHeaders(record.headers()));
                      default ->
                          handler.handleUnknown(
                              record.key(),
                              record.value(),
                              StandardHeaders.fromHeaders(record.headers()));
                    })
            .toArray(CompletableFuture[]::new));
  }
}
