package com.example.events.consumer;

import com.example.events.LinkedListNode;
import com.example.events.header.StandardHeaders;

/** Handler interface for linked-list-node topic events */
public interface LinkedListNodeHandler {
  /** Handle a message from the topic */
  void handle(String key, LinkedListNode value, StandardHeaders headers);
}
