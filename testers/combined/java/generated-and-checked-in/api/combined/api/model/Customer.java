package combined.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.Optional;

public record Customer(
  /** When the customer was created */
  @JsonProperty("createdAt") Optional<OffsetDateTime> createdAt,
  /** Customer ID */
  @JsonProperty("customerId") Long customerId,
  /** Customer's email address (matches Email type) */
  @JsonProperty("email") String email,
  /** Customer's first name (matches FirstName type) */
  @JsonProperty("firstName") String firstName,
  /** Whether customer account is active (matches IsActive type) */
  @JsonProperty("isActive") Boolean isActive,
  /** Customer's last name (matches LastName type) */
  @JsonProperty("lastName") String lastName,
  /** Customer loyalty tier */
  @JsonProperty("tier") Optional<String> tier
) {
  /** When the customer was created */
  public Customer withCreatedAt(Optional<OffsetDateTime> createdAt) {
    return new Customer(createdAt, customerId, email, firstName, isActive, lastName, tier);
  }

  /** Customer ID */
  public Customer withCustomerId(Long customerId) {
    return new Customer(createdAt, customerId, email, firstName, isActive, lastName, tier);
  }

  /** Customer's email address (matches Email type) */
  public Customer withEmail(String email) {
    return new Customer(createdAt, customerId, email, firstName, isActive, lastName, tier);
  }

  /** Customer's first name (matches FirstName type) */
  public Customer withFirstName(String firstName) {
    return new Customer(createdAt, customerId, email, firstName, isActive, lastName, tier);
  }

  /** Whether customer account is active (matches IsActive type) */
  public Customer withIsActive(Boolean isActive) {
    return new Customer(createdAt, customerId, email, firstName, isActive, lastName, tier);
  }

  /** Customer's last name (matches LastName type) */
  public Customer withLastName(String lastName) {
    return new Customer(createdAt, customerId, email, firstName, isActive, lastName, tier);
  }

  /** Customer loyalty tier */
  public Customer withTier(Optional<String> tier) {
    return new Customer(createdAt, customerId, email, firstName, isActive, lastName, tier);
  }
}