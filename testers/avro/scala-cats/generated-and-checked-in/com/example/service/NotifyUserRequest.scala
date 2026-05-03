package com.example.service

import java.util.UUID

/** Request wrapper for notifyUser RPC call */
case class NotifyUserRequest(
  /** Correlation ID for request/reply matching */
  correlationId: String,
  userId: String,
  message: String
) extends UserServiceRequest

object NotifyUserRequest {
  /** Create a request with auto-generated correlation ID */
  def create(
    userId: String,
    message: String
  ): NotifyUserRequest = {
    val correlationId: String = UUID.randomUUID().toString()
    return new NotifyUserRequest(correlationId, userId, message)
  }
}