package com.example.events.consumer

import com.example.events.DynamicValue
import com.example.events.header.StandardHeaders
import java.io.Closeable
import java.time.Duration
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecords

/** Type-safe consumer for dynamic-value topic */
data class DynamicValueConsumer(
  val consumer: Consumer<kotlin.String, DynamicValue>,
  val handler: DynamicValueHandler,
  val topic: kotlin.String = "dynamic-value"
) : Closeable {
  /** Close the consumer */
  override fun close() {
    consumer.close()
  }

  /** Poll for messages and dispatch to handler */
  fun poll(timeout: Duration) {
    val records: ConsumerRecords<kotlin.String, DynamicValue> = consumer.poll(timeout)
    records.forEach({ record -> val key: kotlin.String = record.key()
    val value: DynamicValue = record.value()
    val headers: StandardHeaders = StandardHeaders.fromHeaders(record.headers())
    handler.handle(key, value, headers) })
  }
}