package com.example.service

import java.util.UUID

/** Request wrapper for deleteUser RPC call */
case class DeleteUserRequest(
  /** Correlation ID for request/reply matching */
  correlationId: String,
  userId: String
) extends UserServiceRequest

object DeleteUserRequest {
  /** Create a request with auto-generated correlation ID */
  def create(userId: String): DeleteUserRequest = {
    val correlationId: String = UUID.randomUUID().toString()
    return new DeleteUserRequest(correlationId, userId)
  }
}