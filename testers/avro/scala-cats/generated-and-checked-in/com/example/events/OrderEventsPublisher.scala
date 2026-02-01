package com.example.events

import cats.effect.IO
import fs2.kafka.KafkaProducer
import fs2.kafka.ProducerRecord
import fs2.kafka.ProducerRecords
import fs2.kafka.ProducerResult
import scala.annotation.unused

@unused
/** Type-safe event publisher for order-events topic */
case class OrderEventsPublisher(
  kafkaTemplate: KafkaProducer[IO, String, OrderEvents],
  topic: String = "order-events"
) {
  /** Publish a OrderCancelled event */
  def publish(
    key: String,
    event: OrderCancelled
  ): IO[ProducerResult[String, OrderEvents]] = {
    return kafkaTemplate.produce(ProducerRecords.one(ProducerRecord(topic, key, event))).flatten
  }

  /** Publish a OrderPlaced event */
  def publish(
    key: String,
    event: OrderPlaced
  ): IO[ProducerResult[String, OrderEvents]] = {
    return kafkaTemplate.produce(ProducerRecords.one(ProducerRecord(topic, key, event))).flatten
  }

  /** Publish a OrderUpdated event */
  def publish(
    key: String,
    event: OrderUpdated
  ): IO[ProducerResult[String, OrderEvents]] = {
    return kafkaTemplate.produce(ProducerRecords.one(ProducerRecord(topic, key, event))).flatten
  }
}