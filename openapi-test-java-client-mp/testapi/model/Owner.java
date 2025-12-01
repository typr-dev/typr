package testapi.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.util.Optional;

public record Owner(
  @JsonProperty("address") Optional<Address> address,
  @JsonProperty("email") @jakarta.validation.constraints.Email Optional<String> email,
  @JsonProperty("id") @NotNull String id,
  @JsonProperty("name") @NotNull String name
) {
  public Owner withAddress(Optional<Address> address) {
    return new Owner(address, email, id, name);
  };

  public Owner withEmail(Optional<String> email) {
    return new Owner(address, email, id, name);
  };

  public Owner withId(String id) {
    return new Owner(address, email, id, name);
  };

  public Owner withName(String name) {
    return new Owner(address, email, id, name);
  };
}