package com.example.events

import cats.effect.IO
import fs2.kafka.ConsumerRecord
import fs2.kafka.Headers
import scala.annotation.unused

/** Event listener interface for order-events topic. Implement this interface to handle events. */
trait OrderEventsListener {
  /** Receive and dispatch events to handler methods */
  @unused
  def receive(record: ConsumerRecord[String, Any]): IO[Unit] = {
    return record.value match {
      case null => onUnknown(record)
      case e: OrderCancelled => onOrderCancelled(e, record.headers)
      case e: OrderPlaced => onOrderPlaced(e, record.headers)
      case e: OrderUpdated => onOrderUpdated(e, record.headers)
    }
  }

  /** Handle OrderCancelled event */
  def onOrderCancelled(
    event: OrderCancelled,
    metadata: Headers
  ): IO[Unit]

  /** Handle OrderPlaced event */
  def onOrderPlaced(
    event: OrderPlaced,
    metadata: Headers
  ): IO[Unit]

  /** Handle OrderUpdated event */
  def onOrderUpdated(
    event: OrderUpdated,
    metadata: Headers
  ): IO[Unit]

  /** Handle unknown event types. Override to customize behavior. */
  def onUnknown(record: ConsumerRecord[String, Any]): IO[Unit] = {
    return IO.unit
  }
}