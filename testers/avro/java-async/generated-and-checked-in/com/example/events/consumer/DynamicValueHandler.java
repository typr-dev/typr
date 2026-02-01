package com.example.events.consumer;

import com.example.events.DynamicValue;
import com.example.events.header.StandardHeaders;
import java.util.concurrent.CompletableFuture;

/** Handler interface for dynamic-value topic events */
public interface DynamicValueHandler {
  /** Handle a message from the topic */
  CompletableFuture<Void> handle(String key, DynamicValue value, StandardHeaders headers);
}
