package com.example.events

import cats.effect.IO
import fs2.kafka.KafkaProducer
import fs2.kafka.ProducerRecord
import fs2.kafka.ProducerRecords
import fs2.kafka.ProducerResult
import scala.annotation.unused

@unused
/** Type-safe event publisher for linked-list-node topic */
case class LinkedListNodePublisher(
  kafkaTemplate: KafkaProducer[IO, String, LinkedListNode],
  topic: String = "linked-list-node"
) {
  /** Publish a LinkedListNode event */
  def publish(
    key: String,
    event: LinkedListNode
  ): IO[ProducerResult[String, LinkedListNode]] = {
    return kafkaTemplate.produce(ProducerRecords.one(ProducerRecord(topic, key, event))).flatten
  }
}