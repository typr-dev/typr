package com.example.events

import cats.effect.IO
import fs2.kafka.ConsumerRecord
import fs2.kafka.Headers
import scala.annotation.unused

/** Event listener interface for dynamic-value topic. Implement this interface to handle events. */
trait DynamicValueListener {
  /** Receive and dispatch events to handler methods */
  @unused
  def receive(record: ConsumerRecord[String, Any]): IO[Unit] = {
    return record.value match {
      case null => onUnknown(record)
      case e: DynamicValue => onDynamicValue(e, record.headers)
    }
  }

  /** Handle DynamicValue event */
  def onDynamicValue(
    event: DynamicValue,
    metadata: Headers
  ): IO[Unit]

  /** Handle unknown event types. Override to customize behavior. */
  def onUnknown(record: ConsumerRecord[String, Any]): IO[Unit] = {
    return IO.unit
  }
}