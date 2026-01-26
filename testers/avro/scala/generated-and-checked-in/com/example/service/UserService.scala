package com.example.service



/** User management service protocol */
trait UserService {
  /** Get a user by their ID */
  def getUser(userId: String): Result[User, UserNotFoundError]

  /** Create a new user */
  def createUser(
    email: String,
    name: String
  ): Result[User, ValidationError]

  /** Delete a user */
  def deleteUser(userId: String): Result[Unit, UserNotFoundError]

  /** Send a notification to a user (fire-and-forget) */
  def notifyUser(
    userId: String,
    message: String
  ): Unit
}