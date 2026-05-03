package com.example.events.common

import cats.effect.IO
import fs2.kafka.KafkaProducer
import fs2.kafka.ProducerRecord
import fs2.kafka.ProducerRecords
import fs2.kafka.ProducerResult
import scala.annotation.unused

@unused
/** Type-safe event publisher for money topic */
case class MoneyPublisher(
  kafkaTemplate: KafkaProducer[IO, String, Money],
  topic: String = "money"
) {
  /** Publish a Money event */
  def publish(
    key: String,
    event: Money
  ): IO[ProducerResult[String, Money]] = {
    return kafkaTemplate.produce(ProducerRecords.one(ProducerRecord(topic, key, event))).flatten
  }
}