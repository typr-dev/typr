package com.example.events;

import java.util.concurrent.CompletableFuture;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

/** Type-safe event publisher for invoice topic */
@Service
public record InvoicePublisher(KafkaTemplate<String, Invoice> kafkaTemplate, String topic) {
  public InvoicePublisher(KafkaTemplate<String, Invoice> kafkaTemplate) {
    this(kafkaTemplate, "invoice");
  }

  public InvoicePublisher withKafkaTemplate(KafkaTemplate<String, Invoice> kafkaTemplate) {
    return new InvoicePublisher(kafkaTemplate, topic);
  }

  public InvoicePublisher withTopic(String topic) {
    return new InvoicePublisher(kafkaTemplate, topic);
  }

  /** Publish a Invoice event */
  public CompletableFuture<SendResult<String, Invoice>> publish(String key, Invoice event) {
    return kafkaTemplate.send(topic, key, event);
  }
}
