package com.example.events.consumer;

import com.example.events.CustomerOrder;
import com.example.events.header.StandardHeaders;
import java.time.Duration;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;

/** Type-safe consumer for customer-order topic */
public record CustomerOrderConsumer(
    Consumer<String, CustomerOrder> consumer, CustomerOrderHandler handler, String topic)
    implements AutoCloseable {
  public CustomerOrderConsumer(
      Consumer<String, CustomerOrder> consumer, CustomerOrderHandler handler) {
    this(consumer, handler, "customer-order");
  }

  public CustomerOrderConsumer withConsumer(Consumer<String, CustomerOrder> consumer) {
    return new CustomerOrderConsumer(consumer, handler, topic);
  }

  public CustomerOrderConsumer withHandler(CustomerOrderHandler handler) {
    return new CustomerOrderConsumer(consumer, handler, topic);
  }

  public CustomerOrderConsumer withTopic(String topic) {
    return new CustomerOrderConsumer(consumer, handler, topic);
  }

  /** Close the consumer */
  @Override
  public void close() {
    consumer.close();
  }

  /** Poll for messages and dispatch to handler */
  public void poll(Duration timeout) {
    ConsumerRecords<String, CustomerOrder> records = consumer.poll(timeout);
    records.forEach(
        record -> {
          String key = record.key();
          CustomerOrder value = record.value();
          StandardHeaders headers = StandardHeaders.fromHeaders(record.headers());
          handler.handle(key, value, headers);
        });
  }
}
