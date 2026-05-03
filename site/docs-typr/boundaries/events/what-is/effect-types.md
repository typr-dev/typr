---
title: Effect Types
---

# Effect Types

Typr Events generates code that works with different async/effect systems.

## Available Effect Types

| Effect | Return Type | Use Case |
|--------|-------------|----------|
| `Blocking` | `T` | Synchronous code |
| `CompletableFuture` | `CompletableFuture<T>` | Async Java |
| `Mutiny` | `Uni<T>` | Quarkus |
| `CatsIO` | `IO[T]` | Cats Effect (Scala) |
| `ZIO` | `Task[T]` | ZIO (Scala) |

## Configuration

```scala
val options = AvroOptions.default(...).copy(
  effectType = EffectType.CompletableFuture  // or Blocking, Mutiny, CatsIO, ZIO
)
```

## Examples by Effect Type

### Blocking (Synchronous)

```java
// Producer - blocks until send completes
producer.send("order-123", event, headers);

// Consumer handler
public void handleOrderPlaced(String key, OrderPlaced event, StandardHeaders headers) {
    // Process synchronously
}
```

### CompletableFuture (Async Java)

```java
// Producer - returns immediately
CompletableFuture<RecordMetadata> future = producer.send("order-123", event, headers);
future.thenAccept(metadata -> log.info("Sent to partition {}", metadata.partition()));

// Can compose multiple sends
CompletableFuture.allOf(
    producer.send("key1", event1, headers),
    producer.send("key2", event2, headers)
).join();
```

### Mutiny (Quarkus)

```java
// Producer
Uni<Void> result = producer.send("order-123", event, headers);
result.subscribe().with(
    success -> log.info("Sent"),
    failure -> log.error("Failed", failure)
);

// Consumer handler
public Uni<Void> handleOrderPlaced(String key, OrderPlaced event, Metadata metadata) {
    return processAsync(event);
}
```

### Cats Effect (Scala)

```scala
// Producer
val result: IO[RecordMetadata] = producer.send("order-123", event, headers)

// Compose with other effects
for {
  _ <- producer.send("key1", event1, headers)
  _ <- producer.send("key2", event2, headers)
  _ <- IO.println("Both sent")
} yield ()
```

### ZIO

```scala
// Producer
val result: Task[RecordMetadata] = producer.send("order-123", event, headers)

// ZIO composition
for {
  _ <- producer.send("key1", event1, headers)
  _ <- producer.send("key2", event2, headers)
  _ <- ZIO.logInfo("Both sent")
} yield ()
```

## Language Support

| Effect Type | Java | Kotlin | Scala |
|-------------|------|--------|-------|
| Blocking | Yes | Yes | Yes |
| CompletableFuture | Yes | Yes | Yes |
| Mutiny | Yes | Yes | - |
| CatsIO | - | - | Yes |
| ZIO | - | - | Yes |
