package com.example.events.common;

import java.util.concurrent.CompletableFuture;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

/** Type-safe event publisher for money topic */
@Service
public record MoneyPublisher(KafkaTemplate<String, Money> kafkaTemplate, String topic) {
  public MoneyPublisher(KafkaTemplate<String, Money> kafkaTemplate) {
    this(kafkaTemplate, "money");
  }

  public MoneyPublisher withKafkaTemplate(KafkaTemplate<String, Money> kafkaTemplate) {
    return new MoneyPublisher(kafkaTemplate, topic);
  }

  public MoneyPublisher withTopic(String topic) {
    return new MoneyPublisher(kafkaTemplate, topic);
  }

  /** Publish a Money event */
  public CompletableFuture<SendResult<String, Money>> publish(String key, Money event) {
    return kafkaTemplate.send(topic, key, event);
  }
}
