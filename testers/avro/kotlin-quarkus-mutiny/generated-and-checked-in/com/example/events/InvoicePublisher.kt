package com.example.events

import io.smallrye.mutiny.Uni
import io.smallrye.reactive.messaging.MutinyEmitter
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.lang.Void
import org.eclipse.microprofile.reactive.messaging.Channel

@ApplicationScoped
/** Type-safe event publisher for invoice topic */
data class InvoicePublisher @Inject constructor(
  @field:Channel("invoice") val kafkaTemplate: MutinyEmitter<Invoice>,
  val topic: kotlin.String = "invoice"
) {
  /** Publish a Invoice event */
  fun publish(
    key: kotlin.String,
    event: Invoice
  ): Uni<Void> {
    return kafkaTemplate.send(event)
  }
}