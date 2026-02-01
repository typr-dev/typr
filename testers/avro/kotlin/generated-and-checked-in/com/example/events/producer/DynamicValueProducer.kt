package com.example.events.producer

import com.example.events.DynamicValue
import com.example.events.header.StandardHeaders
import java.io.Closeable
import java.util.concurrent.Future
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata

/** Type-safe producer for dynamic-value topic */
data class DynamicValueProducer(
  val producer: Producer<kotlin.String, DynamicValue>,
  val topic: kotlin.String = "dynamic-value"
) : Closeable {
  /** Close the producer */
  override fun close() {
    producer.close()
  }

  /** Send a message to the topic */
  fun send(
    key: kotlin.String,
    value: DynamicValue
  ): Future<RecordMetadata> {
    return producer.send(ProducerRecord<kotlin.String, DynamicValue>(topic, key, value))
  }

  /** Send a message with headers to the topic */
  fun send(
    key: kotlin.String,
    value: DynamicValue,
    headers: StandardHeaders
  ): Future<RecordMetadata> {
    return producer.send(ProducerRecord<kotlin.String, DynamicValue>(topic, null, key, value, headers.toHeaders()))
  }
}