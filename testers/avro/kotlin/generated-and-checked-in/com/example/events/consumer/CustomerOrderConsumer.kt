package com.example.events.consumer

import com.example.events.CustomerOrder
import com.example.events.header.StandardHeaders
import java.io.Closeable
import java.time.Duration
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecords

/** Type-safe consumer for customer-order topic */
data class CustomerOrderConsumer(
  val consumer: Consumer<kotlin.String, CustomerOrder>,
  val handler: CustomerOrderHandler,
  val topic: kotlin.String = "customer-order"
) : Closeable {
  /** Close the consumer */
  override fun close() {
    consumer.close()
  }

  /** Poll for messages and dispatch to handler */
  fun poll(timeout: Duration) {
    val records: ConsumerRecords<kotlin.String, CustomerOrder> = consumer.poll(timeout)
    records.forEach({ record -> val key: kotlin.String = record.key()
    val value: CustomerOrder = record.value()
    val headers: StandardHeaders = StandardHeaders.fromHeaders(record.headers())
    handler.handle(key, value, headers) })
  }
}