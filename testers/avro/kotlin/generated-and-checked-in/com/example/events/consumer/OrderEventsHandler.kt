package com.example.events.consumer

import com.example.events.OrderCancelled
import com.example.events.OrderEvents
import com.example.events.OrderPlaced
import com.example.events.OrderUpdated
import com.example.events.header.StandardHeaders
import java.lang.IllegalStateException

/** Handler interface for order-events topic events */
interface OrderEventsHandler {
  /** Handle a OrderCancelled event */
  abstract fun handleOrderCancelled(
    key: kotlin.String,
    event: OrderCancelled,
    headers: StandardHeaders
  )

  /** Handle a OrderPlaced event */
  abstract fun handleOrderPlaced(
    key: kotlin.String,
    event: OrderPlaced,
    headers: StandardHeaders
  )

  /** Handle a OrderUpdated event */
  abstract fun handleOrderUpdated(
    key: kotlin.String,
    event: OrderUpdated,
    headers: StandardHeaders
  )

  /** Handle unknown event types (default throws exception) */
  fun handleUnknown(
    key: kotlin.String,
    event: OrderEvents,
    headers: StandardHeaders
  ) {
    throw IllegalStateException("Unknown event type: " + event.javaClass)
  }
}