package com.example.events

import cats.effect.IO
import fs2.kafka.ConsumerRecord
import fs2.kafka.Headers
import scala.annotation.unused

/** Event listener interface for tree-node topic. Implement this interface to handle events. */
trait TreeNodeListener {
  /** Receive and dispatch events to handler methods */
  @unused
  def receive(record: ConsumerRecord[String, Any]): IO[Unit] = {
    return record.value match {
      case null => onUnknown(record)
      case e: TreeNode => onTreeNode(e, record.headers)
    }
  }

  /** Handle TreeNode event */
  def onTreeNode(
    event: TreeNode,
    metadata: Headers
  ): IO[Unit]

  /** Handle unknown event types. Override to customize behavior. */
  def onUnknown(record: ConsumerRecord[String, Any]): IO[Unit] = {
    return IO.unit
  }
}