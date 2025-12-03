package testapi.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Optional;

public record OptionalInfo(
  @JsonProperty("optional_field") Optional<String> optional_field,
  @JsonProperty("optional_with_default") Optional<String> optional_with_default,
  @JsonProperty("required_field") Optional<String> required_field
) {
  public OptionalInfo withOptional_field(Optional<String> optional_field) {
    return new OptionalInfo(optional_field, optional_with_default, required_field);
  };

  public OptionalInfo withOptional_with_default(Optional<String> optional_with_default) {
    return new OptionalInfo(optional_field, optional_with_default, required_field);
  };

  public OptionalInfo withRequired_field(Optional<String> required_field) {
    return new OptionalInfo(optional_field, optional_with_default, required_field);
  };
}