package testapi.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import testapi.model.Error;

/** HTTP 4XX response */
public record ClientError4XX<T>(
  /** HTTP status code */
  @JsonProperty("statusCode") Integer statusCode,
  @JsonProperty("value") Error value
) implements Response2004XX5XX<T> {
  /** HTTP status code */
  public ClientError4XX<T> withStatusCode(Integer statusCode) {
    return new ClientError4XX<>(statusCode, value);
  };

  public ClientError4XX<T> withValue(Error value) {
    return new ClientError4XX<>(statusCode, value);
  };

  @Override
  public String status() {
    return "4XX";
  };
}