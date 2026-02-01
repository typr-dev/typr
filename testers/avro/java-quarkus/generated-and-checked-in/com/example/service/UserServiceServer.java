package com.example.service;

import com.example.service.GetUserResponse.Error;
import com.example.service.GetUserResponse.Success;
import com.example.service.Result.Err;
import com.example.service.Result.Ok;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;

/** Kafka RPC server for UserService */
@ApplicationScoped
public record UserServiceServer(UserServiceHandler handler) {
  @Inject
  public UserServiceServer {}

  public UserServiceServer withHandler(UserServiceHandler handler) {
    return new UserServiceServer(handler);
  }

  public CreateUserResponse handleCreateUser(CreateUserRequest request) {
    var result = handler.createUser(request.email(), request.name());
    return switch (result) {
      case Ok ok ->
          new com.example.service.CreateUserResponse.Success(
              request.correlationId(), ((User) ok.value()));
      case Err err ->
          new com.example.service.CreateUserResponse.Error(
              request.correlationId(), ((ValidationError) err.error()));
    };
  }

  public DeleteUserResponse handleDeleteUser(DeleteUserRequest request) {
    var result = handler.deleteUser(request.userId());
    return switch (result) {
      case Ok ok ->
          new com.example.service.DeleteUserResponse.Success(
              request.correlationId(), ((Void) ok.value()));
      case Err err ->
          new com.example.service.DeleteUserResponse.Error(
              request.correlationId(), ((UserNotFoundError) err.error()));
    };
  }

  public GetUserResponse handleGetUser(GetUserRequest request) {
    var result = handler.getUser(request.userId());
    return switch (result) {
      case Ok ok -> new Success(request.correlationId(), ((User) ok.value()));
      case Err err -> new Error(request.correlationId(), ((UserNotFoundError) err.error()));
    };
  }

  public void handleNotifyUser(NotifyUserRequest request) {
    handler.notifyUser(request.userId(), request.message());
  }

  /** Dispatch incoming requests to handler methods */
  @Incoming("user-service-requests")
  @Outgoing("user-service-replies")
  public Object handleRequest(UserServiceRequest request) {
    return switch (request) {
      case GetUserRequest r -> handleGetUser(r);
      case CreateUserRequest r -> handleCreateUser(r);
      case DeleteUserRequest r -> handleDeleteUser(r);
      case NotifyUserRequest r -> {
        handleNotifyUser(r);
        yield null;
      }
    };
  }
}
