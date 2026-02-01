package com.example.events.producer

import com.example.events.Invoice
import com.example.events.header.StandardHeaders
import java.io.Closeable
import java.util.concurrent.Future
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata

/** Type-safe producer for invoice topic */
data class InvoiceProducer(
  val producer: Producer<kotlin.String, Invoice>,
  val topic: kotlin.String = "invoice"
) : Closeable {
  /** Close the producer */
  override fun close() {
    producer.close()
  }

  /** Send a message to the topic */
  fun send(
    key: kotlin.String,
    value: Invoice
  ): Future<RecordMetadata> {
    return producer.send(ProducerRecord<kotlin.String, Invoice>(topic, key, value))
  }

  /** Send a message with headers to the topic */
  fun send(
    key: kotlin.String,
    value: Invoice,
    headers: StandardHeaders
  ): Future<RecordMetadata> {
    return producer.send(ProducerRecord<kotlin.String, Invoice>(topic, null, key, value, headers.toHeaders()))
  }
}