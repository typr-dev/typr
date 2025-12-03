package testapi.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/** HTTP 201 response */
public record Created<T201, T400>(@JsonProperty("value") T201 value) implements Response201400<T201, T400> {
  public Created<T201, T400> withValue(T201 value) {
    return new Created<>(value);
  };

  @Override
  public String status() {
    return "201";
  };
}