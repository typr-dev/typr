package com.example.events.consumer;

import com.example.events.common.Money;
import com.example.events.header.StandardHeaders;

/** Handler interface for money topic events */
public interface MoneyHandler {
  /** Handle a message from the topic */
  void handle(String key, Money value, StandardHeaders headers);
}
