package com.example.service

import java.util.UUID

/** Request wrapper for getUser RPC call */
data class GetUserRequest(
  /** Correlation ID for request/reply matching */
  val correlationId: kotlin.String,
  val userId: kotlin.String
) : UserServiceRequest {
  companion object {
    /** Create a request with auto-generated correlation ID */
    fun create(userId: kotlin.String): GetUserRequest {
      val correlationId: kotlin.String = UUID.randomUUID().toString()
      return GetUserRequest(correlationId, userId)
    }
  }
}