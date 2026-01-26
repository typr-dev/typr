package com.example;

import static org.junit.Assert.*;

import com.example.events.*;
import com.example.events.precisetypes.Decimal10_2;
import com.example.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.requestreply.ReplyingKafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

/**
 * Integration tests for Spring Kafka framework-specific generated code.
 *
 * <p>Tests the generated publishers, listeners, RPC clients and servers using actual KafkaTemplate
 * instances with JSON serialization.
 *
 * <p>Requires Kafka running on localhost:9092 (use docker-compose up kafka).
 */
public class SpringKafkaIntegrationTest {

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
          case Result.Ok(var u) -> "Found: " + ((User) u).name();
          case Result.Err(var e) -> "Error: " + ((UserNotFoundError) e).message();
        };
    assertEquals("Found: Name", okMessage);

    String errMessage =
        switch (errResult) {
          case Result.Ok(var u) -> "Found: " + ((User) u).name();
          case Result.Err(var e) -> "Error: " + ((UserNotFoundError) e).message();
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
          case Result.Ok(var u) -> "Created: " + ((User) u).id();
          case Result.Err(var e) -> "Validation error: " + ((ValidationError) e).field();
        };
    assertEquals("Created: id", okMessage);

    String errMessage =
        switch (errResult) {
          case Result.Ok(var u) -> "Created: " + ((User) u).id();
          case Result.Err(var e) -> "Validation error: " + ((ValidationError) e).field();
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
          public CompletableFuture<Void> onOrderPlaced(OrderPlaced event, Headers metadata) {
            receivedEvents.add("OrderPlaced:" + event.orderId());
            return CompletableFuture.completedFuture(null);
          }

          @Override
          public CompletableFuture<Void> onOrderUpdated(OrderUpdated event, Headers metadata) {
            receivedEvents.add("OrderUpdated:" + event.orderId());
            return CompletableFuture.completedFuture(null);
          }

          @Override
          public CompletableFuture<Void> onOrderCancelled(OrderCancelled event, Headers metadata) {
            receivedEvents.add("OrderCancelled:" + event.orderId());
            return CompletableFuture.completedFuture(null);
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

    // Simulate receiving events through ConsumerRecord
    ConsumerRecord<String, Object> placedRecord =
        new ConsumerRecord<>("order-events", 0, 0, orderId1.toString(), placed);
    ConsumerRecord<String, Object> updatedRecord =
        new ConsumerRecord<>("order-events", 0, 1, orderId2.toString(), updated);
    ConsumerRecord<String, Object> cancelledRecord =
        new ConsumerRecord<>("order-events", 0, 2, orderId3.toString(), cancelled);

    // Call the receive method which should dispatch to appropriate handlers
    listener.receive(placedRecord);
    listener.receive(updatedRecord);
    listener.receive(cancelledRecord);

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
          public CompletableFuture<Void> onAddress(Address event, Headers metadata) {
            receivedAddresses.add(event);
            return CompletableFuture.completedFuture(null);
          }
        };

    Address address = new Address("123 Main St", "Springfield", "12345", "US");

    ConsumerRecord<String, Object> record = new ConsumerRecord<>("address", 0, 0, "key", address);

    listener.receive(record);

    assertEquals(1, receivedAddresses.size());
    assertEquals("123 Main St", receivedAddresses.get(0).street());
  }

  // ========== Kafka Integration Tests with Generated Publishers ==========

  @Test
  public void testEventPublishingWithGeneratedPublisher() throws Exception {
    if (!kafkaAvailable) {
      System.out.println("Skipping Kafka test - Kafka not available");
      return;
    }

    String topicName = "order-events-publisher-" + TEST_RUN_ID;
    createTopicIfNotExists(topicName);

    // Create KafkaTemplate with JSON serialization
    KafkaTemplate<String, OrderEvents> kafkaTemplate = createOrderEventsKafkaTemplate();

    // Use the generated publisher
    OrderEventsPublisher publisher = new OrderEventsPublisher(kafkaTemplate, topicName);

    UUID orderId = UUID.randomUUID();
    OrderPlaced event =
        new OrderPlaced(
            orderId,
            12345L,
            Decimal10_2.unsafeForce(new BigDecimal("199.99")),
            Instant.now(),
            List.of("product-1", "product-2"),
            Optional.of("123 Test Street"));

    // Set up Spring Kafka consumer with JsonDeserializer FIRST (before publishing)
    AtomicReference<OrderPlaced> received = new AtomicReference<>();
    CountDownLatch receiveLatch = new CountDownLatch(1);

    ConsumerFactory<String, OrderEvents> consumerFactory = createOrderEventsConsumerFactory();
    ContainerProperties containerProps = new ContainerProperties(topicName);
    containerProps.setGroupId("test-group-" + UUID.randomUUID());
    containerProps.setMessageListener(
        (MessageListener<String, OrderEvents>)
            record -> {
              if (record.value() instanceof OrderPlaced op) {
                received.set(op);
                receiveLatch.countDown();
              }
            });

    KafkaMessageListenerContainer<String, OrderEvents> container =
        new KafkaMessageListenerContainer<>(consumerFactory, containerProps);
    container.start();

    try {
      // Publish using generated code (verifies publisher works with KafkaTemplate)
      CompletableFuture<SendResult<String, OrderEvents>> result =
          publisher.publish(orderId.toString(), event);
      SendResult<String, OrderEvents> sendResult = result.get(10, TimeUnit.SECONDS);

      assertNotNull(sendResult);
      assertEquals(topicName, sendResult.getRecordMetadata().topic());

      // Wait for message to be received via Spring Kafka's JsonDeserializer
      assertTrue(
          "Should receive message via Spring Kafka JsonDeserializer",
          receiveLatch.await(10, TimeUnit.SECONDS));

      OrderPlaced receivedEvent = received.get();
      assertNotNull(receivedEvent);
      assertEquals(orderId, receivedEvent.orderId());
      assertEquals(12345L, (long) receivedEvent.customerId());
      assertEquals(2, receivedEvent.items().size());
      assertTrue(receivedEvent.shippingAddress().isPresent());
      assertEquals("123 Test Street", receivedEvent.shippingAddress().get());
    } finally {
      container.stop();
    }
  }

  @Test
  public void testRpcWithGeneratedClientAndServer() throws Exception {
    if (!kafkaAvailable) {
      System.out.println("Skipping Kafka test - Kafka not available");
      return;
    }

    String requestTopic = "user-service-req-" + TEST_RUN_ID;
    createTopicIfNotExists(requestTopic);

    // Set up user store for the server
    Map<String, User> userStore = new HashMap<>();
    User existingUser = new User("test-user-123", "test@example.com", "Test User", Instant.now());
    userStore.put("test-user-123", existingUser);

    UserServiceHandler handler = createTestHandler(userStore);
    UserServiceServer server = new UserServiceServer(handler);

    // Create request using generated code
    GetUserRequest request = GetUserRequest.create("test-user-123");

    // Set up Spring Kafka consumer with JsonDeserializer to receive the request
    AtomicReference<GetUserResponse> responseRef = new AtomicReference<>();
    CountDownLatch receiveLatch = new CountDownLatch(1);

    ConsumerFactory<String, GetUserRequest> requestConsumerFactory = createRequestConsumerFactory();
    ContainerProperties containerProps = new ContainerProperties(requestTopic);
    containerProps.setGroupId("server-group-" + UUID.randomUUID());
    containerProps.setMessageListener(
        (MessageListener<String, GetUserRequest>)
            record -> {
              // Handle request with generated server (simulating what @KafkaListener would do)
              Object rawResponse = server.handleRequest(record.value());
              if (rawResponse instanceof GetUserResponse resp) {
                responseRef.set(resp);
                receiveLatch.countDown();
              }
            });

    KafkaMessageListenerContainer<String, GetUserRequest> container =
        new KafkaMessageListenerContainer<>(requestConsumerFactory, containerProps);
    container.start();

    try {
      // Send request using Spring KafkaTemplate with JsonSerializer
      KafkaTemplate<String, GetUserRequest> requestTemplate = createRequestKafkaTemplate();
      requestTemplate
          .send(requestTopic, request.correlationId(), request)
          .get(10, TimeUnit.SECONDS);

      // Wait for response
      assertTrue(
          "Should receive and process request via Spring Kafka JsonDeserializer",
          receiveLatch.await(10, TimeUnit.SECONDS));

      GetUserResponse response = responseRef.get();
      assertNotNull(response);

      // Verify response
      assertTrue("Response should be Success", response instanceof GetUserResponse.Success);
      GetUserResponse.Success success = (GetUserResponse.Success) response;
      assertEquals(request.correlationId(), success.correlationId());
      assertEquals("test@example.com", success.value().email());
      assertEquals("Test User", success.value().name());
    } finally {
      container.stop();
    }
  }

  // ========== End-to-End RPC Test with Generated Client ==========

  @Test
  public void testEndToEndRpcWithGeneratedClientAndServer() throws Exception {
    if (!kafkaAvailable) {
      System.out.println("Skipping Kafka test - Kafka not available");
      return;
    }

    // Use the same topic that the generated UserServiceClient expects
    String requestTopic = "user-service-requests";
    String replyTopic = "user-service-replies-" + TEST_RUN_ID;
    createTopicIfNotExists(requestTopic);
    createTopicIfNotExists(replyTopic);

    // Set up user store and handler for the server
    Map<String, User> userStore = new HashMap<>();
    User existingUser = new User("e2e-user-1", "e2e@example.com", "E2E Test User", Instant.now());
    userStore.put("e2e-user-1", existingUser);
    UserServiceHandler handler = createTestHandler(userStore);
    UserServiceServer server = new UserServiceServer(handler);

    // Create producer factory for requests and replies
    ProducerFactory<String, Object> producerFactory = createRpcProducerFactory();

    // Create consumer factory for replies (what the client will receive)
    ConsumerFactory<String, Object> replyConsumerFactory = createRpcConsumerFactory();

    // Set up reply container for ReplyingKafkaTemplate
    ContainerProperties replyContainerProps = new ContainerProperties(replyTopic);
    replyContainerProps.setGroupId("rpc-client-" + UUID.randomUUID());
    ConcurrentMessageListenerContainer<String, Object> replyContainer =
        new ConcurrentMessageListenerContainer<>(replyConsumerFactory, replyContainerProps);
    replyContainer.setAutoStartup(false);

    // Create ReplyingKafkaTemplate (used by the generated UserServiceClient)
    ReplyingKafkaTemplate<String, Object, Object> replyingTemplate =
        new ReplyingKafkaTemplate<>(producerFactory, replyContainer);
    replyingTemplate.setDefaultReplyTimeout(java.time.Duration.ofSeconds(30));
    replyingTemplate.start();

    // Set up server-side listener that processes requests and sends replies
    ConsumerFactory<String, Object> requestConsumerFactory = createRpcConsumerFactory();
    ContainerProperties serverContainerProps = new ContainerProperties(requestTopic);
    serverContainerProps.setGroupId("rpc-server-" + UUID.randomUUID());

    KafkaTemplate<String, Object> replyKafkaTemplate = new KafkaTemplate<>(producerFactory);

    serverContainerProps.setMessageListener(
        (MessageListener<String, Object>)
            record -> {
              try {
                // Process request with the generated server
                Object response = server.handleRequest((UserServiceRequest) record.value());
                if (response != null) {
                  // Get reply topic from header (set by ReplyingKafkaTemplate)
                  byte[] replyTopicBytes = record.headers().lastHeader("kafka_replyTopic").value();
                  String replyTo = new String(replyTopicBytes);

                  // Get correlation ID from header
                  byte[] correlationBytes =
                      record.headers().lastHeader("kafka_correlationId").value();

                  // Send reply with correlation ID
                  ProducerRecord<String, Object> replyRecord =
                      new ProducerRecord<>(replyTo, null, record.key(), response);
                  replyRecord.headers().add("kafka_correlationId", correlationBytes);
                  replyKafkaTemplate.send(replyRecord);
                }
              } catch (Exception e) {
                e.printStackTrace();
              }
            });

    KafkaMessageListenerContainer<String, Object> serverContainer =
        new KafkaMessageListenerContainer<>(requestConsumerFactory, serverContainerProps);
    serverContainer.start();

    // Wait for containers to be assigned partitions
    waitForContainerAssignment(serverContainer);
    waitForContainerAssignment(replyContainer);

    try {
      // Create the generated strongly-typed client
      UserServiceClient client = new UserServiceClient(replyingTemplate);

      // Test 1: GetUser - Success case (using the strongly typed client method)
      Result<User, UserNotFoundError> getUserResult = client.getUser("e2e-user-1");

      assertTrue("Result should be Ok", getUserResult instanceof Result.Ok);
      Result.Ok<User, UserNotFoundError> okResult =
          (Result.Ok<User, UserNotFoundError>) getUserResult;
      assertEquals("e2e@example.com", okResult.value().email());
      assertEquals("E2E Test User", okResult.value().name());

      // Test 2: GetUser - Not found case
      Result<User, UserNotFoundError> notFoundResult = client.getUser("nonexistent-user");

      assertTrue("Result should be Err", notFoundResult instanceof Result.Err);
      Result.Err<User, UserNotFoundError> errResult =
          (Result.Err<User, UserNotFoundError>) notFoundResult;
      assertEquals("nonexistent-user", errResult.error().userId());

      // Test 3: CreateUser - Success case
      Result<User, ValidationError> createResult =
          client.createUser("newuser@e2e.com", "New E2E User");

      assertTrue("Result should be Ok", createResult instanceof Result.Ok);
      Result.Ok<User, ValidationError> createOk = (Result.Ok<User, ValidationError>) createResult;
      assertEquals("newuser@e2e.com", createOk.value().email());
      assertEquals("New E2E User", createOk.value().name());

      // Test 4: CreateUser - Validation error case
      Result<User, ValidationError> invalidResult = client.createUser("invalid-email", "Bad User");

      assertTrue("Result should be Err", invalidResult instanceof Result.Err);
      Result.Err<User, ValidationError> invalidErr =
          (Result.Err<User, ValidationError>) invalidResult;
      assertEquals("email", invalidErr.error().field());

      System.out.println("End-to-end RPC test with strongly-typed client completed successfully!");

    } finally {
      serverContainer.stop();
      replyingTemplate.stop();
      replyContainer.stop();
    }
  }

  private void waitForContainerAssignment(
      org.springframework.kafka.listener.MessageListenerContainer container)
      throws InterruptedException {
    // Wait for container to start and get partition assignment
    int attempts = 0;
    while (container.getAssignedPartitions().isEmpty() && attempts < 50) {
      Thread.sleep(100);
      attempts++;
    }
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

  @SuppressWarnings("unchecked")
  private KafkaTemplate<String, OrderEvents> createOrderEventsKafkaTemplate() {
    Map<String, Object> props = new HashMap<>();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

    JsonSerializer<OrderEvents> serializer = new JsonSerializer<>(objectMapper);
    serializer.setAddTypeInfo(true);

    ProducerFactory<String, OrderEvents> producerFactory =
        new DefaultKafkaProducerFactory<>(props, new StringSerializer(), serializer);

    return new KafkaTemplate<>(producerFactory);
  }

  private ConsumerFactory<String, OrderEvents> createOrderEventsConsumerFactory() {
    Map<String, Object> props = new HashMap<>();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

    JsonDeserializer<OrderEvents> deserializer =
        new JsonDeserializer<>(OrderEvents.class, objectMapper);
    deserializer.setRemoveTypeHeaders(false);
    deserializer.addTrustedPackages("com.example.events");
    deserializer.setUseTypeMapperForKey(true);

    return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), deserializer);
  }

  private ProducerFactory<String, Object> createObjectProducerFactory() {
    Map<String, Object> props = new HashMap<>();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);

    JsonSerializer<Object> serializer = new JsonSerializer<>(objectMapper);
    serializer.setAddTypeInfo(true);

    return new DefaultKafkaProducerFactory<>(props, new StringSerializer(), serializer);
  }

  private ConsumerFactory<String, Object> createObjectConsumerFactory() {
    Map<String, Object> props = new HashMap<>();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

    JsonDeserializer<Object> deserializer = new JsonDeserializer<>(Object.class, objectMapper);
    deserializer.setRemoveTypeHeaders(false);
    deserializer.addTrustedPackages("com.example.events", "com.example.service");
    deserializer.setUseTypeMapperForKey(true);

    return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), deserializer);
  }

  private KafkaTemplate<String, GetUserRequest> createRequestKafkaTemplate() {
    Map<String, Object> props = new HashMap<>();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);

    JsonSerializer<GetUserRequest> serializer = new JsonSerializer<>(objectMapper);

    ProducerFactory<String, GetUserRequest> producerFactory =
        new DefaultKafkaProducerFactory<>(props, new StringSerializer(), serializer);

    return new KafkaTemplate<>(producerFactory);
  }

  private ConsumerFactory<String, GetUserRequest> createRequestConsumerFactory() {
    Map<String, Object> props = new HashMap<>();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

    JsonDeserializer<GetUserRequest> deserializer =
        new JsonDeserializer<>(GetUserRequest.class, objectMapper);
    deserializer.addTrustedPackages("com.example.service");

    return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), deserializer);
  }

  private ProducerFactory<String, Object> createRpcProducerFactory() {
    Map<String, Object> props = new HashMap<>();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);

    JsonSerializer<Object> serializer = new JsonSerializer<>(objectMapper);
    serializer.setAddTypeInfo(true);

    return new DefaultKafkaProducerFactory<>(props, new StringSerializer(), serializer);
  }

  private ConsumerFactory<String, Object> createRpcConsumerFactory() {
    Map<String, Object> props = new HashMap<>();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

    JsonDeserializer<Object> deserializer = new JsonDeserializer<>(Object.class, objectMapper);
    deserializer.setRemoveTypeHeaders(false);
    deserializer.addTrustedPackages("com.example.events", "com.example.service");
    // Enable type header usage for polymorphic deserialization
    deserializer.setUseTypeHeaders(true);

    return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), deserializer);
  }
}
