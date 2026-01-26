package com.example.events.consumer

import com.example.events.OrderCancelled
import com.example.events.OrderEvents
import com.example.events.OrderPlaced
import com.example.events.OrderUpdated
import com.example.events.header.StandardHeaders
import java.lang.IllegalStateException

/** Handler interface for order-events topic events */
trait OrderEventsHandler {
  /** Handle a OrderCancelled event */
  def handleOrderCancelled(
    key: String,
    event: OrderCancelled,
    headers: StandardHeaders
  ): Unit

  /** Handle a OrderPlaced event */
  def handleOrderPlaced(
    key: String,
    event: OrderPlaced,
    headers: StandardHeaders
  ): Unit

  /** Handle a OrderUpdated event */
  def handleOrderUpdated(
    key: String,
    event: OrderUpdated,
    headers: StandardHeaders
  ): Unit

  /** Handle unknown event types (default throws exception) */
  def handleUnknown(
    key: String,
    event: OrderEvents,
    headers: StandardHeaders
  ): Unit = {
    throw new IllegalStateException("Unknown event type: " + event.getClass)
  }
}