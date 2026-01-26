package com.example.events.common

import com.example.events.precisetypes.Decimal18_4
import com.fasterxml.jackson.annotation.JsonProperty

/** Represents a monetary amount with currency */
case class Money(
  /** The monetary amount */
  @JsonProperty("amount") amount: Decimal18_4,
  /** Currency code (ISO 4217) */
  @JsonProperty("currency") currency: String
)