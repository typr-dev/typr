package com.example.events;

import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.MutinyEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;

/** Type-safe event publisher for order-events topic */
@ApplicationScoped
public record OrderEventsPublisher(
    @Channel("order-events") MutinyEmitter<OrderEvents> kafkaTemplate, String topic) {
  @Inject
  public OrderEventsPublisher {}

  public OrderEventsPublisher(@Channel("order-events") MutinyEmitter<OrderEvents> kafkaTemplate) {
    this(kafkaTemplate, "order-events");
  }

  public OrderEventsPublisher withKafkaTemplate(MutinyEmitter<OrderEvents> kafkaTemplate) {
    return new OrderEventsPublisher(kafkaTemplate, topic);
  }

  public OrderEventsPublisher withTopic(String topic) {
    return new OrderEventsPublisher(kafkaTemplate, topic);
  }

  /** Publish a OrderCancelled event */
  public Uni<Void> publish(String key, OrderCancelled event) {
    return kafkaTemplate.send(event);
  }

  /** Publish a OrderPlaced event */
  public Uni<Void> publish(String key, OrderPlaced event) {
    return kafkaTemplate.send(event);
  }

  /** Publish a OrderUpdated event */
  public Uni<Void> publish(String key, OrderUpdated event) {
    return kafkaTemplate.send(event);
  }
}
