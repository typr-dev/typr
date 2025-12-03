package testapi.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.Optional;

public record Bird(
  @JsonProperty("id") @NotNull String id,
  @JsonProperty("createdAt") @NotNull OffsetDateTime createdAt,
  @JsonProperty("name") @NotNull String name,
  @JsonProperty("updatedAt") Optional<OffsetDateTime> updatedAt,
  @JsonProperty("wingSpan") Optional<Double> wingSpan,
  @JsonProperty("canFly") @NotNull Boolean canFly
) implements Animal {
  public Bird withId(String id) {
    return new Bird(id, createdAt, name, updatedAt, wingSpan, canFly);
  };

  public Bird withCreatedAt(OffsetDateTime createdAt) {
    return new Bird(id, createdAt, name, updatedAt, wingSpan, canFly);
  };

  public Bird withName(String name) {
    return new Bird(id, createdAt, name, updatedAt, wingSpan, canFly);
  };

  public Bird withUpdatedAt(Optional<OffsetDateTime> updatedAt) {
    return new Bird(id, createdAt, name, updatedAt, wingSpan, canFly);
  };

  public Bird withWingSpan(Optional<Double> wingSpan) {
    return new Bird(id, createdAt, name, updatedAt, wingSpan, canFly);
  };

  public Bird withCanFly(Boolean canFly) {
    return new Bird(id, createdAt, name, updatedAt, wingSpan, canFly);
  };

  @Override
  public String animal_type() {
    return "bird";
  };
}