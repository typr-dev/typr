package com.example.events

import cats.effect.IO
import fs2.kafka.KafkaProducer
import fs2.kafka.ProducerRecord
import fs2.kafka.ProducerRecords
import fs2.kafka.ProducerResult
import scala.annotation.unused

@unused
/** Type-safe event publisher for tree-node topic */
case class TreeNodePublisher(
  kafkaTemplate: KafkaProducer[IO, String, TreeNode],
  topic: String = "tree-node"
) {
  /** Publish a TreeNode event */
  def publish(
    key: String,
    event: TreeNode
  ): IO[ProducerResult[String, TreeNode]] = {
    return kafkaTemplate.produce(ProducerRecords.one(ProducerRecord(topic, key, event))).flatten
  }
}