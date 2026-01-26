package com.example.events.consumer;

import com.example.events.OrderCancelled;
import com.example.events.OrderEvents;
import com.example.events.OrderPlaced;
import com.example.events.OrderUpdated;
import com.example.events.header.StandardHeaders;
import java.time.Duration;
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

  /** Poll for messages and dispatch to handler */
  public void poll(Duration timeout) {
    ConsumerRecords<String, OrderEvents> records = consumer.poll(timeout);
    records.forEach(
        record -> {
          String key = record.key();
          OrderEvents value = record.value();
          StandardHeaders headers = StandardHeaders.fromHeaders(record.headers());
          switch (value) {
            case OrderCancelled e -> handler.handleOrderCancelled(key, e, headers);
            case OrderPlaced e -> handler.handleOrderPlaced(key, e, headers);
            case OrderUpdated e -> handler.handleOrderUpdated(key, e, headers);
            default -> handler.handleUnknown(key, value, headers);
          }
          ;
        });
  }
}
