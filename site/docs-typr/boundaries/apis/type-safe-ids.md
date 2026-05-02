---
title: Type-Safe IDs
sidebar_position: 2
---

# Type-Safe ID Types

Typr generates **zero-overhead type wrappers** for all schema types marked with `format: id` or similar patterns. This eliminates primitive obsession and provides compile-time safety.

## The Problem

Standard OpenAPI generators produce code with strings everywhere:

```java
// ❌ Primitive obsession - easy to mix up parameters
void getPet(String petId);
void getOwner(String ownerId);

// Oops! Wrong ID passed - compiles fine, fails at runtime
getPet(ownerId);
```

## The Solution

Typr generates dedicated ID types that catch mistakes at compile time:

### Java (record)

```java
/** Unique pet identifier */
public record PetId(@JsonValue String value) {
  @Override
  public String toString() {
    return value;
  }
}
```

### Kotlin (data class)

```kotlin
/** Unique pet identifier */
data class PetId @JsonCreator constructor(
  @get:JsonValue val value: String
) {
  override fun toString(): String = value
}
```

### Scala (value class)

```scala
/** Unique pet identifier */
case class PetId(value: String) extends AnyVal

object PetId {
  implicit val decoder: Decoder[PetId] = Decoder[String].map(PetId.apply)
  implicit val encoder: Encoder[PetId] = Encoder[String].contramap(_.value)

  /** Path extractor for Http4s routes */
  def unapply(str: String): Option[PetId] = Some(PetId(str))
}
```

## Usage in APIs

With type-safe IDs, the compiler prevents mixing up different ID types:

```java
// ✅ Type-safe - compiler enforces correct ID types
void getPet(PetId petId);
void getOwner(OwnerId ownerId);

// ❌ Compile error! Cannot pass OwnerId where PetId is expected
getPet(ownerId);
```

This propagates through your entire codebase - from HTTP endpoints to database queries.
