package testapi.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.Optional;

public record Dog(
  @JsonProperty("id") @NotNull PetId id,
  @JsonProperty("name") @NotNull String name,
  @JsonProperty("updatedAt") Optional<OffsetDateTime> updatedAt,
  @JsonProperty("breed") @NotNull String breed,
  @JsonProperty("createdAt") @NotNull OffsetDateTime createdAt,
  @JsonProperty("barkVolume") Optional<Integer> barkVolume
) implements Animal {
  public Dog withId(PetId id) {
    return new Dog(id, name, updatedAt, breed, createdAt, barkVolume);
  };

  public Dog withName(String name) {
    return new Dog(id, name, updatedAt, breed, createdAt, barkVolume);
  };

  public Dog withUpdatedAt(Optional<OffsetDateTime> updatedAt) {
    return new Dog(id, name, updatedAt, breed, createdAt, barkVolume);
  };

  public Dog withBreed(String breed) {
    return new Dog(id, name, updatedAt, breed, createdAt, barkVolume);
  };

  public Dog withCreatedAt(OffsetDateTime createdAt) {
    return new Dog(id, name, updatedAt, breed, createdAt, barkVolume);
  };

  public Dog withBarkVolume(Optional<Integer> barkVolume) {
    return new Dog(id, name, updatedAt, breed, createdAt, barkVolume);
  };

  @Override
  public String animal_type() {
    return "dog";
  };
}