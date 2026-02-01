package com.example.service



/** Thrown when a requested user does not exist */
data class UserNotFoundError(
  val userId: kotlin.String,
  val message: kotlin.String
)