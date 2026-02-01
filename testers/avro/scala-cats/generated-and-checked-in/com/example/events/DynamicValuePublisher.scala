package com.example.events

import cats.effect.IO
import fs2.kafka.KafkaProducer
import fs2.kafka.ProducerRecord
import fs2.kafka.ProducerRecords
import fs2.kafka.ProducerResult
import scala.annotation.unused

@unused
/** Type-safe event publisher for dynamic-value topic */
case class DynamicValuePublisher(
  kafkaTemplate: KafkaProducer[IO, String, DynamicValue],
  topic: String = "dynamic-value"
) {
  /** Publish a DynamicValue event */
  def publish(
    key: String,
    event: DynamicValue
  ): IO[ProducerResult[String, DynamicValue]] = {
    return kafkaTemplate.produce(ProducerRecords.one(ProducerRecord(topic, key, event))).flatten
  }
}