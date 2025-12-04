package testapi.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Optional;

public record PetCreate(
  @JsonProperty("age") @Min(0L) @Max(100L) Optional<Long> age,
  @JsonProperty("email") @jakarta.validation.constraints.Email Optional<String> email,
  @JsonProperty("name") @NotNull @Size(min = 1, max = 100) String name,
  @JsonProperty("status") Optional<PetStatus> status,
  @JsonProperty("tags") @Size(min = 0, max = 10) Optional<List<String>> tags,
  @JsonProperty("website") @Pattern(regexp = "^https?://.*") Optional<String> website
) {
  public PetCreate withAge(Optional<Long> age) {
    return new PetCreate(age, email, name, status, tags, website);
  };

  public PetCreate withEmail(Optional<String> email) {
    return new PetCreate(age, email, name, status, tags, website);
  };

  public PetCreate withName(String name) {
    return new PetCreate(age, email, name, status, tags, website);
  };

  public PetCreate withStatus(Optional<PetStatus> status) {
    return new PetCreate(age, email, name, status, tags, website);
  };

  public PetCreate withTags(Optional<List<String>> tags) {
    return new PetCreate(age, email, name, status, tags, website);
  };

  public PetCreate withWebsite(Optional<String> website) {
    return new PetCreate(age, email, name, status, tags, website);
  };
}