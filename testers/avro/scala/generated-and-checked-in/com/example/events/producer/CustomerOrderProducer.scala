package com.example.events.producer

import com.example.events.CustomerOrder
import com.example.events.header.StandardHeaders
import java.lang.AutoCloseable
import java.util.concurrent.Future
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata

/** Type-safe producer for customer-order topic */
case class CustomerOrderProducer(
  producer: Producer[String, CustomerOrder],
  topic: String = "customer-order"
) extends AutoCloseable {
  /** Send a message to the topic */
  def send(
    key: String,
    value: CustomerOrder
  ): Future[RecordMetadata] = {
    return producer.send(new ProducerRecord[String, CustomerOrder](topic, key, value))
  }

  /** Send a message with headers to the topic */
  def send(
    key: String,
    value: CustomerOrder,
    headers: StandardHeaders
  ): Future[RecordMetadata] = {
    return producer.send(new ProducerRecord[String, CustomerOrder](
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