package com.example.events.consumer

import com.example.events.Invoice
import com.example.events.header.StandardHeaders

/** Handler interface for invoice topic events */
interface InvoiceHandler {
  /** Handle a message from the topic */
  abstract fun handle(
    key: kotlin.String,
    value: Invoice,
    headers: StandardHeaders
  )
}