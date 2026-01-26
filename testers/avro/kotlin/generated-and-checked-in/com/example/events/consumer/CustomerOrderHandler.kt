package com.example.events.consumer

import com.example.events.CustomerOrder
import com.example.events.header.StandardHeaders

/** Handler interface for customer-order topic events */
interface CustomerOrderHandler {
  /** Handle a message from the topic */
  abstract fun handle(
    key: kotlin.String,
    value: CustomerOrder,
    headers: StandardHeaders
  )
}