package com.example.events.producer;

import com.example.events.DynamicValue;
import com.example.events.header.StandardHeaders;
import java.util.concurrent.Future;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

/** Type-safe producer for dynamic-value topic */
public record DynamicValueProducer(Producer<String, DynamicValue> producer, String topic)
    implements AutoCloseable {
  public DynamicValueProducer(Producer<String, DynamicValue> producer) {
    this(producer, "dynamic-value");
  }

  public DynamicValueProducer withProducer(Producer<String, DynamicValue> producer) {
    return new DynamicValueProducer(producer, topic);
  }

  public DynamicValueProducer withTopic(String topic) {
    return new DynamicValueProducer(producer, topic);
  }

  /** Close the producer */
  @Override
  public void close() {
    producer.close();
  }

  /** Send a message to the topic */
  public Future<RecordMetadata> send(String key, DynamicValue value) {
    return producer.send(new ProducerRecord<String, DynamicValue>(topic, key, value));
  }

  /** Send a message with headers to the topic */
  public Future<RecordMetadata> send(String key, DynamicValue value, StandardHeaders headers) {
    return producer.send(
        new ProducerRecord<String, DynamicValue>(topic, null, key, value, headers.toHeaders()));
  }
}
