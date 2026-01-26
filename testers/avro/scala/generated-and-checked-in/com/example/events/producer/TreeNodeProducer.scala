package com.example.events.producer

import com.example.events.TreeNode
import com.example.events.header.StandardHeaders
import java.lang.AutoCloseable
import java.util.concurrent.Future
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata

/** Type-safe producer for tree-node topic */
case class TreeNodeProducer(
  producer: Producer[String, TreeNode],
  topic: String = "tree-node"
) extends AutoCloseable {
  /** Send a message to the topic */
  def send(
    key: String,
    value: TreeNode
  ): Future[RecordMetadata] = {
    return producer.send(new ProducerRecord[String, TreeNode](topic, key, value))
  }

  /** Send a message with headers to the topic */
  def send(
    key: String,
    value: TreeNode,
    headers: StandardHeaders
  ): Future[RecordMetadata] = {
    return producer.send(new ProducerRecord[String, TreeNode](
      topic,
      null,
      key,
      value,
      headers.toHeaders
    ))
  }

  /** Close the producer */
  override def close: Unit = {
    producer.close
  }
}