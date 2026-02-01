package com.example.events

import io.smallrye.mutiny.Uni
import io.smallrye.reactive.messaging.MutinyEmitter
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.lang.Void
import org.eclipse.microprofile.reactive.messaging.Channel

@ApplicationScoped
/** Type-safe event publisher for dynamic-value topic */
data class DynamicValuePublisher @Inject constructor(
  @field:Channel("dynamic-value") val kafkaTemplate: MutinyEmitter<DynamicValue>,
  val topic: kotlin.String = "dynamic-value"
) {
  /** Publish a DynamicValue event */
  fun publish(
    key: kotlin.String,
    event: DynamicValue
  ): Uni<Void> {
    return kafkaTemplate.send(event)
  }
}