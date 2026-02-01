package com.example.events

import io.smallrye.mutiny.Uni
import io.smallrye.reactive.messaging.MutinyEmitter
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.lang.Void
import org.eclipse.microprofile.reactive.messaging.Channel

@ApplicationScoped
/** Type-safe event publisher for address topic */
data class AddressPublisher @Inject constructor(
  @field:Channel("address") val kafkaTemplate: MutinyEmitter<Address>,
  val topic: kotlin.String = "address"
) {
  /** Publish a Address event */
  fun publish(
    key: kotlin.String,
    event: Address
  ): Uni<Void> {
    return kafkaTemplate.send(event)
  }
}