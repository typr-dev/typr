package testapi.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.Optional;

public record Error(
  @JsonProperty("code") @NotNull String code,
  @JsonProperty("details") Optional<Map<String, JsonNode>> details,
  @JsonProperty("message") @NotNull String message
) {
  public Error withCode(String code) {
    return new Error(code, details, message);
  };

  public Error withDetails(Optional<Map<String, JsonNode>> details) {
    return new Error(code, details, message);
  };

  public Error withMessage(String message) {
    return new Error(code, details, message);
  };
}