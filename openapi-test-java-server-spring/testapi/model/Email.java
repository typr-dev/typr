package testapi.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Email address wrapper */
public record Email(@JsonValue @JsonCreator String value) {
  public Email withValue(String value) {
    return new Email(value);
  };
}