package com.example.events.producer

import com.example.events.LinkedListNode
import com.example.events.header.StandardHeaders
import java.io.Closeable
import java.util.concurrent.Future
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata

/** Type-safe producer for linked-list-node topic */
data class LinkedListNodeProducer(
  val producer: Producer<kotlin.String, LinkedListNode>,
  val topic: kotlin.String = "linked-list-node"
) : Closeable {
  /** Close the producer */
  override fun close() {
    producer.close()
  }

  /** Send a message to the topic */
  fun send(
    key: kotlin.String,
    value: LinkedListNode
  ): Future<RecordMetadata> {
    return producer.send(ProducerRecord<kotlin.String, LinkedListNode>(topic, key, value))
  }

  /** Send a message with headers to the topic */
  fun send(
    key: kotlin.String,
    value: LinkedListNode,
    headers: StandardHeaders
  ): Future<RecordMetadata> {
    return producer.send(ProducerRecord<kotlin.String, LinkedListNode>(topic, null, key, value, headers.toHeaders()))
  }
}