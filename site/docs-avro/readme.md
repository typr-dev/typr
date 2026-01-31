---
title: Typr Events
---

# Typr Events

Type-safe code generation for Apache Kafka and Avro schemas. No more `Object` types, no more manual casting, no more runtime surprises.

## What is Typr Events?

Typr Events generates type-safe JVM code from Avro schemas (`.avsc`) and protocols (`.avpr`). You write schemas, run the generator, and get clean, immutable data classes with typed Kafka producers and consumers.

**Before (Standard Avro tooling):**
```java
public class OrderPlaced extends SpecificRecordBase {
  private Object orderId;        // Actually a UUID, but typed as Object
  private Object customerId;     // Actually a Long
  public Object get(int field) { ... }  // No type safety
}

Object value = order.get(0);
UUID id = (UUID) value;  // Runtime cast, might fail
```

**After (Typr Events):**
```java
public record OrderPlaced(
    UUID orderId,
    Long customerId,
    BigDecimal totalAmount,
    List<String> items
) implements OrderEvents {
    // Immutable, all fields properly typed
}

UUID id = order.orderId();  // No casting needed
```

## Features

| Feature | Description |
|---------|-------------|
| **Immutable Records** | Java records, Kotlin data classes, Scala case classes |
| **Typed Producers** | `producer.send(orderId, new OrderPlaced(...))` |
| **Typed Consumers** | Handler interface with one method per event type |
| **Multi-Event Topics** | Sealed interfaces for topics with multiple event types |
| **Complex Unions** | `["string", "int", "boolean"]` → `StringOrIntOrBoolean` |
| **Wrapper Types** | `x-typr-wrapper` for type-safe IDs |
| **Precise Types** | `Decimal10_2` with compile-time constraint validation |
| **Typed Headers** | Strongly-typed Kafka headers |
| **RPC Support** | Request/reply from `.avpr` protocols |
| **Framework Integration** | Spring Boot, Quarkus with DI annotations |

## Supported Languages

- **Java 21+** - Records with pattern matching
- **Kotlin 2.0+** - Data classes with nullable types
- **Scala 3** - Case classes with Option types

## Wire Formats

| Format | Description |
|--------|-------------|
| **Avro + Confluent** | Binary Avro with Schema Registry |
| **Avro** | Plain binary Avro |
| **JSON** | Jackson, Circe, or ZIO JSON |

## Effect Types

| Effect | Return Type | Use Case |
|--------|-------------|----------|
| `Blocking` | `T` | Synchronous code |
| `CompletableFuture` | `CompletableFuture<T>` | Async Java |
| `Mutiny` | `Uni<T>` | Quarkus |
| `CatsIO` | `IO[T]` | Cats Effect |
| `ZIO` | `Task[T]` | ZIO |

## Next Steps

- [Quick Start](setup.md) - Get up and running
- [Schema Types](what-is/schemas.md) - Records, enums, unions
- [Kafka Producers](kafka/producers.md) - Type-safe event publishing
- [Kafka Consumers](kafka/consumers.md) - Type-safe event handling
