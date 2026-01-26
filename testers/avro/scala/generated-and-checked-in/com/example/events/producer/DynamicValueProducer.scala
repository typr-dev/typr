package com.example.events.producer

import com.example.events.DynamicValue
import com.example.events.header.StandardHeaders
import java.lang.AutoCloseable
import java.util.concurrent.Future
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata

/** Type-safe producer for dynamic-value topic */
case class DynamicValueProducer(
  producer: Producer[String, DynamicValue],
  topic: String = "dynamic-value"
) extends AutoCloseable {
  /** Send a message to the topic */
  def send(
    key: String,
    value: DynamicValue
  ): Future[RecordMetadata] = {
    return producer.send(new ProducerRecord[String, DynamicValue](topic, key, value))
  }

  /** Send a message with headers to the topic */
  def send(
    key: String,
    value: DynamicValue,
    headers: StandardHeaders
  ): Future[RecordMetadata] = {
    return producer.send(new ProducerRecord[String, DynamicValue](
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