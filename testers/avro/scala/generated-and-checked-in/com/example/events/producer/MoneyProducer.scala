package com.example.events.producer

import com.example.events.common.Money
import com.example.events.header.StandardHeaders
import java.lang.AutoCloseable
import java.util.concurrent.Future
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata

/** Type-safe producer for money topic */
case class MoneyProducer(
  producer: Producer[String, Money],
  topic: String = "money"
) extends AutoCloseable {
  /** Send a message to the topic */
  def send(
    key: String,
    value: Money
  ): Future[RecordMetadata] = {
    return producer.send(new ProducerRecord[String, Money](topic, key, value))
  }

  /** Send a message with headers to the topic */
  def send(
    key: String,
    value: Money,
    headers: StandardHeaders
  ): Future[RecordMetadata] = {
    return producer.send(new ProducerRecord[String, Money](
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