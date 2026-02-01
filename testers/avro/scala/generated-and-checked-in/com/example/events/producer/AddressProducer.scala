package com.example.events.producer

import com.example.events.Address
import com.example.events.header.StandardHeaders
import java.lang.AutoCloseable
import java.util.concurrent.Future
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata

/** Type-safe producer for address topic */
case class AddressProducer(
  producer: Producer[String, Address],
  topic: String = "address"
) extends AutoCloseable {
  /** Send a message to the topic */
  def send(
    key: String,
    value: Address
  ): Future[RecordMetadata] = {
    return producer.send(new ProducerRecord[String, Address](topic, key, value))
  }

  /** Send a message with headers to the topic */
  def send(
    key: String,
    value: Address,
    headers: StandardHeaders
  ): Future[RecordMetadata] = {
    return producer.send(new ProducerRecord[String, Address](
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