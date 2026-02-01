package com.example.events.producer;

import com.example.events.Address;
import com.example.events.header.StandardHeaders;
import java.util.concurrent.Future;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

/** Type-safe producer for address topic */
public record AddressProducer(Producer<String, Address> producer, String topic)
    implements AutoCloseable {
  public AddressProducer(Producer<String, Address> producer) {
    this(producer, "address");
  }

  public AddressProducer withProducer(Producer<String, Address> producer) {
    return new AddressProducer(producer, topic);
  }

  public AddressProducer withTopic(String topic) {
    return new AddressProducer(producer, topic);
  }

  /** Close the producer */
  @Override
  public void close() {
    producer.close();
  }

  /** Send a message to the topic */
  public Future<RecordMetadata> send(String key, Address value) {
    return producer.send(new ProducerRecord<String, Address>(topic, key, value));
  }

  /** Send a message with headers to the topic */
  public Future<RecordMetadata> send(String key, Address value, StandardHeaders headers) {
    return producer.send(
        new ProducerRecord<String, Address>(topic, null, key, value, headers.toHeaders()));
  }
}
