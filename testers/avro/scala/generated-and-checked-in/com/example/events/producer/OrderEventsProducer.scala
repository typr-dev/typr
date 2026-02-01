package com.example.events.producer

import com.example.events.OrderEvents
import com.example.events.header.StandardHeaders
import java.lang.AutoCloseable
import java.util.concurrent.Future
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata

/** Type-safe producer for order-events topic */
case class OrderEventsProducer(
  producer: Producer[String, OrderEvents],
  topic: String = "order-events"
) extends AutoCloseable {
  /** Send a message to the topic */
  def send(
    key: String,
    value: OrderEvents
  ): Future[RecordMetadata] = {
    return producer.send(new ProducerRecord[String, OrderEvents](topic, key, value))
  }

  /** Send a message with headers to the topic */
  def send(
    key: String,
    value: OrderEvents,
    headers: StandardHeaders
  ): Future[RecordMetadata] = {
    return producer.send(new ProducerRecord[String, OrderEvents](
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