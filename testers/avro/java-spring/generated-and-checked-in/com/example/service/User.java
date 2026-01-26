package com.example.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record User(
    /** User unique identifier */
    @JsonProperty("id") String id,
    /** User email address */
    @JsonProperty("email") String email,
    /** User display name */
    @JsonProperty("name") String name,
    @JsonProperty("createdAt") Instant createdAt) {
  /** User unique identifier */
  public User withId(String id) {
    return new User(id, email, name, createdAt);
  }

  /** User email address */
  public User withEmail(String email) {
    return new User(id, email, name, createdAt);
  }

  /** User display name */
  public User withName(String name) {
    return new User(id, email, name, createdAt);
  }

  public User withCreatedAt(Instant createdAt) {
    return new User(id, email, name, createdAt);
  }
}
