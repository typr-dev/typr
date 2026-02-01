package com.example.events;

import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.MutinyEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;

/** Type-safe event publisher for customer-order topic */
@ApplicationScoped
public record CustomerOrderPublisher(
    @Channel("customer-order") MutinyEmitter<CustomerOrder> kafkaTemplate, String topic) {
  @Inject
  public CustomerOrderPublisher {}

  public CustomerOrderPublisher(
      @Channel("customer-order") MutinyEmitter<CustomerOrder> kafkaTemplate) {
    this(kafkaTemplate, "customer-order");
  }

  public CustomerOrderPublisher withKafkaTemplate(MutinyEmitter<CustomerOrder> kafkaTemplate) {
    return new CustomerOrderPublisher(kafkaTemplate, topic);
  }

  public CustomerOrderPublisher withTopic(String topic) {
    return new CustomerOrderPublisher(kafkaTemplate, topic);
  }

  /** Publish a CustomerOrder event */
  public Uni<Void> publish(String key, CustomerOrder event) {
    return kafkaTemplate.send(event);
  }
}
