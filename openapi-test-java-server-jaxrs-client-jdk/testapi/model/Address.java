package testapi.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Optional;

public record Address(
  @JsonProperty("city") Optional<String> city,
  @JsonProperty("country") Optional<String> country,
  @JsonProperty("street") Optional<String> street,
  @JsonProperty("zipCode") Optional<String> zipCode
) {
  public Address withCity(Optional<String> city) {
    return new Address(city, country, street, zipCode);
  };

  public Address withCountry(Optional<String> country) {
    return new Address(city, country, street, zipCode);
  };

  public Address withStreet(Optional<String> street) {
    return new Address(city, country, street, zipCode);
  };

  public Address withZipCode(Optional<String> zipCode) {
    return new Address(city, country, street, zipCode);
  };
}