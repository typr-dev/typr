package com.example.service



/** User management service protocol */
interface UserService {
  /** Create a new user */
  abstract fun createUser(
    email: kotlin.String,
    name: kotlin.String
  ): Result<User, ValidationError>

  /** Delete a user */
  abstract fun deleteUser(userId: kotlin.String): Result<Unit, UserNotFoundError>

  /** Get a user by their ID */
  abstract fun getUser(userId: kotlin.String): Result<User, UserNotFoundError>

  /** Send a notification to a user (fire-and-forget) */
  abstract fun notifyUser(
    userId: kotlin.String,
    message: kotlin.String
  )
}