package com.example.events.producer

import com.example.events.common.Money
import com.example.events.header.StandardHeaders
import java.io.Closeable
import java.util.concurrent.Future
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata

/** Type-safe producer for money topic */
data class MoneyProducer(
  val producer: Producer<kotlin.String, Money>,
  val topic: kotlin.String = "money"
) : Closeable {
  /** Close the producer */
  override fun close() {
    producer.close()
  }

  /** Send a message to the topic */
  fun send(
    key: kotlin.String,
    value: Money
  ): Future<RecordMetadata> {
    return producer.send(ProducerRecord<kotlin.String, Money>(topic, key, value))
  }

  /** Send a message with headers to the topic */
  fun send(
    key: kotlin.String,
    value: Money,
    headers: StandardHeaders
  ): Future<RecordMetadata> {
    return producer.send(ProducerRecord<kotlin.String, Money>(topic, null, key, value, headers.toHeaders()))
  }
}