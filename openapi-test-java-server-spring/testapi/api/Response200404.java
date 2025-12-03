package testapi.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Response type for: 200, 404 */
public sealed interface Response200404<T200, T404> permits Ok, NotFound {
  @JsonProperty("status")
  String status();
}