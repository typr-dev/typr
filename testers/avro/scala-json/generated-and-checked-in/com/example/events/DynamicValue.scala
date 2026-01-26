package com.example.events

import com.fasterxml.jackson.annotation.JsonProperty

/** A record with complex union types for testing union type generation */
case class DynamicValue(
  /** Unique identifier */
  @JsonProperty("id") id: String,
  /** A value that can be string, int, or boolean */
  @JsonProperty("value") value: StringOrIntOrBoolean,
  /** An optional value that can be string or long */
  @JsonProperty("optionalValue") optionalValue: Option[StringOrLong]
)