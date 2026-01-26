package com.example.events.consumer;

import com.example.events.Invoice;
import com.example.events.header.StandardHeaders;

/** Handler interface for invoice topic events */
public interface InvoiceHandler {
  /** Handle a message from the topic */
  void handle(String key, Invoice value, StandardHeaders headers);
}
