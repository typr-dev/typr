package com.example.service



/** Response wrapper for deleteUser RPC call */
sealed trait DeleteUserResponse {
  def correlationId: String
}

object DeleteUserResponse {
  /** Error response */
  case class Error(
    correlationId: String,
    error: UserNotFoundError
  ) extends DeleteUserResponse

  /** Successful response */
  case class Success(
    correlationId: String,
    value: Unit
  ) extends DeleteUserResponse
}