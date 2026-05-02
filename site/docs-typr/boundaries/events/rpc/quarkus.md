---
title: Quarkus Integration
---

# Quarkus Integration

Typr Events can generate Quarkus-annotated RPC clients and servers using SmallRye Reactive Messaging.

## Configuration

```scala
val options = AvroOptions.default(...).copy(
  frameworkIntegration = FrameworkIntegration.Quarkus,
  effectType = EffectType.Mutiny,
  generateKafkaRpc = true
)
```

## Generated Client

```java
@ApplicationScoped
public class UserServiceClient implements UserService {

    @Channel("user-service")
    KafkaRequestReply<Object, Object> requestReply;

    @Override
    public Uni<GetUserResult> getUser(String userId) {
        var request = new GetUserRequest(UUID.randomUUID().toString(), userId);

        return requestReply.request(request)
            .map(response -> switch (response) {
                case GetUserResponse.Success s -> new GetUserResult.Ok(s.value());
                case GetUserResponse.Error e -> new GetUserResult.Err(e.error());
                default -> throw new IllegalStateException("Unexpected response");
            });
    }
}
```

## Generated Server

```java
@ApplicationScoped
public class UserServiceServer {

    private final UserServiceHandler handler;

    @Inject
    public UserServiceServer(UserServiceHandler handler) {
        this.handler = handler;
    }

    @Incoming("user-service-requests")
    @Outgoing("user-service-replies")
    public Object handleRequest(Object request) {
        return switch (request) {
            case GetUserRequest r -> handleGetUser(r);
            case CreateUserRequest r -> handleCreateUser(r);
            case DeleteUserRequest r -> handleDeleteUser(r);
            default -> throw new IllegalArgumentException("Unknown request: " + request);
        };
    }

    private GetUserResponse handleGetUser(GetUserRequest request) {
        // With Mutiny effect type, handler returns Uni
        return handler.getUser(request.userId())
            .map(result -> switch (result) {
                case GetUserResult.Ok(var user) ->
                    new GetUserResponse.Success(request.correlationId(), user);
                case GetUserResult.Err(var error) ->
                    new GetUserResponse.Error(request.correlationId(), error);
            })
            .await().indefinitely();  // Or handle reactively
    }
}
```

## Quarkus Configuration

```properties
# application.properties

# Request channel
mp.messaging.outgoing.user-service.connector=smallrye-kafka
mp.messaging.outgoing.user-service.topic=user-service-requests
mp.messaging.outgoing.user-service.reply.topic=user-service-replies

# Request handling
mp.messaging.incoming.user-service-requests.connector=smallrye-kafka
mp.messaging.incoming.user-service-requests.topic=user-service-requests

# Reply handling
mp.messaging.outgoing.user-service-replies.connector=smallrye-kafka
mp.messaging.outgoing.user-service-replies.topic=user-service-replies
```

## Implementing the Handler

```java
@ApplicationScoped
public class UserServiceImpl implements UserServiceHandler {

    @Inject
    UserRepository userRepository;

    @Override
    public Uni<GetUserResult> getUser(String userId) {
        return userRepository.findById(userId)
            .onItem().ifNotNull().transform(user -> (GetUserResult) new GetUserResult.Ok(user))
            .onItem().ifNull().continueWith(() ->
                new GetUserResult.Err(new UserNotFoundError(userId, "User not found")));
    }

    @Override
    public Uni<CreateUserResult> createUser(String email, String name) {
        if (!isValidEmail(email)) {
            return Uni.createFrom().item(
                new CreateUserResult.Err(new ValidationError("email", "Invalid format")));
        }

        var user = new User(UUID.randomUUID().toString(), email, name, Instant.now());
        return userRepository.persist(user)
            .map(saved -> new CreateUserResult.Ok(saved));
    }
}
```

## Dependencies

```xml
<dependencies>
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-smallrye-reactive-messaging-kafka</artifactId>
    </dependency>
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-avro</artifactId>
    </dependency>
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-confluent-registry-avro</artifactId>
    </dependency>
</dependencies>
```

## Reactive Benefits

With Mutiny, the entire request/response flow is non-blocking:

```java
@Override
public Uni<GetUserResult> getUser(String userId) {
    return userRepository.findById(userId)           // Non-blocking DB call
        .flatMap(user -> auditService.log(userId))   // Non-blocking audit
        .map(__ -> new GetUserResult.Ok(user));
}
```
