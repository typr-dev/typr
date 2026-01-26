package com.example.events.consumer

import com.example.events.OrderCancelled
import com.example.events.OrderEvents
import com.example.events.OrderPlaced
import com.example.events.OrderUpdated
import com.example.events.header.StandardHeaders
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

  /** Poll for messages and dispatch to handler */
  fun poll(timeout: Duration) {
    val records: ConsumerRecords<kotlin.String, OrderEvents> = consumer.poll(timeout)
    records.forEach({ record -> val key: kotlin.String = record.key()
    val value: OrderEvents = record.value()
    val headers: StandardHeaders = StandardHeaders.fromHeaders(record.headers())
    when (val __r = value) {
      is OrderCancelled -> { val e = __r as OrderCancelled; handler.handleOrderCancelled(key, e, headers) }
      is OrderPlaced -> { val e = __r as OrderPlaced; handler.handleOrderPlaced(key, e, headers) }
      is OrderUpdated -> { val e = __r as OrderUpdated; handler.handleOrderUpdated(key, e, headers) }
      else -> handler.handleUnknown(key, value, headers)
    } })
  }
}