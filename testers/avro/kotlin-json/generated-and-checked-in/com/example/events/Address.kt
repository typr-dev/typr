package com.example.events

import com.fasterxml.jackson.annotation.JsonProperty

/** A physical address */
data class Address(
  /** Street address */
  @field:JsonProperty("street") val street: kotlin.String,
  /** City name */
  @field:JsonProperty("city") val city: kotlin.String,
  /** Postal/ZIP code */
  @field:JsonProperty("postalCode") val postalCode: kotlin.String,
  /** Country code (ISO 3166-1 alpha-2) */
  @field:JsonProperty("country") val country: kotlin.String
)