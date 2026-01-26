package com.example.events.consumer;

import com.example.events.Invoice;
import com.example.events.header.StandardHeaders;
import java.time.Duration;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;

/** Type-safe consumer for invoice topic */
public record InvoiceConsumer(
    Consumer<String, Invoice> consumer, InvoiceHandler handler, String topic)
    implements AutoCloseable {
  public InvoiceConsumer(Consumer<String, Invoice> consumer, InvoiceHandler handler) {
    this(consumer, handler, "invoice");
  }

  public InvoiceConsumer withConsumer(Consumer<String, Invoice> consumer) {
    return new InvoiceConsumer(consumer, handler, topic);
  }

  public InvoiceConsumer withHandler(InvoiceHandler handler) {
    return new InvoiceConsumer(consumer, handler, topic);
  }

  public InvoiceConsumer withTopic(String topic) {
    return new InvoiceConsumer(consumer, handler, topic);
  }

  /** Close the consumer */
  @Override
  public void close() {
    consumer.close();
  }

  /** Poll for messages and dispatch to handler */
  public void poll(Duration timeout) {
    ConsumerRecords<String, Invoice> records = consumer.poll(timeout);
    records.forEach(
        record -> {
          String key = record.key();
          Invoice value = record.value();
          StandardHeaders headers = StandardHeaders.fromHeaders(record.headers());
          handler.handle(key, value, headers);
        });
  }
}
