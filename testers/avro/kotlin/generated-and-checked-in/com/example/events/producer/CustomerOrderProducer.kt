package com.example.events.producer

import com.example.events.CustomerOrder
import com.example.events.header.StandardHeaders
import java.io.Closeable
import java.util.concurrent.Future
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata

/** Type-safe producer for customer-order topic */
data class CustomerOrderProducer(
  val producer: Producer<kotlin.String, CustomerOrder>,
  val topic: kotlin.String = "customer-order"
) : Closeable {
  /** Close the producer */
  override fun close() {
    producer.close()
  }

  /** Send a message to the topic */
  fun send(
    key: kotlin.String,
    value: CustomerOrder
  ): Future<RecordMetadata> {
    return producer.send(ProducerRecord<kotlin.String, CustomerOrder>(topic, key, value))
  }

  /** Send a message with headers to the topic */
  fun send(
    key: kotlin.String,
    value: CustomerOrder,
    headers: StandardHeaders
  ): Future<RecordMetadata> {
    return producer.send(ProducerRecord<kotlin.String, CustomerOrder>(topic, null, key, value, headers.toHeaders()))
  }
}