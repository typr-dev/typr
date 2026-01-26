package com.example.service

import cats.effect.IO
import java.lang.Void

/** User management service protocol */
trait UserService {
  /** Get a user by their ID */
  def getUser(userId: String): IO[Result[User, UserNotFoundError]]

  /** Create a new user */
  def createUser(
    email: String,
    name: String
  ): IO[Result[User, ValidationError]]

  /** Delete a user */
  def deleteUser(userId: String): IO[Result[Unit, UserNotFoundError]]

  /** Send a notification to a user (fire-and-forget) */
  def notifyUser(
    userId: String,
    message: String
  ): IO[Void]
}