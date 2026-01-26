package com.example;

import static org.junit.Assert.*;

import com.example.events.*;
import com.example.events.precisetypes.Decimal10_2;
import com.example.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.smallrye.mutiny.Uni;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Metadata;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Integration tests for Quarkus Kafka framework-specific generated code.
 *
 * <p>Tests the generated publishers, listeners, RPC clients and servers.
 *
 * <p>Requires Kafka running on localhost:9092 (use docker-compose up kafka).
 */
public class QuarkusKafkaIntegrationTest {

  private static final String BOOTSTRAP_SERVERS = "localhost:9092";
  private static final String TEST_RUN_ID = UUID.randomUUID().toString().substring(0, 8);

  private static boolean kafkaAvailable = false;
  private static ObjectMapper objectMapper;

  @BeforeClass
  public static void checkKafkaAvailability() {
    objectMapper =
        new ObjectMapper().registerModule(new JavaTimeModule()).registerModule(new Jdk8Module());

    Properties props = new Properties();
    props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
    props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000);
    props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 5000);

    try (AdminClient admin = AdminClient.create(props)) {
      admin.listTopics().names().get();
      kafkaAvailable = true;
      System.out.println("Kafka is available at " + BOOTSTRAP_SERVERS);
    } catch (Exception e) {
      System.out.println("Kafka not available at " + BOOTSTRAP_SERVERS + ": " + e.getMessage());
      System.out.println(
          "Skipping Kafka integration tests. Start Kafka with: docker-compose up -d kafka");
    }
  }

  // ========== RPC Request/Response Type Tests ==========

  @Test
  public void testGetUserRequestCreation() {
    GetUserRequest request = GetUserRequest.create("user-123");

    assertNotNull(request.correlationId());
    assertFalse(request.correlationId().isEmpty());
    assertEquals("user-123", request.userId());
  }

  @Test
  public void testGetUserResponseSuccess() {
    User user = new User("user-123", "test@example.com", "Test User", Instant.now());
    GetUserResponse response = new GetUserResponse.Success("corr-id", user);

    assertEquals("corr-id", response.correlationId());
    assertTrue(response instanceof GetUserResponse.Success);
    assertEquals(user, ((GetUserResponse.Success) response).value());
  }

  @Test
  public void testGetUserResponseError() {
    UserNotFoundError error = new UserNotFoundError("user-123", "User not found");
    GetUserResponse response = new GetUserResponse.Error("corr-id", error);

    assertEquals("corr-id", response.correlationId());
    assertTrue(response instanceof GetUserResponse.Error);
    assertEquals(error, ((GetUserResponse.Error) response).error());
  }

  @Test
  public void testCreateUserRequestCreation() {
    CreateUserRequest request = CreateUserRequest.create("test@example.com", "Test User");

    assertNotNull(request.correlationId());
    assertEquals("test@example.com", request.email());
    assertEquals("Test User", request.name());
  }

  @Test
  public void testDeleteUserRequestCreation() {
    DeleteUserRequest request = DeleteUserRequest.create("user-456");

    assertNotNull(request.correlationId());
    assertEquals("user-456", request.userId());
  }

  @Test
  public void testNotifyUserRequestCreation() {
    NotifyUserRequest request = NotifyUserRequest.create("user-789", "Hello!");

    assertNotNull(request.correlationId());
    assertEquals("user-789", request.userId());
    assertEquals("Hello!", request.message());
  }

  // ========== RPC Result Pattern Matching Tests ==========

  @Test
  public void testGetUserResultPatternMatching() {
    User user = new User("id", "email@test.com", "Name", Instant.now());
    Result<User, UserNotFoundError> okResult = new Result.Ok<>(user);
    Result<User, UserNotFoundError> errResult =
        new Result.Err<>(new UserNotFoundError("id", "Not found"));

    String okMessage =
        switch (okResult) {
          case Result.Ok(var u) -> "Found: " + u.name();
          case Result.Err(var e) -> "Error: " + e.message();
        };
    assertEquals("Found: Name", okMessage);

    String errMessage =
        switch (errResult) {
          case Result.Ok(var u) -> "Found: " + u.name();
          case Result.Err(var e) -> "Error: " + e.message();
        };
    assertEquals("Error: Not found", errMessage);
  }

  @Test
  public void testCreateUserResultPatternMatching() {
    User user = new User("id", "email@test.com", "Name", Instant.now());
    Result<User, ValidationError> okResult = new Result.Ok<>(user);
    Result<User, ValidationError> errResult =
        new Result.Err<>(new ValidationError("email", "Invalid email"));

    String okMessage =
        switch (okResult) {
          case Result.Ok(var u) -> "Created: " + u.id();
          case Result.Err(var e) -> "Validation error: " + e.field();
        };
    assertEquals("Created: id", okMessage);

    String errMessage =
        switch (errResult) {
          case Result.Ok(var u) -> "Created: " + u.id();
          case Result.Err(var e) -> "Validation error: " + e.field();
        };
    assertEquals("Validation error: email", errMessage);
  }

  // ========== UserServiceServer Tests ==========

  @Test
  public void testUserServiceServerHandlesGetUserRequest() {
    Map<String, User> userStore = new HashMap<>();
    User existingUser = new User("user-1", "user1@test.com", "User One", Instant.now());
    userStore.put("user-1", existingUser);

    UserServiceHandler handler = createTestHandler(userStore);
    UserServiceServer server = new UserServiceServer(handler);

    // Test successful get
    GetUserRequest getRequest = GetUserRequest.create("user-1");
    Object response = server.handleRequest(getRequest);

    assertTrue(response instanceof GetUserResponse.Success);
    GetUserResponse.Success success = (GetUserResponse.Success) response;
    assertEquals(getRequest.correlationId(), success.correlationId());
    assertEquals(existingUser.email(), success.value().email());

    // Test not found
    GetUserRequest notFoundRequest = GetUserRequest.create("nonexistent");
    Object notFoundResponse = server.handleRequest(notFoundRequest);

    assertTrue(notFoundResponse instanceof GetUserResponse.Error);
    GetUserResponse.Error error = (GetUserResponse.Error) notFoundResponse;
    assertEquals(notFoundRequest.correlationId(), error.correlationId());
    assertEquals("nonexistent", error.error().userId());
  }

  @Test
  public void testUserServiceServerHandlesCreateUserRequest() {
    Map<String, User> userStore = new HashMap<>();
    UserServiceHandler handler = createTestHandler(userStore);
    UserServiceServer server = new UserServiceServer(handler);

    // Test successful creation
    CreateUserRequest createRequest = CreateUserRequest.create("new@example.com", "New User");
    Object response = server.handleRequest(createRequest);

    assertTrue(response instanceof CreateUserResponse.Success);
    CreateUserResponse.Success success = (CreateUserResponse.Success) response;
    assertEquals(createRequest.correlationId(), success.correlationId());
    assertEquals("new@example.com", success.value().email());
    assertEquals("New User", success.value().name());

    // Test validation error
    CreateUserRequest invalidRequest = CreateUserRequest.create("invalid-email", "Bad User");
    Object errorResponse = server.handleRequest(invalidRequest);

    assertTrue(errorResponse instanceof CreateUserResponse.Error);
    CreateUserResponse.Error error = (CreateUserResponse.Error) errorResponse;
    assertEquals(invalidRequest.correlationId(), error.correlationId());
    assertEquals("email", error.error().field());
  }

  @Test
  public void testUserServiceServerHandlesDeleteUserRequest() {
    Map<String, User> userStore = new HashMap<>();
    userStore.put(
        "user-to-delete", new User("user-to-delete", "del@test.com", "Delete Me", Instant.now()));
    UserServiceHandler handler = createTestHandler(userStore);
    UserServiceServer server = new UserServiceServer(handler);

    // Test successful delete
    DeleteUserRequest deleteRequest = DeleteUserRequest.create("user-to-delete");
    Object response = server.handleRequest(deleteRequest);

    assertTrue(response instanceof DeleteUserResponse.Success);

    // Test delete non-existent
    DeleteUserRequest notFoundRequest = DeleteUserRequest.create("nonexistent");
    Object errorResponse = server.handleRequest(notFoundRequest);

    assertTrue(errorResponse instanceof DeleteUserResponse.Error);
  }

  // ========== Event Listener Tests ==========

  @Test
  public void testOrderEventsListenerDispatching() {
    List<String> receivedEvents = new ArrayList<>();

    OrderEventsListener listener =
        new OrderEventsListener() {
          @Override
          public Uni<Void> onOrderPlaced(OrderPlaced event, Metadata metadata) {
            receivedEvents.add("OrderPlaced:" + event.orderId());
            return Uni.createFrom().voidItem();
          }

          @Override
          public Uni<Void> onOrderUpdated(OrderUpdated event, Metadata metadata) {
            receivedEvents.add("OrderUpdated:" + event.orderId());
            return Uni.createFrom().voidItem();
          }

          @Override
          public Uni<Void> onOrderCancelled(OrderCancelled event, Metadata metadata) {
            receivedEvents.add("OrderCancelled:" + event.orderId());
            return Uni.createFrom().voidItem();
          }
        };

    // Create test events
    UUID orderId1 = UUID.randomUUID();
    UUID orderId2 = UUID.randomUUID();
    UUID orderId3 = UUID.randomUUID();

    OrderPlaced placed =
        new OrderPlaced(
            orderId1,
            123L,
            Decimal10_2.unsafeForce(new BigDecimal("99.99")),
            Instant.now(),
            List.of("item1"),
            Optional.empty());

    OrderUpdated updated =
        new OrderUpdated(
            orderId2, OrderStatus.PENDING, OrderStatus.SHIPPED, Instant.now(), Optional.empty());

    OrderCancelled cancelled =
        new OrderCancelled(
            orderId3, 456L, Optional.of("Customer request"), Instant.now(), Optional.empty());

    // Create Message wrappers
    Message<Object> placedMsg = Message.of(placed);
    Message<Object> updatedMsg = Message.of(updated);
    Message<Object> cancelledMsg = Message.of(cancelled);

    // Call the receive method which should dispatch to appropriate handlers
    listener.receive(placedMsg).await().indefinitely();
    listener.receive(updatedMsg).await().indefinitely();
    listener.receive(cancelledMsg).await().indefinitely();

    assertEquals(3, receivedEvents.size());
    assertTrue(receivedEvents.get(0).startsWith("OrderPlaced:"));
    assertTrue(receivedEvents.get(1).startsWith("OrderUpdated:"));
    assertTrue(receivedEvents.get(2).startsWith("OrderCancelled:"));
  }

  @Test
  public void testAddressListenerDispatching() {
    List<Address> receivedAddresses = new ArrayList<>();

    AddressListener listener =
        new AddressListener() {
          @Override
          public Uni<Void> onAddress(Address event, Metadata metadata) {
            receivedAddresses.add(event);
            return Uni.createFrom().voidItem();
          }
        };

    Address address = new Address("123 Main St", "Springfield", "12345", "US");
    Message<Object> msg = Message.of(address);

    listener.receive(msg).await().indefinitely();

    assertEquals(1, receivedAddresses.size());
    assertEquals("123 Main St", receivedAddresses.get(0).street());
  }

  // ========== Kafka Integration Tests ==========

  @Test
  public void testEventPublishingRoundTrip() throws Exception {
    if (!kafkaAvailable) {
      System.out.println("Skipping Kafka test - Kafka not available");
      return;
    }

    String topicName = "order-events-quarkus-" + TEST_RUN_ID;
    createTopicIfNotExists(topicName);

    UUID orderId = UUID.randomUUID();
    OrderPlaced event =
        new OrderPlaced(
            orderId,
            12345L,
            Decimal10_2.unsafeForce(new BigDecimal("199.99")),
            Instant.now(),
            List.of("product-1", "product-2"),
            Optional.of("123 Test Street"));

    // Send event using JSON serialization
    try (KafkaProducer<String, String> producer = createJsonProducer()) {
      String json = objectMapper.writeValueAsString(event);
      ProducerRecord<String, String> record =
          new ProducerRecord<>(topicName, orderId.toString(), json);
      producer.send(record).get();
      producer.flush();
    }

    // Receive and verify
    try (KafkaConsumer<String, String> consumer = createJsonConsumer()) {
      consumer.subscribe(Collections.singletonList(topicName));

      ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));
      assertFalse("Should receive event", records.isEmpty());

      var received = records.iterator().next();
      OrderPlaced receivedEvent = objectMapper.readValue(received.value(), OrderPlaced.class);

      assertEquals(orderId, receivedEvent.orderId());
      assertEquals(12345L, (long) receivedEvent.customerId());
      assertEquals(2, receivedEvent.items().size());
      assertTrue(receivedEvent.shippingAddress().isPresent());
      assertEquals("123 Test Street", receivedEvent.shippingAddress().get());
    }
  }

  @Test
  public void testRpcRoundTripThroughKafka() throws Exception {
    if (!kafkaAvailable) {
      System.out.println("Skipping Kafka test - Kafka not available");
      return;
    }

    String requestTopic = "user-service-requests-quarkus-" + TEST_RUN_ID;
    String replyTopic = "user-service-replies-quarkus-" + TEST_RUN_ID;
    createTopicIfNotExists(requestTopic);
    createTopicIfNotExists(replyTopic);

    // Set up user store and server
    Map<String, User> userStore = new HashMap<>();
    User existingUser = new User("test-user-123", "test@example.com", "Test User", Instant.now());
    userStore.put("test-user-123", existingUser);

    UserServiceHandler handler = createTestHandler(userStore);
    UserServiceServer server = new UserServiceServer(handler);

    // Create a request
    GetUserRequest request = GetUserRequest.create("test-user-123");

    // Send request
    try (KafkaProducer<String, String> producer = createJsonProducer()) {
      String json = objectMapper.writeValueAsString(request);
      ProducerRecord<String, String> record =
          new ProducerRecord<>(requestTopic, request.correlationId(), json);
      producer.send(record).get();
      producer.flush();
    }

    // Receive request and handle with server
    GetUserResponse response;
    try (KafkaConsumer<String, String> consumer = createJsonConsumer()) {
      consumer.subscribe(Collections.singletonList(requestTopic));

      ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));
      assertFalse("Should receive request", records.isEmpty());

      var received = records.iterator().next();
      GetUserRequest receivedRequest =
          objectMapper.readValue(received.value(), GetUserRequest.class);

      assertEquals(request.correlationId(), receivedRequest.correlationId());
      assertEquals(request.userId(), receivedRequest.userId());

      // Handle request with generated server
      Object rawResponse = server.handleRequest(receivedRequest);
      assertTrue("Server should return GetUserResponse", rawResponse instanceof GetUserResponse);
      response = (GetUserResponse) rawResponse;
    }

    // Send response
    try (KafkaProducer<String, String> producer = createJsonProducer()) {
      String json = objectMapper.writeValueAsString(response);
      ProducerRecord<String, String> record =
          new ProducerRecord<>(replyTopic, response.correlationId(), json);
      producer.send(record).get();
      producer.flush();
    }

    // Receive response and verify
    try (KafkaConsumer<String, String> consumer = createJsonConsumer()) {
      consumer.subscribe(Collections.singletonList(replyTopic));

      ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));
      assertFalse("Should receive response", records.isEmpty());

      var received = records.iterator().next();
      // Deserialize as Success type directly since we know the response type
      GetUserResponse.Success receivedResponse =
          objectMapper.readValue(received.value(), GetUserResponse.Success.class);

      assertEquals(request.correlationId(), receivedResponse.correlationId());
      assertEquals("test@example.com", receivedResponse.value().email());
      assertEquals("Test User", receivedResponse.value().name());
    }
  }

  // ========== Mutiny Uni Tests ==========

  @Test
  public void testUniCreateFromVoidItem() {
    Uni<Void> uni = Uni.createFrom().voidItem();
    assertNull(uni.await().indefinitely());
  }

  @Test
  public void testUniCreateFromItem() {
    User user = new User("id", "email@test.com", "Name", Instant.now());
    Uni<User> uni = Uni.createFrom().item(user);
    assertEquals(user, uni.await().indefinitely());
  }

  // ========== Helper Methods ==========

  private void createTopicIfNotExists(String topicName)
      throws ExecutionException, InterruptedException {
    Properties props = new Properties();
    props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);

    try (AdminClient admin = AdminClient.create(props)) {
      Set<String> existingTopics = admin.listTopics().names().get();
      if (!existingTopics.contains(topicName)) {
        NewTopic newTopic = new NewTopic(topicName, 1, (short) 1);
        admin.createTopics(Collections.singletonList(newTopic)).all().get();
      }
    }
  }

  private UserServiceHandler createTestHandler(Map<String, User> userStore) {
    return new UserServiceHandler() {
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
  }

  private KafkaProducer<String, String> createJsonProducer() {
    Properties props = new Properties();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    props.put(ProducerConfig.ACKS_CONFIG, "all");
    return new KafkaProducer<>(props);
  }

  private KafkaConsumer<String, String> createJsonConsumer() {
    Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-" + UUID.randomUUID());
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
    return new KafkaConsumer<>(props);
  }
}
