package com.example.events.common;

import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.MutinyEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;

/** Type-safe event publisher for money topic */
@ApplicationScoped
public record MoneyPublisher(@Channel("money") MutinyEmitter<Money> kafkaTemplate, String topic) {
  @Inject
  public MoneyPublisher {}

  public MoneyPublisher(@Channel("money") MutinyEmitter<Money> kafkaTemplate) {
    this(kafkaTemplate, "money");
  }

  public MoneyPublisher withKafkaTemplate(MutinyEmitter<Money> kafkaTemplate) {
    return new MoneyPublisher(kafkaTemplate, topic);
  }

  public MoneyPublisher withTopic(String topic) {
    return new MoneyPublisher(kafkaTemplate, topic);
  }

  /** Publish a Money event */
  public Uni<Void> publish(String key, Money event) {
    return kafkaTemplate.send(event);
  }
}
