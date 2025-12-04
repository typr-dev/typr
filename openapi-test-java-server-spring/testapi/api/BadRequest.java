package testapi.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/** HTTP 400 response */
public record BadRequest<T201, T400>(@JsonProperty("value") T400 value) implements Response201400<T201, T400> {
  public BadRequest<T201, T400> withValue(T400 value) {
    return new BadRequest<>(value);
  };

  @Override
  public String status() {
    return "400";
  };
}