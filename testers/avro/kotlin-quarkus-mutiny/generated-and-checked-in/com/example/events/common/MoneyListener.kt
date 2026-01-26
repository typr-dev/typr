package com.example.events.common

import io.smallrye.mutiny.Uni
import java.lang.Void
import org.eclipse.microprofile.reactive.messaging.Incoming
import org.eclipse.microprofile.reactive.messaging.Message
import org.eclipse.microprofile.reactive.messaging.Metadata

/** Event listener interface for money topic. Implement this interface to handle events. */
interface MoneyListener {
  /** Handle Money event */
  abstract fun onMoney(
    event: Money,
    metadata: Metadata
  ): Uni<Void>

  /** Handle unknown event types. Override to customize behavior. */
  fun onUnknown(record: Message<Any>): Uni<Void> {
    return Uni.createFrom().voidItem()
  }

  /** Receive and dispatch events to handler methods */
  @Incoming("money")
  fun receive(record: Message<Any>): Uni<Void> {
    return when (val __r = record.getPayload()) {
      null -> onUnknown(record)
      is Money -> { val e = __r as Money; onMoney(e, record.getMetadata()) }
      else -> onUnknown(record)
    }
  }
}