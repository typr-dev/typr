package com.example.events

import com.fasterxml.jackson.annotation.JsonProperty

/** A record with complex union types for testing union type generation */
data class DynamicValue(
  /** Unique identifier */
  @field:JsonProperty("id") val id: kotlin.String,
  /** A value that can be string, int, or boolean */
  @field:JsonProperty("value") val value: StringOrIntOrBoolean,
  /** An optional value that can be string or long */
  @field:JsonProperty("optionalValue") val optionalValue: StringOrLong?
)