package com.example.events.consumer

import com.example.events.common.Money
import com.example.events.header.StandardHeaders
import java.io.Closeable
import java.time.Duration
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecords

/** Type-safe consumer for money topic */
data class MoneyConsumer(
  val consumer: Consumer<kotlin.String, Money>,
  val handler: MoneyHandler,
  val topic: kotlin.String = "money"
) : Closeable {
  /** Close the consumer */
  override fun close() {
    consumer.close()
  }

  /** Poll for messages and dispatch to handler */
  fun poll(timeout: Duration) {
    val records: ConsumerRecords<kotlin.String, Money> = consumer.poll(timeout)
    records.forEach({ record -> val key: kotlin.String = record.key()
    val value: Money = record.value()
    val headers: StandardHeaders = StandardHeaders.fromHeaders(record.headers())
    handler.handle(key, value, headers) })
  }
}