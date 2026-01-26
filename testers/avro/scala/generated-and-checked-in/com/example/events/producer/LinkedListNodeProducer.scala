package com.example.events.producer

import com.example.events.LinkedListNode
import com.example.events.header.StandardHeaders
import java.lang.AutoCloseable
import java.util.concurrent.Future
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata

/** Type-safe producer for linked-list-node topic */
case class LinkedListNodeProducer(
  producer: Producer[String, LinkedListNode],
  topic: String = "linked-list-node"
) extends AutoCloseable {
  /** Send a message to the topic */
  def send(
    key: String,
    value: LinkedListNode
  ): Future[RecordMetadata] = {
    return producer.send(new ProducerRecord[String, LinkedListNode](topic, key, value))
  }

  /** Send a message with headers to the topic */
  def send(
    key: String,
    value: LinkedListNode,
    headers: StandardHeaders
  ): Future[RecordMetadata] = {
    return producer.send(new ProducerRecord[String, LinkedListNode](
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