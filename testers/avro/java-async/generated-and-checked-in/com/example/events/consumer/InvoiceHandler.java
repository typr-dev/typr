package com.example.events.consumer;

import com.example.events.Invoice;
import com.example.events.header.StandardHeaders;
import java.util.concurrent.CompletableFuture;

/** Handler interface for invoice topic events */
public interface InvoiceHandler {
  /** Handle a message from the topic */
  CompletableFuture<Void> handle(String key, Invoice value, StandardHeaders headers);
}
