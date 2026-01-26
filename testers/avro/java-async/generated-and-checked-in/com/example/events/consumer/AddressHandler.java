package com.example.events.consumer;

import com.example.events.Address;
import com.example.events.header.StandardHeaders;
import java.util.concurrent.CompletableFuture;

/** Handler interface for address topic events */
public interface AddressHandler {
  /** Handle a message from the topic */
  CompletableFuture<Void> handle(String key, Address value, StandardHeaders headers);
}
