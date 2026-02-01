---
title: Avro Protocols
---

# Avro Protocols

Typr Events generates RPC interfaces from Avro protocol files (`.avpr`).

## Protocol Definition

```json
{
  "protocol": "UserService",
  "namespace": "com.example.service",
  "doc": "User management service",
  "types": [
    {
      "type": "record",
      "name": "User",
      "fields": [
        {"name": "id", "type": "string"},
        {"name": "email", "type": "string"},
        {"name": "name", "type": "string"},
        {"name": "createdAt", "type": {"type": "long", "logicalType": "timestamp-millis"}}
      ]
    },
    {
      "type": "error",
      "name": "UserNotFoundError",
      "fields": [
        {"name": "userId", "type": "string"},
        {"name": "message", "type": "string"}
      ]
    },
    {
      "type": "error",
      "name": "ValidationError",
      "fields": [
        {"name": "field", "type": "string"},
        {"name": "message", "type": "string"}
      ]
    }
  ],
  "messages": {
    "getUser": {
      "doc": "Get a user by ID",
      "request": [{"name": "userId", "type": "string"}],
      "response": "User",
      "errors": ["UserNotFoundError"]
    },
    "createUser": {
      "doc": "Create a new user",
      "request": [
        {"name": "email", "type": "string"},
        {"name": "name", "type": "string"}
      ],
      "response": "User",
      "errors": ["ValidationError"]
    },
    "deleteUser": {
      "doc": "Delete a user",
      "request": [{"name": "userId", "type": "string"}],
      "response": "null",
      "errors": ["UserNotFoundError"]
    },
    "notifyUser": {
      "doc": "Send notification (fire-and-forget)",
      "request": [
        {"name": "userId", "type": "string"},
        {"name": "message", "type": "string"}
      ],
      "one-way": true
    }
  }
}
```

## Generated Interface

### Java

```java
public interface UserService {
    GetUserResult getUser(String userId);
    CreateUserResult createUser(String email, String name);
    DeleteUserResult deleteUser(String userId);
    void notifyUser(String userId, String message);  // one-way
}
```

### Kotlin

```kotlin
interface UserService {
    fun getUser(userId: String): GetUserResult
    fun createUser(email: String, name: String): CreateUserResult
    fun deleteUser(userId: String): DeleteUserResult
    fun notifyUser(userId: String, message: String)  // one-way
}
```

### Scala

```scala
trait UserService:
  def getUser(userId: String): GetUserResult
  def createUser(email: String, name: String): CreateUserResult
  def deleteUser(userId: String): DeleteUserResult
  def notifyUser(userId: String, message: String): Unit  // one-way
```

## Handler Interface

For implementing the service:

```java
public interface UserServiceHandler extends UserService {
    // Inherits all methods from UserService
}
```

Implement this interface with your business logic:

```java
public class UserServiceImpl implements UserServiceHandler {
    @Override
    public GetUserResult getUser(String userId) {
        return userRepository.findById(userId)
            .map(GetUserResult.Ok::new)
            .orElseGet(() -> new GetUserResult.Err(
                new UserNotFoundError(userId, "User not found")));
    }
}
```

## Message Types

### Request-Response

Messages with `response` and optionally `errors`:

```json
{
  "request": [{"name": "userId", "type": "string"}],
  "response": "User",
  "errors": ["UserNotFoundError"]
}
```

### Void Response

Use `"response": "null"` for operations that succeed without returning data:

```json
{
  "response": "null",
  "errors": ["UserNotFoundError"]
}
```

### One-Way (Fire-and-Forget)

Use `"one-way": true` for notifications that don't expect a response:

```json
{
  "one-way": true
}
```

Generates `void` return type with no result ADT.

## Error Types

Avro `error` types generate exception-like classes:

```java
public record UserNotFoundError(
    String userId,
    String message
) { }
```

These are used in the Result ADT (see [Result ADT](result-adt.md)).
