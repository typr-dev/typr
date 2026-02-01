package com.example.service;

/** Thrown when a requested user does not exist */
public record UserNotFoundError(String userId, String message) {
  public UserNotFoundError withUserId(String userId) {
    return new UserNotFoundError(userId, message);
  }

  public UserNotFoundError withMessage(String message) {
    return new UserNotFoundError(userId, message);
  }
}
