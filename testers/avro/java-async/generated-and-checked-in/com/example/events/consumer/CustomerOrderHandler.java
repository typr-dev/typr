package com.example.events.consumer;

import com.example.events.CustomerOrder;
import com.example.events.header.StandardHeaders;
import java.util.concurrent.CompletableFuture;

/** Handler interface for customer-order topic events */
public interface CustomerOrderHandler {
  /** Handle a message from the topic */
  CompletableFuture<Void> handle(String key, CustomerOrder value, StandardHeaders headers);
}
