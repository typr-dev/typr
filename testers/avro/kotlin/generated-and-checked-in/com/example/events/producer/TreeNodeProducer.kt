package com.example.events.producer

import com.example.events.TreeNode
import com.example.events.header.StandardHeaders
import java.io.Closeable
import java.util.concurrent.Future
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata

/** Type-safe producer for tree-node topic */
data class TreeNodeProducer(
  val producer: Producer<kotlin.String, TreeNode>,
  val topic: kotlin.String = "tree-node"
) : Closeable {
  /** Close the producer */
  override fun close() {
    producer.close()
  }

  /** Send a message to the topic */
  fun send(
    key: kotlin.String,
    value: TreeNode
  ): Future<RecordMetadata> {
    return producer.send(ProducerRecord<kotlin.String, TreeNode>(topic, key, value))
  }

  /** Send a message with headers to the topic */
  fun send(
    key: kotlin.String,
    value: TreeNode,
    headers: StandardHeaders
  ): Future<RecordMetadata> {
    return producer.send(ProducerRecord<kotlin.String, TreeNode>(topic, null, key, value, headers.toHeaders()))
  }
}