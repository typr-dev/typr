package com.example.events.consumer

import com.example.events.DynamicValue
import com.example.events.header.StandardHeaders

/** Handler interface for dynamic-value topic events */
trait DynamicValueHandler {
  /** Handle a message from the topic */
  def handle(
    key: String,
    value: DynamicValue,
    headers: StandardHeaders
  ): Unit
}