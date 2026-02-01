package com.example.events.producer;

import com.example.events.LinkedListNode;
import com.example.events.header.StandardHeaders;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

/** Type-safe producer for linked-list-node topic */
public record LinkedListNodeProducer(Producer<String, LinkedListNode> producer, String topic)
    implements AutoCloseable {
  public LinkedListNodeProducer(Producer<String, LinkedListNode> producer) {
    this(producer, "linked-list-node");
  }

  public LinkedListNodeProducer withProducer(Producer<String, LinkedListNode> producer) {
    return new LinkedListNodeProducer(producer, topic);
  }

  public LinkedListNodeProducer withTopic(String topic) {
    return new LinkedListNodeProducer(producer, topic);
  }

  /** Close the producer */
  @Override
  public void close() {
    producer.close();
  }

  /** Send a message to the topic asynchronously */
  public CompletableFuture<RecordMetadata> send(String key, LinkedListNode value) {
    return CompletableFuture.supplyAsync(
            () -> {
              CompletableFuture<RecordMetadata> future = new CompletableFuture<>();
              producer.send(
                  new ProducerRecord<String, LinkedListNode>(topic, key, value),
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
      String key, LinkedListNode value, StandardHeaders headers) {
    return CompletableFuture.supplyAsync(
            () -> {
              CompletableFuture<RecordMetadata> future = new CompletableFuture<>();
              producer.send(
                  new ProducerRecord<String, LinkedListNode>(
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
