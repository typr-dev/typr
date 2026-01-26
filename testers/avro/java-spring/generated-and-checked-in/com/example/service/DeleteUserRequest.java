package com.example.service;

import java.util.UUID;

/** Request wrapper for deleteUser RPC call */
public record DeleteUserRequest(
    /** Correlation ID for request/reply matching */
    String correlationId, String userId) implements UserServiceRequest {
  /** Correlation ID for request/reply matching */
  public DeleteUserRequest withCorrelationId(String correlationId) {
    return new DeleteUserRequest(correlationId, userId);
  }

  public DeleteUserRequest withUserId(String userId) {
    return new DeleteUserRequest(correlationId, userId);
  }

  /** Create a request with auto-generated correlation ID */
  public static DeleteUserRequest create(String userId) {
    String correlationId = UUID.randomUUID().toString();
    return new DeleteUserRequest(correlationId, userId);
  }
}
