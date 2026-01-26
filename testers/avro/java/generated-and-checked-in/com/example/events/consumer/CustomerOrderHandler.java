package com.example.events.consumer;

import com.example.events.CustomerOrder;
import com.example.events.header.StandardHeaders;

/** Handler interface for customer-order topic events */
public interface CustomerOrderHandler {
  /** Handle a message from the topic */
  void handle(String key, CustomerOrder value, StandardHeaders headers);
}
