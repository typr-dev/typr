package com.example.events

import com.fasterxml.jackson.annotation.JsonProperty

/** A physical address */
case class Address(
  /** Street address */
  @JsonProperty("street") street: String,
  /** City name */
  @JsonProperty("city") city: String,
  /** Postal/ZIP code */
  @JsonProperty("postalCode") postalCode: String,
  /** Country code (ISO 3166-1 alpha-2) */
  @JsonProperty("country") country: String
)