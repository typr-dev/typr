package testapi.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** ISO 4217 currency code */
public record Currency(@JsonValue @JsonCreator String value) {
  public Currency withValue(String value) {
    return new Currency(value);
  };
}