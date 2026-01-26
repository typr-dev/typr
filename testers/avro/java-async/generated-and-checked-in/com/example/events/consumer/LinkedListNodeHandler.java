package com.example.events.consumer;

import com.example.events.LinkedListNode;
import com.example.events.header.StandardHeaders;
import java.util.concurrent.CompletableFuture;

/** Handler interface for linked-list-node topic events */
public interface LinkedListNodeHandler {
  /** Handle a message from the topic */
  CompletableFuture<Void> handle(String key, LinkedListNode value, StandardHeaders headers);
}
