package com.example.events;

import com.fasterxml.jackson.annotation.JsonValue;

/** Customer email address */
public record Email(@JsonValue String value) {
  public Email withValue(String value) {
    return new Email(value);
  }

  @Override
  public java.lang.String toString() {
    return value.toString();
  }

  /** Create a Email from a raw value */
  public static Email valueOf(String v) {
    return new Email(v);
  }

  /** Get the underlying value */
  public String unwrap() {
    return this.value();
  }
}
