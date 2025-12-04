package testapi.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/** HTTP 404 response */
public record NotFound<T200, T404>(@JsonProperty("value") T404 value) implements Response200404<T200, T404> {
  public NotFound<T200, T404> withValue(T404 value) {
    return new NotFound<>(value);
  };

  @Override
  public String status() {
    return "404";
  };
}