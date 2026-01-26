package com.example.service

import com.example.service.GetUserResponse.Error
import com.example.service.GetUserResponse.Success
import com.example.service.Result.Err
import com.example.service.Result.Ok
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.eclipse.microprofile.reactive.messaging.Incoming
import org.eclipse.microprofile.reactive.messaging.Outgoing

@ApplicationScoped
/** Kafka RPC server for UserService */
data class UserServiceServer @Inject constructor(val handler: UserServiceHandler) {
  fun handleCreateUser(request: CreateUserRequest): CreateUserResponse {
    val result = handler.createUser(request.email, request.name)
    return when (val __r = result) {
      is Ok<*, *> -> { val ok = __r as Ok<*, *>; com.example.service.CreateUserResponse.Success(request.correlationId, (ok.value as User)) }
      is Err<*, *> -> { val err = __r as Err<*, *>; com.example.service.CreateUserResponse.Error(request.correlationId, (err.error as ValidationError)) }
      else -> throw IllegalStateException("Unreachable")
    }
  }

  fun handleDeleteUser(request: DeleteUserRequest): DeleteUserResponse {
    val result = handler.deleteUser(request.userId)
    return when (val __r = result) {
      is Ok<*, *> -> { val ok = __r as Ok<*, *>; com.example.service.DeleteUserResponse.Success(request.correlationId, (ok.value as Unit)) }
      is Err<*, *> -> { val err = __r as Err<*, *>; com.example.service.DeleteUserResponse.Error(request.correlationId, (err.error as UserNotFoundError)) }
      else -> throw IllegalStateException("Unreachable")
    }
  }

  fun handleGetUser(request: GetUserRequest): GetUserResponse {
    val result = handler.getUser(request.userId)
    return when (val __r = result) {
      is Ok<*, *> -> { val ok = __r as Ok<*, *>; Success(request.correlationId, (ok.value as User)) }
      is Err<*, *> -> { val err = __r as Err<*, *>; Error(request.correlationId, (err.error as UserNotFoundError)) }
      else -> throw IllegalStateException("Unreachable")
    }
  }

  fun handleNotifyUser(request: NotifyUserRequest) {
    handler.notifyUser(request.userId, request.message)
  }

  /** Dispatch incoming requests to handler methods */
  @Incoming("user-service-requests")
  @Outgoing("user-service-replies")
  fun handleRequest(request: UserServiceRequest): Any? {
    return when (val __r = request) {
      is GetUserRequest -> { val r = __r as GetUserRequest; handleGetUser(r) }
      is CreateUserRequest -> { val r = __r as CreateUserRequest; handleCreateUser(r) }
      is DeleteUserRequest -> { val r = __r as DeleteUserRequest; handleDeleteUser(r) }
      is NotifyUserRequest -> {
        val r = __r as NotifyUserRequest
        handleNotifyUser(r)
          null
      }
    }
  }
}