package testapi.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.Optional;

public record BaseEntity(
  @JsonProperty("createdAt") @NotNull OffsetDateTime createdAt,
  @JsonProperty("id") @NotNull String id,
  @JsonProperty("updatedAt") Optional<OffsetDateTime> updatedAt
) {
  public BaseEntity withCreatedAt(OffsetDateTime createdAt) {
    return new BaseEntity(createdAt, id, updatedAt);
  };

  public BaseEntity withId(String id) {
    return new BaseEntity(createdAt, id, updatedAt);
  };

  public BaseEntity withUpdatedAt(Optional<OffsetDateTime> updatedAt) {
    return new BaseEntity(createdAt, id, updatedAt);
  };
}