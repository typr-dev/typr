package com.example.events;

import java.util.concurrent.CompletableFuture;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

/** Type-safe event publisher for order-events topic */
@Service
public record OrderEventsPublisher(KafkaTemplate<String, OrderEvents> kafkaTemplate, String topic) {
  public OrderEventsPublisher(KafkaTemplate<String, OrderEvents> kafkaTemplate) {
    this(kafkaTemplate, "order-events");
  }

  public OrderEventsPublisher withKafkaTemplate(KafkaTemplate<String, OrderEvents> kafkaTemplate) {
    return new OrderEventsPublisher(kafkaTemplate, topic);
  }

  public OrderEventsPublisher withTopic(String topic) {
    return new OrderEventsPublisher(kafkaTemplate, topic);
  }

  /** Publish a OrderCancelled event */
  public CompletableFuture<SendResult<String, OrderEvents>> publish(
      String key, OrderCancelled event) {
    return kafkaTemplate.send(topic, key, event);
  }

  /** Publish a OrderPlaced event */
  public CompletableFuture<SendResult<String, OrderEvents>> publish(String key, OrderPlaced event) {
    return kafkaTemplate.send(topic, key, event);
  }

  /** Publish a OrderUpdated event */
  public CompletableFuture<SendResult<String, OrderEvents>> publish(
      String key, OrderUpdated event) {
    return kafkaTemplate.send(topic, key, event);
  }
}
