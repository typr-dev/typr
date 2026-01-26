package com.example.events;

import com.fasterxml.jackson.annotation.JsonValue;

/** Unique order identifier */
public record OrderId(@JsonValue String value) {
  public OrderId withValue(String value) {
    return new OrderId(value);
  }

  @Override
  public java.lang.String toString() {
    return value.toString();
  }

  /** Create a OrderId from a raw value */
  public static OrderId valueOf(String v) {
    return new OrderId(v);
  }

  /** Get the underlying value */
  public String unwrap() {
    return this.value();
  }
}
