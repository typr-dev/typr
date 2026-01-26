package com.example.events.consumer

import com.example.events.Address
import com.example.events.header.StandardHeaders

/** Handler interface for address topic events */
trait AddressHandler {
  /** Handle a message from the topic */
  def handle(
    key: String,
    value: Address,
    headers: StandardHeaders
  ): Unit
}