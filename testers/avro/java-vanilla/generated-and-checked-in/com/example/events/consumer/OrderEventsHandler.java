package com.example.events.consumer;

import com.example.events.OrderCancelled;
import com.example.events.OrderEvents;
import com.example.events.OrderPlaced;
import com.example.events.OrderUpdated;
import com.example.events.header.StandardHeaders;

/** Handler interface for order-events topic events */
public interface OrderEventsHandler {
  /** Handle a OrderCancelled event */
  void handleOrderCancelled(String key, OrderCancelled event, StandardHeaders headers);

  /** Handle a OrderPlaced event */
  void handleOrderPlaced(String key, OrderPlaced event, StandardHeaders headers);

  /** Handle a OrderUpdated event */
  void handleOrderUpdated(String key, OrderUpdated event, StandardHeaders headers);

  /** Handle unknown event types (default throws exception) */
  default void handleUnknown(String key, OrderEvents event, StandardHeaders headers) {
    throw new IllegalStateException("Unknown event type: " + event.getClass());
  }
}
