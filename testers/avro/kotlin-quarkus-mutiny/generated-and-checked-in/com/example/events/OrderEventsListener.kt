package com.example.events

import io.smallrye.mutiny.Uni
import java.lang.Void
import org.eclipse.microprofile.reactive.messaging.Incoming
import org.eclipse.microprofile.reactive.messaging.Message
import org.eclipse.microprofile.reactive.messaging.Metadata

/** Event listener interface for order-events topic. Implement this interface to handle events. */
interface OrderEventsListener {
  /** Handle OrderCancelled event */
  abstract fun onOrderCancelled(
    event: OrderCancelled,
    metadata: Metadata
  ): Uni<Void>

  /** Handle OrderPlaced event */
  abstract fun onOrderPlaced(
    event: OrderPlaced,
    metadata: Metadata
  ): Uni<Void>

  /** Handle OrderUpdated event */
  abstract fun onOrderUpdated(
    event: OrderUpdated,
    metadata: Metadata
  ): Uni<Void>

  /** Handle unknown event types. Override to customize behavior. */
  fun onUnknown(record: Message<Any>): Uni<Void> {
    return Uni.createFrom().voidItem()
  }

  /** Receive and dispatch events to handler methods */
  @Incoming("order-events")
  fun receive(record: Message<Any>): Uni<Void> {
    return when (val __r = record.getPayload()) {
      null -> onUnknown(record)
      is OrderCancelled -> { val e = __r as OrderCancelled; onOrderCancelled(e, record.getMetadata()) }
      is OrderPlaced -> { val e = __r as OrderPlaced; onOrderPlaced(e, record.getMetadata()) }
      is OrderUpdated -> { val e = __r as OrderUpdated; onOrderUpdated(e, record.getMetadata()) }
      else -> onUnknown(record)
    }
  }
}