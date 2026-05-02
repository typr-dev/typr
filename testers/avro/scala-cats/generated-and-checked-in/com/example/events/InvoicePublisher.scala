package com.example.events

import cats.effect.IO
import fs2.kafka.KafkaProducer
import fs2.kafka.ProducerRecord
import fs2.kafka.ProducerRecords
import fs2.kafka.ProducerResult
import scala.annotation.unused

@unused
/** Type-safe event publisher for invoice topic */
case class InvoicePublisher(
  kafkaTemplate: KafkaProducer[IO, String, Invoice],
  topic: String = "invoice"
) {
  /** Publish a Invoice event */
  def publish(
    key: String,
    event: Invoice
  ): IO[ProducerResult[String, Invoice]] = {
    return kafkaTemplate.produce(ProducerRecords.one(ProducerRecord(topic, key, event))).flatten
  }
}