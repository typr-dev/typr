package com.example.events.consumer

import cats.effect.IO
import com.example.events.TreeNode
import com.example.events.header.StandardHeaders
import java.lang.AutoCloseable
import java.time.Duration
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecords
import cats.syntax.all._
import scala.jdk.CollectionConverters._

/** Type-safe consumer for tree-node topic */
case class TreeNodeConsumer(
  consumer: Consumer[String, TreeNode],
  handler: TreeNodeHandler,
  topic: String = "tree-node"
) extends AutoCloseable {
  /** Poll for messages and dispatch to handler, returning composed effect */
  def poll(timeout: Duration): IO[Unit] = {
    val records: ConsumerRecords[String, TreeNode] = consumer.poll(timeout)
    return records.asScala.toList.traverse_(record => handler.handle(record.key, record.value, StandardHeaders.fromHeaders(record.headers)))
  }

  /** Close the consumer */
  override def close: Unit = {
    consumer.close
  }
}