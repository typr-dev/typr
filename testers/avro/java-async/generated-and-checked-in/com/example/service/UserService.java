package com.example.service;

import java.util.concurrent.CompletableFuture;

/** User management service protocol */
public interface UserService {
  /** Get a user by their ID */
  CompletableFuture<Result<User, UserNotFoundError>> getUser(String userId);

  /** Create a new user */
  CompletableFuture<Result<User, ValidationError>> createUser(String email, String name);

  /** Delete a user */
  CompletableFuture<Result<Void, UserNotFoundError>> deleteUser(String userId);

  /** Send a notification to a user (fire-and-forget) */
  CompletableFuture<Void> notifyUser(String userId, String message);
}
