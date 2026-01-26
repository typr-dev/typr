package com.example.events.consumer

import com.example.events.TreeNode
import com.example.events.header.StandardHeaders
import java.io.Closeable
import java.time.Duration
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecords

/** Type-safe consumer for tree-node topic */
data class TreeNodeConsumer(
  val consumer: Consumer<kotlin.String, TreeNode>,
  val handler: TreeNodeHandler,
  val topic: kotlin.String = "tree-node"
) : Closeable {
  /** Close the consumer */
  override fun close() {
    consumer.close()
  }

  /** Poll for messages and dispatch to handler */
  fun poll(timeout: Duration) {
    val records: ConsumerRecords<kotlin.String, TreeNode> = consumer.poll(timeout)
    records.forEach({ record -> val key: kotlin.String = record.key()
    val value: TreeNode = record.value()
    val headers: StandardHeaders = StandardHeaders.fromHeaders(record.headers())
    handler.handle(key, value, headers) })
  }
}