package com.example.events.consumer

import cats.effect.IO
import com.example.events.common.Money
import com.example.events.header.StandardHeaders

/** Handler interface for money topic events */
trait MoneyHandler {
  /** Handle a message from the topic */
  def handle(
    key: String,
    value: Money,
    headers: StandardHeaders
  ): IO[Unit]
}