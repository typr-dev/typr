package com.example.events.common

import com.example.events.precisetypes.Decimal18_4
import com.fasterxml.jackson.annotation.JsonProperty

/** Represents a monetary amount with currency */
data class Money(
  /** The monetary amount */
  @field:JsonProperty("amount") val amount: Decimal18_4,
  /** Currency code (ISO 4217) */
  @field:JsonProperty("currency") val currency: kotlin.String
)