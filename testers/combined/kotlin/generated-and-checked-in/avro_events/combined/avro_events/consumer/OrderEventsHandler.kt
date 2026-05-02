package combined.avro_events.consumer

import com.example.events.OrderCancelled
import com.example.events.OrderEvents
import com.example.events.OrderPlaced
import com.example.events.OrderUpdated
import com.example.events.PaymentCallback
import com.example.events.PaymentCharged
import combined.avro_events.header.StandardHeaders
import io.smallrye.mutiny.Uni
import java.lang.IllegalStateException

/** Handler interface for order-events topic events */
interface OrderEventsHandler {
  /** Handle a OrderCancelled event */
  abstract fun handleOrderCancelled(
    key: kotlin.String,
    event: OrderCancelled,
    headers: StandardHeaders
  ): Uni<Unit>

  /** Handle a OrderPlaced event */
  abstract fun handleOrderPlaced(
    key: kotlin.String,
    event: OrderPlaced,
    headers: StandardHeaders
  ): Uni<Unit>

  /** Handle a OrderUpdated event */
  abstract fun handleOrderUpdated(
    key: kotlin.String,
    event: OrderUpdated,
    headers: StandardHeaders
  ): Uni<Unit>

  /** Handle a PaymentCallback event */
  abstract fun handlePaymentCallback(
    key: kotlin.String,
    event: PaymentCallback,
    headers: StandardHeaders
  ): Uni<Unit>

  /** Handle a PaymentCharged event */
  abstract fun handlePaymentCharged(
    key: kotlin.String,
    event: PaymentCharged,
    headers: StandardHeaders
  ): Uni<Unit>

  /** Handle unknown event types (default throws exception) */
  fun handleUnknown(
    key: kotlin.String,
    event: OrderEvents,
    headers: StandardHeaders
  ): Uni<Unit> {
    throw IllegalStateException("Unknown event type: " + event.javaClass)
  }
}