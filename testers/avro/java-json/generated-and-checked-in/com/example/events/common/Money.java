package com.example.events.common;

import com.example.events.precisetypes.Decimal18_4;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Represents a monetary amount with currency */
public record Money(
    /** The monetary amount */
    @JsonProperty("amount") Decimal18_4 amount,
    /** Currency code (ISO 4217) */
    @JsonProperty("currency") String currency) {
  /** The monetary amount */
  public Money withAmount(Decimal18_4 amount) {
    return new Money(amount, currency);
  }

  /** Currency code (ISO 4217) */
  public Money withCurrency(String currency) {
    return new Money(amount, currency);
  }
}
