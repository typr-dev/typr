package com.example.events

import io.smallrye.mutiny.Uni
import io.smallrye.reactive.messaging.MutinyEmitter
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.lang.Void
import org.eclipse.microprofile.reactive.messaging.Channel

@ApplicationScoped
/** Type-safe event publisher for order-events topic */
data class OrderEventsPublisher @Inject constructor(
  @field:Channel("order-events") val kafkaTemplate: MutinyEmitter<OrderEvents>,
  val topic: kotlin.String = "order-events"
) {
  /** Publish a OrderCancelled event */
  fun publish(
    key: kotlin.String,
    event: OrderCancelled
  ): Uni<Void> {
    return kafkaTemplate.send(event)
  }

  /** Publish a OrderPlaced event */
  fun publish(
    key: kotlin.String,
    event: OrderPlaced
  ): Uni<Void> {
    return kafkaTemplate.send(event)
  }

  /** Publish a OrderUpdated event */
  fun publish(
    key: kotlin.String,
    event: OrderUpdated
  ): Uni<Void> {
    return kafkaTemplate.send(event)
  }
}