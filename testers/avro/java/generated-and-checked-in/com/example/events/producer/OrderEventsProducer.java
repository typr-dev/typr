package com.example.events.producer;

import com.example.events.OrderEvents;
import com.example.events.header.StandardHeaders;
import java.util.concurrent.Future;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

/** Type-safe producer for order-events topic */
public record OrderEventsProducer(Producer<String, OrderEvents> producer, String topic)
    implements AutoCloseable {
  public OrderEventsProducer(Producer<String, OrderEvents> producer) {
    this(producer, "order-events");
  }

  public OrderEventsProducer withProducer(Producer<String, OrderEvents> producer) {
    return new OrderEventsProducer(producer, topic);
  }

  public OrderEventsProducer withTopic(String topic) {
    return new OrderEventsProducer(producer, topic);
  }

  /** Close the producer */
  @Override
  public void close() {
    producer.close();
  }

  /** Send a message to the topic */
  public Future<RecordMetadata> send(String key, OrderEvents value) {
    return producer.send(new ProducerRecord<String, OrderEvents>(topic, key, value));
  }

  /** Send a message with headers to the topic */
  public Future<RecordMetadata> send(String key, OrderEvents value, StandardHeaders headers) {
    return producer.send(
        new ProducerRecord<String, OrderEvents>(topic, null, key, value, headers.toHeaders()));
  }
}
