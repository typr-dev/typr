package com.example.events.producer;

import com.example.events.Invoice;
import com.example.events.header.StandardHeaders;
import java.util.concurrent.Future;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

/** Type-safe producer for invoice topic */
public record InvoiceProducer(Producer<String, Invoice> producer, String topic)
    implements AutoCloseable {
  public InvoiceProducer(Producer<String, Invoice> producer) {
    this(producer, "invoice");
  }

  public InvoiceProducer withProducer(Producer<String, Invoice> producer) {
    return new InvoiceProducer(producer, topic);
  }

  public InvoiceProducer withTopic(String topic) {
    return new InvoiceProducer(producer, topic);
  }

  /** Close the producer */
  @Override
  public void close() {
    producer.close();
  }

  /** Send a message to the topic */
  public Future<RecordMetadata> send(String key, Invoice value) {
    return producer.send(new ProducerRecord<String, Invoice>(topic, key, value));
  }

  /** Send a message with headers to the topic */
  public Future<RecordMetadata> send(String key, Invoice value, StandardHeaders headers) {
    return producer.send(
        new ProducerRecord<String, Invoice>(topic, null, key, value, headers.toHeaders()));
  }
}
