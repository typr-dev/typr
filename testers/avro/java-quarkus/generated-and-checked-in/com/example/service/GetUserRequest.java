package com.example.service;

import java.util.UUID;

/** Request wrapper for getUser RPC call */
public record GetUserRequest(
    /** Correlation ID for request/reply matching */
    String correlationId, String userId) implements UserServiceRequest {
  /** Correlation ID for request/reply matching */
  public GetUserRequest withCorrelationId(String correlationId) {
    return new GetUserRequest(correlationId, userId);
  }

  public GetUserRequest withUserId(String userId) {
    return new GetUserRequest(correlationId, userId);
  }

  /** Create a request with auto-generated correlation ID */
  public static GetUserRequest create(String userId) {
    String correlationId = UUID.randomUUID().toString();
    return new GetUserRequest(correlationId, userId);
  }
}
