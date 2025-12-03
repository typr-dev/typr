package testapi.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import testapi.model.Error;

/** HTTP default response */
public record Default<T>(
  /** HTTP status code */
  @JsonProperty("statusCode") Integer statusCode,
  @JsonProperty("value") Error value
) implements Response404Default<T> {
  /** HTTP status code */
  public Default<T> withStatusCode(Integer statusCode) {
    return new Default<>(statusCode, value);
  };

  public Default<T> withValue(Error value) {
    return new Default<>(statusCode, value);
  };

  @Override
  public String status() {
    return "default";
  };
}