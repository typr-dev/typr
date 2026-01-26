package com.example.service



/** Thrown when a requested user does not exist */
case class UserNotFoundError(
  userId: String,
  message: String
)