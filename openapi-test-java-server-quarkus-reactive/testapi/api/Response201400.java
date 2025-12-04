package testapi.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Response type for: 201, 400 */
public sealed interface Response201400<T201, T400> permits Created, BadRequest {
  @JsonProperty("status")
  String status();
}