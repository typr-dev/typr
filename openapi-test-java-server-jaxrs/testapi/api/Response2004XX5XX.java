package testapi.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Response type for: 200, 4XX, 5XX */
public sealed interface Response2004XX5XX<T200> permits Ok, ClientError4XX, ServerError5XX {
  @JsonProperty("status")
  String status();
}