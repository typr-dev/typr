package testapi.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Response type for: 404, default */
public sealed interface Response404Default<T404> permits NotFound, Default {
  @JsonProperty("status")
  String status();
}