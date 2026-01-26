package com.example;

import static org.junit.Assert.*;

import com.example.service.*;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.kafka.reply.CorrelationId;
import io.smallrye.reactive.messaging.kafka.reply.KafkaRequestReply;
import io.smallrye.reactive.messaging.kafka.reply.PendingReply;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.kafka.common.TopicPartition;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.Before;
import org.junit.Test;

/**
 * Integration test demonstrating the generated strongly-typed UserServiceClient working with a mock
 * KafkaRequestReply implementation.
 *
 * <p>Uses a mock that directly routes requests through the UserServiceServer, simulating the full
 * RPC flow without requiring actual Kafka.
 */
public class QuarkusCdiRpcTest {

  private UserServiceClient userServiceClient;
  private Map<String, User> userStore;

  @Before
  public void setUp() {
    userStore = new HashMap<>();
    User existingUser =
        new User("quarkus-user-1", "quarkus@example.com", "Quarkus Test User", Instant.now());
    userStore.put("quarkus-user-1", existingUser);

    UserServiceHandler handler =
        new UserServiceHandler() {
          @Override
          public Result<User, UserNotFoundError> getUser(String userId) {
            User user = userStore.get(userId);
            if (user == null) {
              return new Result.Err<>(new UserNotFoundError(userId, "User not found"));
            }
            return new Result.Ok<>(user);
          }

          @Override
          public Result<User, ValidationError> createUser(String email, String name) {
            if (!email.contains("@")) {
              return new Result.Err<>(new ValidationError("email", "Invalid email"));
            }
            String id = UUID.randomUUID().toString();
            User user = new User(id, email, name, Instant.now());
            userStore.put(id, user);
            return new Result.Ok<>(user);
          }

          @Override
          public Result<Void, UserNotFoundError> deleteUser(String userId) {
            if (userStore.remove(userId) == null) {
              return new Result.Err<>(new UserNotFoundError(userId, "User not found"));
            }
            return new Result.Ok<>(null);
          }

          @Override
          public void notifyUser(String userId, String message) {
            System.out.println("Notification to " + userId + ": " + message);
          }
        };

    UserServiceServer server = new UserServiceServer(handler);
    KafkaRequestReply<Object, Object> mockRequestReply = new MockKafkaRequestReply(server);
    userServiceClient = new UserServiceClient(mockRequestReply);
  }

  @Test
  public void testGetUserSuccess() throws Exception {
    Result<User, UserNotFoundError> result = userServiceClient.getUser("quarkus-user-1");

    assertTrue("Result should be Ok", result instanceof Result.Ok);
    Result.Ok<User, UserNotFoundError> ok = (Result.Ok<User, UserNotFoundError>) result;
    assertEquals("quarkus@example.com", ok.value().email());
    assertEquals("Quarkus Test User", ok.value().name());
  }

  @Test
  public void testGetUserNotFound() throws Exception {
    Result<User, UserNotFoundError> result = userServiceClient.getUser("nonexistent");

    assertTrue("Result should be Err", result instanceof Result.Err);
    Result.Err<User, UserNotFoundError> err = (Result.Err<User, UserNotFoundError>) result;
    assertEquals("nonexistent", err.error().userId());
  }

  @Test
  public void testCreateUserSuccess() throws Exception {
    Result<User, ValidationError> result =
        userServiceClient.createUser("new@quarkus.com", "New User");

    assertTrue("Result should be Ok", result instanceof Result.Ok);
    Result.Ok<User, ValidationError> ok = (Result.Ok<User, ValidationError>) result;
    assertEquals("new@quarkus.com", ok.value().email());
    assertEquals("New User", ok.value().name());
  }

  @Test
  public void testCreateUserValidationError() throws Exception {
    Result<User, ValidationError> result =
        userServiceClient.createUser("invalid-email", "Bad User");

    assertTrue("Result should be Err", result instanceof Result.Err);
    Result.Err<User, ValidationError> err = (Result.Err<User, ValidationError>) result;
    assertEquals("email", err.error().field());
  }

  @Test
  public void testDeleteUserSuccess() throws Exception {
    // First create a user to delete
    Result<User, ValidationError> createResult =
        userServiceClient.createUser("delete@quarkus.com", "Delete Me");
    assertTrue(createResult instanceof Result.Ok);
    String userId = ((Result.Ok<User, ValidationError>) createResult).value().id();

    Result<Void, UserNotFoundError> deleteResult = userServiceClient.deleteUser(userId);

    assertTrue("Result should be Ok", deleteResult instanceof Result.Ok);
  }

  @Test
  public void testDeleteUserNotFound() throws Exception {
    Result<Void, UserNotFoundError> result = userServiceClient.deleteUser("nonexistent-for-delete");

    assertTrue("Result should be Err", result instanceof Result.Err);
    Result.Err<Void, UserNotFoundError> err = (Result.Err<Void, UserNotFoundError>) result;
    assertEquals("nonexistent-for-delete", err.error().userId());
  }

  /**
   * Mock implementation of KafkaRequestReply that directly routes requests through the
   * UserServiceServer without actual Kafka.
   */
  static class MockKafkaRequestReply implements KafkaRequestReply<Object, Object> {
    private final UserServiceServer server;

    MockKafkaRequestReply(UserServiceServer server) {
      this.server = server;
    }

    @Override
    public Uni<Object> request(Object request) {
      Object response = server.handleRequest((UserServiceRequest) request);
      return Uni.createFrom().item(response);
    }

    @Override
    public Uni<Message<Object>> request(Message<Object> message) {
      Object response = server.handleRequest((UserServiceRequest) message.getPayload());
      return Uni.createFrom().item(Message.of(response));
    }

    @Override
    public Uni<Set<TopicPartition>> waitForAssignments() {
      return Uni.createFrom().item(Collections.emptySet());
    }

    @Override
    public Uni<Set<TopicPartition>> waitForAssignments(Collection<TopicPartition> topicPartitions) {
      return Uni.createFrom().item(Collections.emptySet());
    }

    @Override
    public Map<CorrelationId, PendingReply> getPendingReplies() {
      return Collections.emptyMap();
    }

    @Override
    public io.smallrye.reactive.messaging.kafka.KafkaConsumer<Object, Object> getConsumer() {
      return null;
    }

    @Override
    public void complete() {
      // Nothing to complete
    }
  }
}
