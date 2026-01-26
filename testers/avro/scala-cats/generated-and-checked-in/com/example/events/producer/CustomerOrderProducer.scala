package com.example.events.producer

import cats.effect.IO
import com.example.events.CustomerOrder
import com.example.events.header.StandardHeaders
import java.lang.AutoCloseable
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata

/** Type-safe producer for customer-order topic */
case class CustomerOrderProducer(
  producer: Producer[String, CustomerOrder],
  topic: String = "customer-order"
) extends AutoCloseable {
  /** Send a message to the topic asynchronously */
  def send(
    key: String,
    value: CustomerOrder
  ): IO[RecordMetadata] = {
    return IO.async_(cb => { producer.send(new ProducerRecord[String, CustomerOrder](topic, key, value), (result, exception) => { if (exception != null) {
      cb(scala.Left(exception))
    } else {
      cb(scala.Right(result))
    } }) })
  }

  /** Send a message with headers to the topic asynchronously */
  def send(
    key: String,
    value: CustomerOrder,
    headers: StandardHeaders
  ): IO[RecordMetadata] = {
    return IO.async_(cb => { producer.send(new ProducerRecord[String, CustomerOrder](
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