package com.example.events

import cats.effect.IO
import fs2.kafka.KafkaProducer
import fs2.kafka.ProducerRecord
import fs2.kafka.ProducerRecords
import fs2.kafka.ProducerResult
import scala.annotation.unused

@unused
/** Type-safe event publisher for customer-order topic */
case class CustomerOrderPublisher(
  kafkaTemplate: KafkaProducer[IO, String, CustomerOrder],
  topic: String = "customer-order"
) {
  /** Publish a CustomerOrder event */
  def publish(
    key: String,
    event: CustomerOrder
  ): IO[ProducerResult[String, CustomerOrder]] = {
    return kafkaTemplate.produce(ProducerRecords.one(ProducerRecord(topic, key, event))).flatten
  }
}