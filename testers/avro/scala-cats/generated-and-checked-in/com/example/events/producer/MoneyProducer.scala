package com.example.events.producer

import cats.effect.IO
import com.example.events.common.Money
import com.example.events.header.StandardHeaders
import java.lang.AutoCloseable
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata

/** Type-safe producer for money topic */
case class MoneyProducer(
  producer: Producer[String, Money],
  topic: String = "money"
) extends AutoCloseable {
  /** Send a message to the topic asynchronously */
  def send(
    key: String,
    value: Money
  ): IO[RecordMetadata] = {
    return IO.async_(cb => { producer.send(new ProducerRecord[String, Money](topic, key, value), (result, exception) => { if (exception != null) {
      cb(scala.Left(exception))
    } else {
      cb(scala.Right(result))
    } }) })
  }

  /** Send a message with headers to the topic asynchronously */
  def send(
    key: String,
    value: Money,
    headers: StandardHeaders
  ): IO[RecordMetadata] = {
    return IO.async_(cb => { producer.send(new ProducerRecord[String, Money](
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