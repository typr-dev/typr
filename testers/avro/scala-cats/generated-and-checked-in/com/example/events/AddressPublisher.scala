package com.example.events

import cats.effect.IO
import fs2.kafka.KafkaProducer
import fs2.kafka.ProducerRecord
import fs2.kafka.ProducerRecords
import fs2.kafka.ProducerResult
import scala.annotation.unused

@unused
/** Type-safe event publisher for address topic */
case class AddressPublisher(
  kafkaTemplate: KafkaProducer[IO, String, Address],
  topic: String = "address"
) {
  /** Publish a Address event */
  def publish(
    key: String,
    event: Address
  ): IO[ProducerResult[String, Address]] = {
    return kafkaTemplate.produce(ProducerRecords.one(ProducerRecord(topic, key, event))).flatten
  }
}