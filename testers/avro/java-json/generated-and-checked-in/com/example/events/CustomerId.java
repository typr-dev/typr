package com.example.events;

import com.fasterxml.jackson.annotation.JsonValue;

/** Customer identifier */
public record CustomerId(@JsonValue Long value) {
  public CustomerId withValue(Long value) {
    return new CustomerId(value);
  }

  @Override
  public java.lang.String toString() {
    return value.toString();
  }

  /** Create a CustomerId from a raw value */
  public static CustomerId valueOf(Long v) {
    return new CustomerId(v);
  }

  /** Get the underlying value */
  public Long unwrap() {
    return this.value();
  }
}
