package com.example.events.common

import cats.effect.IO
import fs2.kafka.ConsumerRecord
import fs2.kafka.Headers
import scala.annotation.unused

/** Event listener interface for money topic. Implement this interface to handle events. */
trait MoneyListener {
  /** Receive and dispatch events to handler methods */
  @unused
  def receive(record: ConsumerRecord[String, Any]): IO[Unit] = {
    return record.value match {
      case null => onUnknown(record)
      case e: Money => onMoney(e, record.headers)
    }
  }

  /** Handle Money event */
  def onMoney(
    event: Money,
    metadata: Headers
  ): IO[Unit]

  /** Handle unknown event types. Override to customize behavior. */
  def onUnknown(record: ConsumerRecord[String, Any]): IO[Unit] = {
    return IO.unit
  }
}