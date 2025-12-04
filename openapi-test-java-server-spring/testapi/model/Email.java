package testapi.model;

import com.fasterxml.jackson.annotation.JsonValue;

/** Email address wrapper */
public record Email(@JsonValue String value) {
  public Email withValue(String value) {
    return new Email(value);
  };
}