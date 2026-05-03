---
title: Result ADT
---

# Result ADT

Typr Events generates Result ADTs (Algebraic Data Types) for RPC methods, providing type-safe error handling.

## The Problem

Traditional RPC error handling is error-prone:

```java
// Checked exceptions - clutters code
User getUser(String id) throws UserNotFoundException;

// Unchecked exceptions - easy to forget handling
User getUser(String id);  // might throw!

// Null returns - loses error information
User getUser(String id);  // null means... what?
```

## The Solution

Result ADT makes errors explicit in the type system:

```java
GetUserResult getUser(String userId);
```

## Generated Result Types

For a method with errors:

```json
{
  "response": "User",
  "errors": ["UserNotFoundError"]
}
```

### Java

```java
public sealed interface GetUserResult
    permits GetUserResult.Ok, GetUserResult.Err {

    record Ok(User value) implements GetUserResult {}
    record Err(UserNotFoundError error) implements GetUserResult {}
}
```

### Kotlin

```kotlin
sealed interface GetUserResult {
    data class Ok(val value: User) : GetUserResult
    data class Err(val error: UserNotFoundError) : GetUserResult
}
```

### Scala

```scala
enum GetUserResult:
  case Ok(value: User)
  case Err(error: UserNotFoundError)
```

## Pattern Matching

### Java

```java
var result = userService.getUser(userId);

switch (result) {
    case GetUserResult.Ok(var user) -> {
        return ResponseEntity.ok(user);
    }
    case GetUserResult.Err(var error) -> {
        return ResponseEntity.notFound()
            .body(error.message());
    }
}
```

### Kotlin

```kotlin
when (val result = userService.getUser(userId)) {
    is GetUserResult.Ok -> ResponseEntity.ok(result.value)
    is GetUserResult.Err -> ResponseEntity.notFound()
        .body(result.error.message)
}
```

### Scala

```scala
userService.getUser(userId) match
  case GetUserResult.Ok(user) => Ok(user)
  case GetUserResult.Err(error) => NotFound(error.message)
```

## Multiple Error Types

Methods can have multiple error types:

```json
{
  "response": "User",
  "errors": ["UserNotFoundError", "ValidationError"]
}
```

### Generated

```java
public sealed interface CreateUserResult
    permits CreateUserResult.Ok,
            CreateUserResult.UserNotFoundErr,
            CreateUserResult.ValidationErr {

    record Ok(User value) implements CreateUserResult {}
    record UserNotFoundErr(UserNotFoundError error) implements CreateUserResult {}
    record ValidationErr(ValidationError error) implements CreateUserResult {}
}
```

### Handling

```java
switch (result) {
    case CreateUserResult.Ok(var user) -> success(user);
    case CreateUserResult.UserNotFoundErr(var e) -> notFound(e);
    case CreateUserResult.ValidationErr(var e) -> badRequest(e);
}
```

## Void Results

For methods with `"response": "null"`:

```java
public sealed interface DeleteUserResult
    permits DeleteUserResult.Ok, DeleteUserResult.Err {

    record Ok() implements DeleteUserResult {}
    record Err(UserNotFoundError error) implements DeleteUserResult {}
}
```

## Benefits

1. **Compile-time safety** - Can't forget to handle errors
2. **Exhaustive matching** - Compiler warns if you miss a case
3. **No exceptions** - Control flow is explicit
4. **Self-documenting** - Error types are visible in the signature
5. **Composable** - Works well with functional patterns
