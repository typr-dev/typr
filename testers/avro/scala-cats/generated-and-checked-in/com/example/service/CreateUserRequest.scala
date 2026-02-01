package com.example.service

import java.util.UUID

/** Request wrapper for createUser RPC call */
case class CreateUserRequest(
  /** Correlation ID for request/reply matching */
  correlationId: String,
  email: String,
  name: String
) extends UserServiceRequest

object CreateUserRequest {
  /** Create a request with auto-generated correlation ID */
  def create(
    email: String,
    name: String
  ): CreateUserRequest = {
    val correlationId: String = UUID.randomUUID().toString()
    return new CreateUserRequest(correlationId, email, name)
  }
}