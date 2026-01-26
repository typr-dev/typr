package com.example.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Optional;

/** Order with wrapper types for type-safe IDs */
public record CustomerOrder(
    /** Unique order identifier */
    @JsonProperty("orderId") OrderId orderId,
    /** Customer identifier */
    @JsonProperty("customerId") CustomerId customerId,
    /** Customer email address */
    @JsonProperty("email") Optional<Email> email,
    /** Order amount in cents (no wrapper) */
    @JsonProperty("amount") Long amount) {
  /** Unique order identifier */
  public CustomerOrder withOrderId(OrderId orderId) {
    return new CustomerOrder(orderId, customerId, email, amount);
  }

  /** Customer identifier */
  public CustomerOrder withCustomerId(CustomerId customerId) {
    return new CustomerOrder(orderId, customerId, email, amount);
  }

  /** Customer email address */
  public CustomerOrder withEmail(Optional<Email> email) {
    return new CustomerOrder(orderId, customerId, email, amount);
  }

  /** Order amount in cents (no wrapper) */
  public CustomerOrder withAmount(Long amount) {
    return new CustomerOrder(orderId, customerId, email, amount);
  }
}
