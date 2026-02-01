package com.example.service

import java.util.UUID

/** Request wrapper for notifyUser RPC call */
data class NotifyUserRequest(
  /** Correlation ID for request/reply matching */
  val correlationId: kotlin.String,
  val userId: kotlin.String,
  val message: kotlin.String
) : UserServiceRequest {
  companion object {
    /** Create a request with auto-generated correlation ID */
    fun create(
      userId: kotlin.String,
      message: kotlin.String
    ): NotifyUserRequest {
      val correlationId: kotlin.String = UUID.randomUUID().toString()
      return NotifyUserRequest(correlationId, userId, message)
    }
  }
}