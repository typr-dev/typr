package com.example.service

import com.example.service.GetUserResponse.Error
import com.example.service.GetUserResponse.Success
import com.example.service.Result.Err
import com.example.service.Result.Ok
import io.smallrye.mutiny.Uni
import io.smallrye.reactive.messaging.kafka.reply.KafkaRequestReply
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.lang.IllegalStateException
import java.lang.Void

@ApplicationScoped
/** Kafka RPC client for UserService */
data class UserServiceClient @Inject constructor(val replyingTemplate: KafkaRequestReply<Any, Any>) {
  /** Create a new user */
  fun createUser(
    email: kotlin.String,
    name: kotlin.String
  ): Uni<Result<User, ValidationError>> {
    val request: CreateUserRequest = CreateUserRequest.create(email, name)
    return replyingTemplate.request(request).map({ reply -> when (val __r = reply) {
      is com.example.service.CreateUserResponse.Success -> { val s = __r as com.example.service.CreateUserResponse.Success; Ok(s.value) }
      is com.example.service.CreateUserResponse.Error -> { val e = __r as com.example.service.CreateUserResponse.Error; Err(e.error) }
      else -> throw IllegalStateException("Unexpected response type")
    } })
  }

  /** Delete a user */
  fun deleteUser(userId: kotlin.String): Uni<Result<Unit, UserNotFoundError>> {
    val request: DeleteUserRequest = DeleteUserRequest.create(userId)
    return replyingTemplate.request(request).map({ reply -> when (val __r = reply) {
      is com.example.service.DeleteUserResponse.Success -> { val s = __r as com.example.service.DeleteUserResponse.Success; Ok(s.value) }
      is com.example.service.DeleteUserResponse.Error -> { val e = __r as com.example.service.DeleteUserResponse.Error; Err(e.error) }
      else -> throw IllegalStateException("Unexpected response type")
    } })
  }

  /** Get a user by their ID */
  fun getUser(userId: kotlin.String): Uni<Result<User, UserNotFoundError>> {
    val request: GetUserRequest = GetUserRequest.create(userId)
    return replyingTemplate.request(request).map({ reply -> when (val __r = reply) {
      is Success -> { val s = __r as Success; Ok(s.value) }
      is Error -> { val e = __r as Error; Err(e.error) }
      else -> throw IllegalStateException("Unexpected response type")
    } })
  }

  /** Send a notification to a user (fire-and-forget) */
  fun notifyUser(
    userId: kotlin.String,
    message: kotlin.String
  ): Uni<Void> {
    val request: NotifyUserRequest = NotifyUserRequest.create(userId, message)
    return replyingTemplate.request(request).map({ __reply -> null })
  }
}