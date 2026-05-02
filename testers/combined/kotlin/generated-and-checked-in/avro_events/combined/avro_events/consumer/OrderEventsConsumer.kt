package combined.avro_events.consumer

import com.example.events.OrderCancelled
import com.example.events.OrderEvents
import com.example.events.OrderPlaced
import com.example.events.OrderUpdated
import com.example.events.PaymentCallback
import com.example.events.PaymentCharged
import combined.avro_events.header.StandardHeaders
import io.smallrye.mutiny.Multi
import io.smallrye.mutiny.Uni
import java.io.Closeable
import java.time.Duration
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecords

/** Type-safe consumer for order-events topic */
data class OrderEventsConsumer(
  val consumer: Consumer<kotlin.String, OrderEvents>,
  val handler: OrderEventsHandler,
  val topic: kotlin.String = "order-events"
) : Closeable {
  /** Close the consumer */
  override fun close() {
    consumer.close()
  }

  /** Poll for messages and dispatch to handler, returning composed effect */
  fun poll(timeout: Duration): Uni<Unit> {
    val records: ConsumerRecords<kotlin.String, OrderEvents> = consumer.poll(timeout)
    return Multi.createFrom().iterable(records).onItem().transformToUniAndConcatenate({ record -> when (val __r = record.value()) {
      is OrderCancelled -> { val e = __r; handler.handleOrderCancelled(record.key(), e, StandardHeaders.fromHeaders(record.headers())) }
      is OrderPlaced -> { val e = __r; handler.handleOrderPlaced(record.key(), e, StandardHeaders.fromHeaders(record.headers())) }
      is OrderUpdated -> { val e = __r; handler.handleOrderUpdated(record.key(), e, StandardHeaders.fromHeaders(record.headers())) }
      is PaymentCallback -> { val e = __r; handler.handlePaymentCallback(record.key(), e, StandardHeaders.fromHeaders(record.headers())) }
      is PaymentCharged -> { val e = __r; handler.handlePaymentCharged(record.key(), e, StandardHeaders.fromHeaders(record.headers())) }
      else -> handler.handleUnknown(record.key(), record.value(), StandardHeaders.fromHeaders(record.headers()))
    } }).collect().last()
  }
}