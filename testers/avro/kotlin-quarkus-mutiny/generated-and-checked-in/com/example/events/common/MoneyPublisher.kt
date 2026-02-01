package com.example.events.common

import io.smallrye.mutiny.Uni
import io.smallrye.reactive.messaging.MutinyEmitter
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.lang.Void
import org.eclipse.microprofile.reactive.messaging.Channel

@ApplicationScoped
/** Type-safe event publisher for money topic */
data class MoneyPublisher @Inject constructor(
  @field:Channel("money") val kafkaTemplate: MutinyEmitter<Money>,
  val topic: kotlin.String = "money"
) {
  /** Publish a Money event */
  fun publish(
    key: kotlin.String,
    event: Money
  ): Uni<Void> {
    return kafkaTemplate.send(event)
  }
}