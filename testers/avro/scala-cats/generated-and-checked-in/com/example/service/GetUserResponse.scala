package com.example.service



/** Response wrapper for getUser RPC call */
sealed trait GetUserResponse {
  def correlationId: String
}

object GetUserResponse {
  /** Error response */
  case class Error(
    correlationId: String,
    error: UserNotFoundError
  ) extends GetUserResponse

  /** Successful response */
  case class Success(
    correlationId: String,
    value: User
  ) extends GetUserResponse
}