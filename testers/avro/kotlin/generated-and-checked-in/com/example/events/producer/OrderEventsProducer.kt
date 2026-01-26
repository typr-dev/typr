package com.example.events.producer

import com.example.events.OrderEvents
import com.example.events.header.StandardHeaders
import java.io.Closeable
import java.util.concurrent.Future
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata

/** Type-safe producer for order-events topic */
data class OrderEventsProducer(
  val producer: Producer<kotlin.String, OrderEvents>,
  val topic: kotlin.String = "order-events"
) : Closeable {
  /** Close the producer */
  override fun close() {
    producer.close()
  }

  /** Send a message to the topic */
  fun send(
    key: kotlin.String,
    value: OrderEvents
  ): Future<RecordMetadata> {
    return producer.send(ProducerRecord<kotlin.String, OrderEvents>(topic, key, value))
  }

  /** Send a message with headers to the topic */
  fun send(
    key: kotlin.String,
    value: OrderEvents,
    headers: StandardHeaders
  ): Future<RecordMetadata> {
    return producer.send(ProducerRecord<kotlin.String, OrderEvents>(topic, null, key, value, headers.toHeaders()))
  }
}