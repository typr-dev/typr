package com.example.events.consumer

import com.example.events.common.Money
import com.example.events.header.StandardHeaders

/** Handler interface for money topic events */
interface MoneyHandler {
  /** Handle a message from the topic */
  abstract fun handle(
    key: kotlin.String,
    value: Money,
    headers: StandardHeaders
  )
}