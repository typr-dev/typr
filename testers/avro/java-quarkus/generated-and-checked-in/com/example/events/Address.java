package com.example.events;

import com.fasterxml.jackson.annotation.JsonProperty;

/** A physical address */
public record Address(
    /** Street address */
    @JsonProperty("street") String street,
    /** City name */
    @JsonProperty("city") String city,
    /** Postal/ZIP code */
    @JsonProperty("postalCode") String postalCode,
    /** Country code (ISO 3166-1 alpha-2) */
    @JsonProperty("country") String country) {
  /** Street address */
  public Address withStreet(String street) {
    return new Address(street, city, postalCode, country);
  }

  /** City name */
  public Address withCity(String city) {
    return new Address(street, city, postalCode, country);
  }

  /** Postal/ZIP code */
  public Address withPostalCode(String postalCode) {
    return new Address(street, city, postalCode, country);
  }

  /** Country code (ISO 3166-1 alpha-2) */
  public Address withCountry(String country) {
    return new Address(street, city, postalCode, country);
  }
}
