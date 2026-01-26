package com.example.events;

import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.MutinyEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;

/** Type-safe event publisher for dynamic-value topic */
@ApplicationScoped
public record DynamicValuePublisher(
    @Channel("dynamic-value") MutinyEmitter<DynamicValue> kafkaTemplate, String topic) {
  @Inject
  public DynamicValuePublisher {}

  public DynamicValuePublisher(
      @Channel("dynamic-value") MutinyEmitter<DynamicValue> kafkaTemplate) {
    this(kafkaTemplate, "dynamic-value");
  }

  public DynamicValuePublisher withKafkaTemplate(MutinyEmitter<DynamicValue> kafkaTemplate) {
    return new DynamicValuePublisher(kafkaTemplate, topic);
  }

  public DynamicValuePublisher withTopic(String topic) {
    return new DynamicValuePublisher(kafkaTemplate, topic);
  }

  /** Publish a DynamicValue event */
  public Uni<Void> publish(String key, DynamicValue event) {
    return kafkaTemplate.send(event);
  }
}
