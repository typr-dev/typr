package com.example.events;

import java.util.concurrent.CompletableFuture;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

/** Type-safe event publisher for linked-list-node topic */
@Service
public record LinkedListNodePublisher(
    KafkaTemplate<String, LinkedListNode> kafkaTemplate, String topic) {
  public LinkedListNodePublisher(KafkaTemplate<String, LinkedListNode> kafkaTemplate) {
    this(kafkaTemplate, "linked-list-node");
  }

  public LinkedListNodePublisher withKafkaTemplate(
      KafkaTemplate<String, LinkedListNode> kafkaTemplate) {
    return new LinkedListNodePublisher(kafkaTemplate, topic);
  }

  public LinkedListNodePublisher withTopic(String topic) {
    return new LinkedListNodePublisher(kafkaTemplate, topic);
  }

  /** Publish a LinkedListNode event */
  public CompletableFuture<SendResult<String, LinkedListNode>> publish(
      String key, LinkedListNode event) {
    return kafkaTemplate.send(topic, key, event);
  }
}
