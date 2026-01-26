package com.example.events.consumer

import com.example.events.Address
import com.example.events.header.StandardHeaders

/** Handler interface for address topic events */
interface AddressHandler {
  /** Handle a message from the topic */
  abstract fun handle(
    key: kotlin.String,
    value: Address,
    headers: StandardHeaders
  )
}