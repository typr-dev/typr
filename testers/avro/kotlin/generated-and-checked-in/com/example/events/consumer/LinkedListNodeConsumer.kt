package com.example.events.consumer

import com.example.events.LinkedListNode
import com.example.events.header.StandardHeaders
import java.io.Closeable
import java.time.Duration
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecords

/** Type-safe consumer for linked-list-node topic */
data class LinkedListNodeConsumer(
  val consumer: Consumer<kotlin.String, LinkedListNode>,
  val handler: LinkedListNodeHandler,
  val topic: kotlin.String = "linked-list-node"
) : Closeable {
  /** Close the consumer */
  override fun close() {
    consumer.close()
  }

  /** Poll for messages and dispatch to handler */
  fun poll(timeout: Duration) {
    val records: ConsumerRecords<kotlin.String, LinkedListNode> = consumer.poll(timeout)
    records.forEach({ record -> val key: kotlin.String = record.key()
    val value: LinkedListNode = record.value()
    val headers: StandardHeaders = StandardHeaders.fromHeaders(record.headers())
    handler.handle(key, value, headers) })
  }
}