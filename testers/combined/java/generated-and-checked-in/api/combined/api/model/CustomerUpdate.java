package combined.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Optional;

public record CustomerUpdate(
  /** Customer's email address (matches Email type) */
  @JsonProperty("email") Optional<String> email,
  /** Customer's first name (matches FirstName type) */
  @JsonProperty("firstName") Optional<String> firstName,
  /** Whether customer account is active (matches IsActive type) */
  @JsonProperty("isActive") Optional<Boolean> isActive,
  /** Customer's last name (matches LastName type) */
  @JsonProperty("lastName") Optional<String> lastName,
  @JsonProperty("tier") Optional<String> tier
) {
  /** Customer's email address (matches Email type) */
  public CustomerUpdate withEmail(Optional<String> email) {
    return new CustomerUpdate(email, firstName, isActive, lastName, tier);
  }

  /** Customer's first name (matches FirstName type) */
  public CustomerUpdate withFirstName(Optional<String> firstName) {
    return new CustomerUpdate(email, firstName, isActive, lastName, tier);
  }

  /** Whether customer account is active (matches IsActive type) */
  public CustomerUpdate withIsActive(Optional<Boolean> isActive) {
    return new CustomerUpdate(email, firstName, isActive, lastName, tier);
  }

  /** Customer's last name (matches LastName type) */
  public CustomerUpdate withLastName(Optional<String> lastName) {
    return new CustomerUpdate(email, firstName, isActive, lastName, tier);
  }

  public CustomerUpdate withTier(Optional<String> tier) {
    return new CustomerUpdate(email, firstName, isActive, lastName, tier);
  }
}