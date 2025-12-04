package testapi.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record Pet(
  @JsonProperty("tags") Optional<List<String>> tags,
  @JsonProperty("id") @NotNull PetId id,
  @JsonProperty("status") @NotNull PetStatus status,
  @JsonProperty("createdAt") @NotNull OffsetDateTime createdAt,
  @JsonProperty("metadata") Optional<Map<String, String>> metadata,
  /** Pet name */
  @JsonProperty("name") @NotNull String name,
  @JsonProperty("updatedAt") Optional<OffsetDateTime> updatedAt
) {
  public Pet withTags(Optional<List<String>> tags) {
    return new Pet(tags, id, status, createdAt, metadata, name, updatedAt);
  };

  public Pet withId(PetId id) {
    return new Pet(tags, id, status, createdAt, metadata, name, updatedAt);
  };

  public Pet withStatus(PetStatus status) {
    return new Pet(tags, id, status, createdAt, metadata, name, updatedAt);
  };

  public Pet withCreatedAt(OffsetDateTime createdAt) {
    return new Pet(tags, id, status, createdAt, metadata, name, updatedAt);
  };

  public Pet withMetadata(Optional<Map<String, String>> metadata) {
    return new Pet(tags, id, status, createdAt, metadata, name, updatedAt);
  };

  /** Pet name */
  public Pet withName(String name) {
    return new Pet(tags, id, status, createdAt, metadata, name, updatedAt);
  };

  public Pet withUpdatedAt(Optional<OffsetDateTime> updatedAt) {
    return new Pet(tags, id, status, createdAt, metadata, name, updatedAt);
  };
}