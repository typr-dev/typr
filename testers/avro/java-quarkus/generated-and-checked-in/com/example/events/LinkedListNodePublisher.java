package com.example.events;

import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.MutinyEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;

/** Type-safe event publisher for linked-list-node topic */
@ApplicationScoped
public record LinkedListNodePublisher(
    @Channel("linked-list-node") MutinyEmitter<LinkedListNode> kafkaTemplate, String topic) {
  @Inject
  public LinkedListNodePublisher {}

  public LinkedListNodePublisher(
      @Channel("linked-list-node") MutinyEmitter<LinkedListNode> kafkaTemplate) {
    this(kafkaTemplate, "linked-list-node");
  }

  public LinkedListNodePublisher withKafkaTemplate(MutinyEmitter<LinkedListNode> kafkaTemplate) {
    return new LinkedListNodePublisher(kafkaTemplate, topic);
  }

  public LinkedListNodePublisher withTopic(String topic) {
    return new LinkedListNodePublisher(kafkaTemplate, topic);
  }

  /** Publish a LinkedListNode event */
  public Uni<Void> publish(String key, LinkedListNode event) {
    return kafkaTemplate.send(event);
  }
}
