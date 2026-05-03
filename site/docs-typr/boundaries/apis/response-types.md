---
title: Response Types
sidebar_position: 3
---

# Type-Safe Response Handling

Typr generates **sealed response types** that enable exhaustive pattern matching for HTTP status codes. No more unchecked casts or missing error handling.

## The Problem

Traditional OpenAPI generators return generic types, forcing unsafe casts:

```java
// ❌ No type safety - what if it's a 404?
Object response = api.getPet(petId);
Pet pet = (Pet) response; // Runtime crash if 404!
```

## The Solution

Typr generates sealed interfaces that represent all possible responses:

### Java

```java
/** Response type for: 200, 404 */
public sealed interface Response200404<T200, T404> permits Ok, NotFound {}

/** HTTP 200 response */
public record Ok<T200, T404>(T200 value) implements Response200404<T200, T404> {}

/** HTTP 404 response */
public record NotFound<T200, T404>(T404 value) implements Response200404<T200, T404> {}
```

Usage with pattern matching:

```java
Response200404<Pet, Error> response = api.getPet(petId);

// ✅ Exhaustive - compiler ensures all cases handled
return switch (response) {
    case Ok<Pet, Error> r -> processPet(r.value());
    case NotFound<Pet, Error> r -> handleError(r.value());
};
```

### Kotlin

```kotlin
sealed interface Response200404<out T200, out T404>

data class Ok<out T200>(val value: T200) : Response200404<T200, Nothing>
data class NotFound<out T404>(val value: T404) : Response200404<Nothing, T404>
```

Usage:

```kotlin
when (val response = api.getPet(petId)) {
    is Ok -> processPet(response.value)
    is NotFound -> handleError(response.value)
}
```

### Scala

```scala
sealed trait Response200404[+T200, +T404]

case class Ok[T200](value: T200) extends Response200404[T200, Nothing]
case class NotFound[T404](value: T404) extends Response200404[Nothing, T404]
```

Usage:

```scala
api.getPet(petId).map {
  case Ok(pet) => processPet(pet)
  case NotFound(error) => handleError(error)
}
```

## Range Status Codes

Typr also supports HTTP status code ranges (4XX, 5XX):

```java
public record ClientError4XX<T>(int statusCode, T value)
    implements Response2004XX5XX<Nothing> {}

public record ServerError5XX<T>(int statusCode, T value)
    implements Response2004XX5XX<Nothing> {}
```

This enables handling all 4xx errors uniformly while still having access to the specific status code.
