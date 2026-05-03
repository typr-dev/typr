---
title: Spring Boot Integration
---

# Spring Boot Integration

Typr Events can generate Spring Boot-annotated RPC clients and servers.

## Configuration

```scala
val options = AvroOptions.default(...).copy(
  frameworkIntegration = FrameworkIntegration.Spring,
  generateKafkaRpc = true
)
```

## Generated Client

```java
@Service
public class UserServiceClient implements UserService {

    private final ReplyingKafkaTemplate<String, Object, Object> replyingTemplate;

    public UserServiceClient(ReplyingKafkaTemplate<String, Object, Object> replyingTemplate) {
        this.replyingTemplate = replyingTemplate;
    }

    @Override
    public GetUserResult getUser(String userId) {
        var request = new GetUserRequest(UUID.randomUUID().toString(), userId);
        var record = new ProducerRecord<String, Object>("user-service-requests", request);

        try {
            var reply = replyingTemplate.sendAndReceive(record).get(30, TimeUnit.SECONDS);
            return switch (reply.value()) {
                case GetUserResponse.Success s -> new GetUserResult.Ok(s.value());
                case GetUserResponse.Error e -> new GetUserResult.Err(e.error());
                default -> throw new IllegalStateException("Unexpected response type");
            };
        } catch (Exception e) {
            throw new RuntimeException("RPC call failed", e);
        }
    }
}
```

## Generated Server

```java
@Service
public class UserServiceServer {

    private final UserServiceHandler handler;

    public UserServiceServer(UserServiceHandler handler) {
        this.handler = handler;
    }

    @KafkaListener(topics = "user-service-requests")
    @SendTo  // Replies to topic specified in REPLY_TOPIC header
    public Object handleRequest(Object request) {
        return switch (request) {
            case GetUserRequest r -> handleGetUser(r);
            case CreateUserRequest r -> handleCreateUser(r);
            case DeleteUserRequest r -> handleDeleteUser(r);
            default -> throw new IllegalArgumentException("Unknown request: " + request);
        };
    }

    private GetUserResponse handleGetUser(GetUserRequest request) {
        return switch (handler.getUser(request.userId())) {
            case GetUserResult.Ok(var user) ->
                new GetUserResponse.Success(request.correlationId(), user);
            case GetUserResult.Err(var error) ->
                new GetUserResponse.Error(request.correlationId(), error);
        };
    }
}
```

## Spring Configuration

```java
@Configuration
public class KafkaRpcConfig {

    @Bean
    public ReplyingKafkaTemplate<String, Object, Object> replyingKafkaTemplate(
            ProducerFactory<String, Object> pf,
            ConcurrentKafkaListenerContainerFactory<String, Object> factory) {

        var container = factory.createContainer("user-service-replies");
        container.getContainerProperties().setGroupId("rpc-client");
        return new ReplyingKafkaTemplate<>(pf, container);
    }
}
```

## Implementing the Handler

```java
@Service
public class UserServiceImpl implements UserServiceHandler {

    private final UserRepository userRepository;

    @Override
    public GetUserResult getUser(String userId) {
        return userRepository.findById(userId)
            .map(GetUserResult.Ok::new)
            .orElseGet(() -> new GetUserResult.Err(
                new UserNotFoundError(userId, "User not found")));
    }

    @Override
    public CreateUserResult createUser(String email, String name) {
        if (!isValidEmail(email)) {
            return new CreateUserResult.Err(
                new ValidationError("email", "Invalid email format"));
        }
        var user = userRepository.save(new User(
            UUID.randomUUID().toString(), email, name, Instant.now()));
        return new CreateUserResult.Ok(user);
    }
}
```

## Dependencies

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.kafka</groupId>
        <artifactId>spring-kafka</artifactId>
    </dependency>
    <dependency>
        <groupId>org.apache.avro</groupId>
        <artifactId>avro</artifactId>
        <version>1.12.0</version>
    </dependency>
    <dependency>
        <groupId>io.confluent</groupId>
        <artifactId>kafka-avro-serializer</artifactId>
        <version>7.8.0</version>
    </dependency>
</dependencies>
```
