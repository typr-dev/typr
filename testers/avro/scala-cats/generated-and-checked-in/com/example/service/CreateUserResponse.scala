package com.example.service



/** Response wrapper for createUser RPC call */
sealed trait CreateUserResponse {
  def correlationId: String
}

object CreateUserResponse {
  /** Error response */
  case class Error(
    correlationId: String,
    error: ValidationError
  ) extends CreateUserResponse

  /** Successful response */
  case class Success(
    correlationId: String,
    value: User
  ) extends CreateUserResponse
}