package com.example.events;

import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.MutinyEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;

/** Type-safe event publisher for invoice topic */
@ApplicationScoped
public record InvoicePublisher(
    @Channel("invoice") MutinyEmitter<Invoice> kafkaTemplate, String topic) {
  @Inject
  public InvoicePublisher {}

  public InvoicePublisher(@Channel("invoice") MutinyEmitter<Invoice> kafkaTemplate) {
    this(kafkaTemplate, "invoice");
  }

  public InvoicePublisher withKafkaTemplate(MutinyEmitter<Invoice> kafkaTemplate) {
    return new InvoicePublisher(kafkaTemplate, topic);
  }

  public InvoicePublisher withTopic(String topic) {
    return new InvoicePublisher(kafkaTemplate, topic);
  }

  /** Publish a Invoice event */
  public Uni<Void> publish(String key, Invoice event) {
    return kafkaTemplate.send(event);
  }
}
