package com.example.service;

import java.util.UUID;

/** Request wrapper for notifyUser RPC call */
public record NotifyUserRequest(
    /** Correlation ID for request/reply matching */
    String correlationId, String userId, String message) implements UserServiceRequest {
  /** Correlation ID for request/reply matching */
  public NotifyUserRequest withCorrelationId(String correlationId) {
    return new NotifyUserRequest(correlationId, userId, message);
  }

  public NotifyUserRequest withUserId(String userId) {
    return new NotifyUserRequest(correlationId, userId, message);
  }

  public NotifyUserRequest withMessage(String message) {
    return new NotifyUserRequest(correlationId, userId, message);
  }

  /** Create a request with auto-generated correlation ID */
  public static NotifyUserRequest create(String userId, String message) {
    String correlationId = UUID.randomUUID().toString();
    return new NotifyUserRequest(correlationId, userId, message);
  }
}
