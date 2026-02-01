package com.example.events.consumer

import com.example.events.CustomerOrder
import com.example.events.header.StandardHeaders

/** Handler interface for customer-order topic events */
trait CustomerOrderHandler {
  /** Handle a message from the topic */
  def handle(
    key: String,
    value: CustomerOrder,
    headers: StandardHeaders
  ): Unit
}