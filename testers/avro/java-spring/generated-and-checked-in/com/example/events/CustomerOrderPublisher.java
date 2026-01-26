package com.example.events;

import java.util.concurrent.CompletableFuture;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

/** Type-safe event publisher for customer-order topic */
@Service
public record CustomerOrderPublisher(
    KafkaTemplate<String, CustomerOrder> kafkaTemplate, String topic) {
  public CustomerOrderPublisher(KafkaTemplate<String, CustomerOrder> kafkaTemplate) {
    this(kafkaTemplate, "customer-order");
  }

  public CustomerOrderPublisher withKafkaTemplate(
      KafkaTemplate<String, CustomerOrder> kafkaTemplate) {
    return new CustomerOrderPublisher(kafkaTemplate, topic);
  }

  public CustomerOrderPublisher withTopic(String topic) {
    return new CustomerOrderPublisher(kafkaTemplate, topic);
  }

  /** Publish a CustomerOrder event */
  public CompletableFuture<SendResult<String, CustomerOrder>> publish(
      String key, CustomerOrder event) {
    return kafkaTemplate.send(topic, key, event);
  }
}
