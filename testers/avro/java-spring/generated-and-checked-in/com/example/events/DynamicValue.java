package com.example.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Optional;

/** A record with complex union types for testing union type generation */
public record DynamicValue(
    /** Unique identifier */
    @JsonProperty("id") String id,
    /** A value that can be string, int, or boolean */
    @JsonProperty("value") StringOrIntOrBoolean value,
    /** An optional value that can be string or long */
    @JsonProperty("optionalValue") Optional<StringOrLong> optionalValue) {
  /** Unique identifier */
  public DynamicValue withId(String id) {
    return new DynamicValue(id, value, optionalValue);
  }

  /** A value that can be string, int, or boolean */
  public DynamicValue withValue(StringOrIntOrBoolean value) {
    return new DynamicValue(id, value, optionalValue);
  }

  /** An optional value that can be string or long */
  public DynamicValue withOptionalValue(Optional<StringOrLong> optionalValue) {
    return new DynamicValue(id, value, optionalValue);
  }
}
