package com.example.events

import io.smallrye.mutiny.Uni
import io.smallrye.reactive.messaging.MutinyEmitter
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.lang.Void
import org.eclipse.microprofile.reactive.messaging.Channel

@ApplicationScoped
/** Type-safe event publisher for customer-order topic */
data class CustomerOrderPublisher @Inject constructor(
  @field:Channel("customer-order") val kafkaTemplate: MutinyEmitter<CustomerOrder>,
  val topic: kotlin.String = "customer-order"
) {
  /** Publish a CustomerOrder event */
  fun publish(
    key: kotlin.String,
    event: CustomerOrder
  ): Uni<Void> {
    return kafkaTemplate.send(event)
  }
}