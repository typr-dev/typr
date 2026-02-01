package com.example.service;

import com.example.service.GetUserResponse.Error;
import com.example.service.GetUserResponse.Success;
import com.example.service.Result.Err;
import com.example.service.Result.Ok;
import io.smallrye.reactive.messaging.kafka.reply.KafkaRequestReply;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Kafka RPC client for UserService */
@ApplicationScoped
public record UserServiceClient(KafkaRequestReply<Object, Object> replyingTemplate) {
  @Inject
  public UserServiceClient {}

  public UserServiceClient withReplyingTemplate(
      KafkaRequestReply<Object, Object> replyingTemplate) {
    return new UserServiceClient(replyingTemplate);
  }

  /** Create a new user */
  public Result<User, ValidationError> createUser(String email, String name) throws Exception {
    CreateUserRequest request = CreateUserRequest.create(email, name);
    var reply = replyingTemplate.request(request).await().indefinitely();
    return switch (reply) {
      case com.example.service.CreateUserResponse.Success s -> new Ok(s.value());
      case com.example.service.CreateUserResponse.Error e -> new Err(e.error());
      default -> throw new IllegalStateException("Unexpected response type");
    };
  }

  /** Delete a user */
  public Result<Void, UserNotFoundError> deleteUser(String userId) throws Exception {
    DeleteUserRequest request = DeleteUserRequest.create(userId);
    var reply = replyingTemplate.request(request).await().indefinitely();
    return switch (reply) {
      case com.example.service.DeleteUserResponse.Success s -> new Ok(s.value());
      case com.example.service.DeleteUserResponse.Error e -> new Err(e.error());
      default -> throw new IllegalStateException("Unexpected response type");
    };
  }

  /** Get a user by their ID */
  public Result<User, UserNotFoundError> getUser(String userId) throws Exception {
    GetUserRequest request = GetUserRequest.create(userId);
    var reply = replyingTemplate.request(request).await().indefinitely();
    return switch (reply) {
      case Success s -> new Ok(s.value());
      case Error e -> new Err(e.error());
      default -> throw new IllegalStateException("Unexpected response type");
    };
  }

  /** Send a notification to a user (fire-and-forget) */
  public void notifyUser(String userId, String message) throws Exception {
    NotifyUserRequest request = NotifyUserRequest.create(userId, message);
    replyingTemplate.request(request).await().indefinitely();
    ;
  }
}
