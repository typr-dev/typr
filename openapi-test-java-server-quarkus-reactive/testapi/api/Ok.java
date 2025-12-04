package testapi.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/** HTTP 200 response */
public record Ok<T200, T404>(@JsonProperty("value") T200 value) implements Response200404<T200, T404>, Response2004XX5XX<T200> {
  public Ok<T200, T404> withValue(T200 value) {
    return new Ok<>(value);
  };

  @Override
  public String status() {
    return "200";
  };
}