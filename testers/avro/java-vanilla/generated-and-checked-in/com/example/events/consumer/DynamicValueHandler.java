package com.example.events.consumer;

import com.example.events.DynamicValue;
import com.example.events.header.StandardHeaders;

/** Handler interface for dynamic-value topic events */
public interface DynamicValueHandler {
  /** Handle a message from the topic */
  void handle(String key, DynamicValue value, StandardHeaders headers);
}
