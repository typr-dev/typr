package com.example.service

import java.util.UUID

/** Request wrapper for createUser RPC call */
data class CreateUserRequest(
  /** Correlation ID for request/reply matching */
  val correlationId: kotlin.String,
  val email: kotlin.String,
  val name: kotlin.String
) : UserServiceRequest {
  companion object {
    /** Create a request with auto-generated correlation ID */
    fun create(
      email: kotlin.String,
      name: kotlin.String
    ): CreateUserRequest {
      val correlationId: kotlin.String = UUID.randomUUID().toString()
      return CreateUserRequest(correlationId, email, name)
    }
  }
}