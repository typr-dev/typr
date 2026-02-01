package com.example.events.consumer

import cats.effect.IO
import com.example.events.Invoice
import com.example.events.header.StandardHeaders

/** Handler interface for invoice topic events */
trait InvoiceHandler {
  /** Handle a message from the topic */
  def handle(
    key: String,
    value: Invoice,
    headers: StandardHeaders
  ): IO[Unit]
}