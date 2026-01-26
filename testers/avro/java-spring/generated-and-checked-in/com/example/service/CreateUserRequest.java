package com.example.service;

import java.util.UUID;

/** Request wrapper for createUser RPC call */
public record CreateUserRequest(
    /** Correlation ID for request/reply matching */
    String correlationId, String email, String name) implements UserServiceRequest {
  /** Correlation ID for request/reply matching */
  public CreateUserRequest withCorrelationId(String correlationId) {
    return new CreateUserRequest(correlationId, email, name);
  }

  public CreateUserRequest withEmail(String email) {
    return new CreateUserRequest(correlationId, email, name);
  }

  public CreateUserRequest withName(String name) {
    return new CreateUserRequest(correlationId, email, name);
  }

  /** Create a request with auto-generated correlation ID */
  public static CreateUserRequest create(String email, String name) {
    String correlationId = UUID.randomUUID().toString();
    return new CreateUserRequest(correlationId, email, name);
  }
}
