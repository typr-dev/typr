package com.example.events;

import java.util.concurrent.CompletableFuture;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

/** Type-safe event publisher for address topic */
@Service
public record AddressPublisher(KafkaTemplate<String, Address> kafkaTemplate, String topic) {
  public AddressPublisher(KafkaTemplate<String, Address> kafkaTemplate) {
    this(kafkaTemplate, "address");
  }

  public AddressPublisher withKafkaTemplate(KafkaTemplate<String, Address> kafkaTemplate) {
    return new AddressPublisher(kafkaTemplate, topic);
  }

  public AddressPublisher withTopic(String topic) {
    return new AddressPublisher(kafkaTemplate, topic);
  }

  /** Publish a Address event */
  public CompletableFuture<SendResult<String, Address>> publish(String key, Address event) {
    return kafkaTemplate.send(topic, key, event);
  }
}
