package testapi.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Unique pet identifier */
public record PetId(@JsonValue @JsonCreator String value) {
  public PetId withValue(String value) {
    return new PetId(value);
  };
}