package com.example.events.consumer;

import com.example.events.common.Money;
import com.example.events.header.StandardHeaders;
import java.util.concurrent.CompletableFuture;

/** Handler interface for money topic events */
public interface MoneyHandler {
  /** Handle a message from the topic */
  CompletableFuture<Void> handle(String key, Money value, StandardHeaders headers);
}
