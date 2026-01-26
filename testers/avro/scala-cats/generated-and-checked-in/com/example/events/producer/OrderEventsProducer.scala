package com.example.events.producer

import cats.effect.IO
import com.example.events.OrderEvents
import com.example.events.header.StandardHeaders
import java.lang.AutoCloseable
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata

/** Type-safe producer for order-events topic */
case class OrderEventsProducer(
  producer: Producer[String, OrderEvents],
  topic: String = "order-events"
) extends AutoCloseable {
  /** Send a message to the topic asynchronously */
  def send(
    key: String,
    value: OrderEvents
  ): IO[RecordMetadata] = {
    return IO.async_(cb => { producer.send(new ProducerRecord[String, OrderEvents](topic, key, value), (result, exception) => { if (exception != null) {
      cb(scala.Left(exception))
    } else {
      cb(scala.Right(result))
    } }) })
  }

  /** Send a message with headers to the topic asynchronously */
  def send(
    key: String,
    value: OrderEvents,
    headers: StandardHeaders
  ): IO[RecordMetadata] = {
    return IO.async_(cb => { producer.send(new ProducerRecord[String, OrderEvents](
      topic,
      null,
      key,
      value,
      headers.toHeaders
    ), (result, exception) => { if (exception != null) {
      cb(scala.Left(exception))
    } else {
      cb(scala.Right(result))
    } }) })
  }

  /** Close the producer */
  override def close: Unit = {
    producer.close
  }
}