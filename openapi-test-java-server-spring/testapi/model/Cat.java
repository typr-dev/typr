package testapi.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.Optional;

public record Cat(
  @JsonProperty("meowVolume") Optional<Integer> meowVolume,
  @JsonProperty("id") @NotNull PetId id,
  @JsonProperty("createdAt") @NotNull OffsetDateTime createdAt,
  /** Whether the cat is an indoor cat */
  @JsonProperty("indoor") @NotNull Boolean indoor,
  @JsonProperty("name") @NotNull String name,
  @JsonProperty("updatedAt") Optional<OffsetDateTime> updatedAt
) implements Animal {
  public Cat withMeowVolume(Optional<Integer> meowVolume) {
    return new Cat(meowVolume, id, createdAt, indoor, name, updatedAt);
  };

  public Cat withId(PetId id) {
    return new Cat(meowVolume, id, createdAt, indoor, name, updatedAt);
  };

  public Cat withCreatedAt(OffsetDateTime createdAt) {
    return new Cat(meowVolume, id, createdAt, indoor, name, updatedAt);
  };

  /** Whether the cat is an indoor cat */
  public Cat withIndoor(Boolean indoor) {
    return new Cat(meowVolume, id, createdAt, indoor, name, updatedAt);
  };

  public Cat withName(String name) {
    return new Cat(meowVolume, id, createdAt, indoor, name, updatedAt);
  };

  public Cat withUpdatedAt(Optional<OffsetDateTime> updatedAt) {
    return new Cat(meowVolume, id, createdAt, indoor, name, updatedAt);
  };

  @Override
  public String animal_type() {
    return "cat";
  };
}