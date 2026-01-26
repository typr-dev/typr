package com.example.events.consumer

import com.example.events.Invoice
import com.example.events.header.StandardHeaders
import java.io.Closeable
import java.time.Duration
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecords

/** Type-safe consumer for invoice topic */
data class InvoiceConsumer(
  val consumer: Consumer<kotlin.String, Invoice>,
  val handler: InvoiceHandler,
  val topic: kotlin.String = "invoice"
) : Closeable {
  /** Close the consumer */
  override fun close() {
    consumer.close()
  }

  /** Poll for messages and dispatch to handler */
  fun poll(timeout: Duration) {
    val records: ConsumerRecords<kotlin.String, Invoice> = consumer.poll(timeout)
    records.forEach({ record -> val key: kotlin.String = record.key()
    val value: Invoice = record.value()
    val headers: StandardHeaders = StandardHeaders.fromHeaders(record.headers())
    handler.handle(key, value, headers) })
  }
}