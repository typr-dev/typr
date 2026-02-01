package com.example.service;

/** User management service protocol */
public interface UserService {
  /** Get a user by their ID */
  Result<User, UserNotFoundError> getUser(String userId);

  /** Create a new user */
  Result<User, ValidationError> createUser(String email, String name);

  /** Delete a user */
  Result<Void, UserNotFoundError> deleteUser(String userId);

  /** Send a notification to a user (fire-and-forget) */
  void notifyUser(String userId, String message);
}
