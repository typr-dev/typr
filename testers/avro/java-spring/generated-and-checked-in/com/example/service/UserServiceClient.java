package com.example.service;

import com.example.service.GetUserResponse.Error;
import com.example.service.GetUserResponse.Success;
import com.example.service.Result.Err;
import com.example.service.Result.Ok;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.requestreply.ReplyingKafkaTemplate;
import org.springframework.stereotype.Service;

/** Kafka RPC client for UserService */
@Service
public record UserServiceClient(ReplyingKafkaTemplate<String, Object, Object> replyingTemplate) {
  public UserServiceClient withReplyingTemplate(
      ReplyingKafkaTemplate<String, Object, Object> replyingTemplate) {
    return new UserServiceClient(replyingTemplate);
  }

  /** Create a new user */
  public Result<User, ValidationError> createUser(String email, String name) throws Exception {
    CreateUserRequest request = CreateUserRequest.create(email, name);
    var reply =
        replyingTemplate
            .sendAndReceive(new ProducerRecord<>("user-service-requests", request))
            .get()
            .value();
    return switch (reply) {
      case com.example.service.CreateUserResponse.Success s -> new Ok(s.value());
      case com.example.service.CreateUserResponse.Error e -> new Err(e.error());
      default -> throw new IllegalStateException("Unexpected response type");
    };
  }

  /** Delete a user */
  public Result<Void, UserNotFoundError> deleteUser(String userId) throws Exception {
    DeleteUserRequest request = DeleteUserRequest.create(userId);
    var reply =
        replyingTemplate
            .sendAndReceive(new ProducerRecord<>("user-service-requests", request))
            .get()
            .value();
    return switch (reply) {
      case com.example.service.DeleteUserResponse.Success s -> new Ok(s.value());
      case com.example.service.DeleteUserResponse.Error e -> new Err(e.error());
      default -> throw new IllegalStateException("Unexpected response type");
    };
  }

  /** Get a user by their ID */
  public Result<User, UserNotFoundError> getUser(String userId) throws Exception {
    GetUserRequest request = GetUserRequest.create(userId);
    var reply =
        replyingTemplate
            .sendAndReceive(new ProducerRecord<>("user-service-requests", request))
            .get()
            .value();
    return switch (reply) {
      case Success s -> new Ok(s.value());
      case Error e -> new Err(e.error());
      default -> throw new IllegalStateException("Unexpected response type");
    };
  }

  /** Send a notification to a user (fire-and-forget) */
  public void notifyUser(String userId, String message) throws Exception {
    NotifyUserRequest request = NotifyUserRequest.create(userId, message);
    replyingTemplate
        .sendAndReceive(new ProducerRecord<>("user-service-requests", request))
        .get()
        .value();
    ;
  }
}
