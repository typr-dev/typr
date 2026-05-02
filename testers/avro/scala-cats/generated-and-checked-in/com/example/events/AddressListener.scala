package com.example.events

import cats.effect.IO
import fs2.kafka.ConsumerRecord
import fs2.kafka.Headers
import scala.annotation.unused

/** Event listener interface for address topic. Implement this interface to handle events. */
trait AddressListener {
  /** Receive and dispatch events to handler methods */
  @unused
  def receive(record: ConsumerRecord[String, Any]): IO[Unit] = {
    return record.value match {
      case null => onUnknown(record)
      case e: Address => onAddress(e, record.headers)
    }
  }

  /** Handle Address event */
  def onAddress(
    event: Address,
    metadata: Headers
  ): IO[Unit]

  /** Handle unknown event types. Override to customize behavior. */
  def onUnknown(record: ConsumerRecord[String, Any]): IO[Unit] = {
    return IO.unit
  }
}