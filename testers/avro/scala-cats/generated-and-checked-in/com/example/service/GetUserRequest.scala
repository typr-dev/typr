package com.example.service

import java.util.UUID

/** Request wrapper for getUser RPC call */
case class GetUserRequest(
  /** Correlation ID for request/reply matching */
  correlationId: String,
  userId: String
) extends UserServiceRequest

object GetUserRequest {
  /** Create a request with auto-generated correlation ID */
  def create(userId: String): GetUserRequest = {
    val correlationId: String = UUID.randomUUID().toString()
    return new GetUserRequest(correlationId, userId)
  }
}