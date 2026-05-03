package combined.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Optional;

public record CustomerCreate(
  /** Customer's email address (matches Email type) */
  @JsonProperty("email") String email,
  /** Customer's first name (matches FirstName type) */
  @JsonProperty("firstName") String firstName,
  /** Customer's last name (matches LastName type) */
  @JsonProperty("lastName") String lastName,
  @JsonProperty("tier") Optional<String> tier
) {
  /** Customer's email address (matches Email type) */
  public CustomerCreate withEmail(String email) {
    return new CustomerCreate(email, firstName, lastName, tier);
  }

  /** Customer's first name (matches FirstName type) */
  public CustomerCreate withFirstName(String firstName) {
    return new CustomerCreate(email, firstName, lastName, tier);
  }

  /** Customer's last name (matches LastName type) */
  public CustomerCreate withLastName(String lastName) {
    return new CustomerCreate(email, firstName, lastName, tier);
  }

  public CustomerCreate withTier(Optional<String> tier) {
    return new CustomerCreate(email, firstName, lastName, tier);
  }
}