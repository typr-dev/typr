package com.example.events;

import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.MutinyEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;

/** Type-safe event publisher for address topic */
@ApplicationScoped
public record AddressPublisher(
    @Channel("address") MutinyEmitter<Address> kafkaTemplate, String topic) {
  @Inject
  public AddressPublisher {}

  public AddressPublisher(@Channel("address") MutinyEmitter<Address> kafkaTemplate) {
    this(kafkaTemplate, "address");
  }

  public AddressPublisher withKafkaTemplate(MutinyEmitter<Address> kafkaTemplate) {
    return new AddressPublisher(kafkaTemplate, topic);
  }

  public AddressPublisher withTopic(String topic) {
    return new AddressPublisher(kafkaTemplate, topic);
  }

  /** Publish a Address event */
  public Uni<Void> publish(String key, Address event) {
    return kafkaTemplate.send(event);
  }
}
