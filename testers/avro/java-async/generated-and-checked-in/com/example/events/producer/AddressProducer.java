package com.example.events.producer;

import com.example.events.Address;
import com.example.events.header.StandardHeaders;
import java.util.concurrent.CompletableFuture;
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

  /** Send a message to the topic asynchronously */
  public CompletableFuture<RecordMetadata> send(String key, Address value) {
    return CompletableFuture.supplyAsync(
            () -> {
              CompletableFuture<RecordMetadata> future = new CompletableFuture<>();
              producer.send(
                  new ProducerRecord<String, Address>(topic, key, value),
                  (result, exception) -> {
                    if (exception != null) {
                      future.completeExceptionally(exception);
                    } else {
                      future.complete(result);
                    }
                  });
              return future;
            })
        .thenCompose(f -> f);
  }

  /** Send a message with headers to the topic asynchronously */
  public CompletableFuture<RecordMetadata> send(
      String key, Address value, StandardHeaders headers) {
    return CompletableFuture.supplyAsync(
            () -> {
              CompletableFuture<RecordMetadata> future = new CompletableFuture<>();
              producer.send(
                  new ProducerRecord<String, Address>(topic, null, key, value, headers.toHeaders()),
                  (result, exception) -> {
                    if (exception != null) {
                      future.completeExceptionally(exception);
                    } else {
                      future.complete(result);
                    }
                  });
              return future;
            })
        .thenCompose(f -> f);
  }
}
