package com.example.service

import java.util.UUID

/** Request wrapper for deleteUser RPC call */
data class DeleteUserRequest(
  /** Correlation ID for request/reply matching */
  val correlationId: kotlin.String,
  val userId: kotlin.String
) : UserServiceRequest {
  companion object {
    /** Create a request with auto-generated correlation ID */
    fun create(userId: kotlin.String): DeleteUserRequest {
      val correlationId: kotlin.String = UUID.randomUUID().toString()
      return DeleteUserRequest(correlationId, userId)
    }
  }
}