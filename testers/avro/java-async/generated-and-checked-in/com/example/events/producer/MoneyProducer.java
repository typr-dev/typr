package com.example.events.producer;

import com.example.events.common.Money;
import com.example.events.header.StandardHeaders;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

/** Type-safe producer for money topic */
public record MoneyProducer(Producer<String, Money> producer, String topic)
    implements AutoCloseable {
  public MoneyProducer(Producer<String, Money> producer) {
    this(producer, "money");
  }

  public MoneyProducer withProducer(Producer<String, Money> producer) {
    return new MoneyProducer(producer, topic);
  }

  public MoneyProducer withTopic(String topic) {
    return new MoneyProducer(producer, topic);
  }

  /** Close the producer */
  @Override
  public void close() {
    producer.close();
  }

  /** Send a message to the topic asynchronously */
  public CompletableFuture<RecordMetadata> send(String key, Money value) {
    return CompletableFuture.supplyAsync(
            () -> {
              CompletableFuture<RecordMetadata> future = new CompletableFuture<>();
              producer.send(
                  new ProducerRecord<String, Money>(topic, key, value),
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
  public CompletableFuture<RecordMetadata> send(String key, Money value, StandardHeaders headers) {
    return CompletableFuture.supplyAsync(
            () -> {
              CompletableFuture<RecordMetadata> future = new CompletableFuture<>();
              producer.send(
                  new ProducerRecord<String, Money>(topic, null, key, value, headers.toHeaders()),
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
