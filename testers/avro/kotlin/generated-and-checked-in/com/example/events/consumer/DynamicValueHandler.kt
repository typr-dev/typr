package com.example.events.consumer

import com.example.events.DynamicValue
import com.example.events.header.StandardHeaders

/** Handler interface for dynamic-value topic events */
interface DynamicValueHandler {
  /** Handle a message from the topic */
  abstract fun handle(
    key: kotlin.String,
    value: DynamicValue,
    headers: StandardHeaders
  )
}