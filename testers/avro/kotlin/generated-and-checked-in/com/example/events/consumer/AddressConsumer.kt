package com.example.events.consumer

import com.example.events.Address
import com.example.events.header.StandardHeaders
import java.io.Closeable
import java.time.Duration
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecords

/** Type-safe consumer for address topic */
data class AddressConsumer(
  val consumer: Consumer<kotlin.String, Address>,
  val handler: AddressHandler,
  val topic: kotlin.String = "address"
) : Closeable {
  /** Close the consumer */
  override fun close() {
    consumer.close()
  }

  /** Poll for messages and dispatch to handler */
  fun poll(timeout: Duration) {
    val records: ConsumerRecords<kotlin.String, Address> = consumer.poll(timeout)
    records.forEach({ record -> val key: kotlin.String = record.key()
    val value: Address = record.value()
    val headers: StandardHeaders = StandardHeaders.fromHeaders(record.headers())
    handler.handle(key, value, headers) })
  }
}