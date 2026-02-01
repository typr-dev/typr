package com.example.events.producer;

import com.example.events.CustomerOrder;
import com.example.events.header.StandardHeaders;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

/** Type-safe producer for customer-order topic */
public record CustomerOrderProducer(Producer<String, CustomerOrder> producer, String topic)
    implements AutoCloseable {
  public CustomerOrderProducer(Producer<String, CustomerOrder> producer) {
    this(producer, "customer-order");
  }

  public CustomerOrderProducer withProducer(Producer<String, CustomerOrder> producer) {
    return new CustomerOrderProducer(producer, topic);
  }

  public CustomerOrderProducer withTopic(String topic) {
    return new CustomerOrderProducer(producer, topic);
  }

  /** Close the producer */
  @Override
  public void close() {
    producer.close();
  }

  /** Send a message to the topic asynchronously */
  public CompletableFuture<RecordMetadata> send(String key, CustomerOrder value) {
    return CompletableFuture.supplyAsync(
            () -> {
              CompletableFuture<RecordMetadata> future = new CompletableFuture<>();
              producer.send(
                  new ProducerRecord<String, CustomerOrder>(topic, key, value),
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
      String key, CustomerOrder value, StandardHeaders headers) {
    return CompletableFuture.supplyAsync(
            () -> {
              CompletableFuture<RecordMetadata> future = new CompletableFuture<>();
              producer.send(
                  new ProducerRecord<String, CustomerOrder>(
                      topic, null, key, value, headers.toHeaders()),
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
