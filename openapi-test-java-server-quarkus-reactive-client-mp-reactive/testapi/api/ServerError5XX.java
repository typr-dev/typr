package testapi.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import testapi.model.Error;

/** HTTP 5XX response */
public record ServerError5XX<T>(
  /** HTTP status code */
  @JsonProperty("statusCode") Integer statusCode,
  @JsonProperty("value") Error value
) implements Response2004XX5XX<T> {
  /** HTTP status code */
  public ServerError5XX<T> withStatusCode(Integer statusCode) {
    return new ServerError5XX<>(statusCode, value);
  };

  public ServerError5XX<T> withValue(Error value) {
    return new ServerError5XX<>(statusCode, value);
  };

  @Override
  public String status() {
    return "5XX";
  };
}