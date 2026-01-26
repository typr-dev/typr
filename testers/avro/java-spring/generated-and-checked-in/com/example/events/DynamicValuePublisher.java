package com.example.events;

import java.util.concurrent.CompletableFuture;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

/** Type-safe event publisher for dynamic-value topic */
@Service
public record DynamicValuePublisher(
    KafkaTemplate<String, DynamicValue> kafkaTemplate, String topic) {
  public DynamicValuePublisher(KafkaTemplate<String, DynamicValue> kafkaTemplate) {
    this(kafkaTemplate, "dynamic-value");
  }

  public DynamicValuePublisher withKafkaTemplate(
      KafkaTemplate<String, DynamicValue> kafkaTemplate) {
    return new DynamicValuePublisher(kafkaTemplate, topic);
  }

  public DynamicValuePublisher withTopic(String topic) {
    return new DynamicValuePublisher(kafkaTemplate, topic);
  }

  /** Publish a DynamicValue event */
  public CompletableFuture<SendResult<String, DynamicValue>> publish(
      String key, DynamicValue event) {
    return kafkaTemplate.send(topic, key, event);
  }
}
