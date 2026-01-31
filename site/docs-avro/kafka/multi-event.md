---
title: Multi-Event Topics
---

# Multi-Event Topics

Typr Events handles Kafka topics that carry multiple event types through sealed interfaces.

## Directory-Based Grouping

Schemas in the same subdirectory become variants of a sealed interface:

```
schemas/
└── order-events/          # Directory name → interface name
    ├── OrderPlaced.avsc
    ├── OrderUpdated.avsc
    └── OrderCancelled.avsc
```

## Generated Sealed Interface

### Java 21+

```java
public sealed interface OrderEvents
    permits OrderPlaced, OrderUpdated, OrderCancelled {
}

public record OrderPlaced(...) implements OrderEvents { }
public record OrderUpdated(...) implements OrderEvents { }
public record OrderCancelled(...) implements OrderEvents { }
```

### Kotlin

```kotlin
sealed interface OrderEvents

data class OrderPlaced(...) : OrderEvents
data class OrderUpdated(...) : OrderEvents
data class OrderCancelled(...) : OrderEvents
```

### Scala

```scala
sealed trait OrderEvents

case class OrderPlaced(...) extends OrderEvents
case class OrderUpdated(...) extends OrderEvents
case class OrderCancelled(...) extends OrderEvents
```

## Type-Safe Pattern Matching

### Java

```java
switch (event) {
    case OrderPlaced e -> processPlaced(e);
    case OrderUpdated e -> processUpdated(e);
    case OrderCancelled e -> processCancelled(e);
}
```

### Kotlin

```kotlin
when (event) {
    is OrderPlaced -> processPlaced(event)
    is OrderUpdated -> processUpdated(event)
    is OrderCancelled -> processCancelled(event)
}
```

### Scala

```scala
event match {
  case e: OrderPlaced => processPlaced(e)
  case e: OrderUpdated => processUpdated(e)
  case e: OrderCancelled => processCancelled(e)
}
```

## Exhaustive Handler Interface

The generated handler interface enforces exhaustive handling:

```java
public interface OrderEventsHandler {
    void handleOrderPlaced(String key, OrderPlaced event, StandardHeaders headers);
    void handleOrderUpdated(String key, OrderUpdated event, StandardHeaders headers);
    void handleOrderCancelled(String key, OrderCancelled event, StandardHeaders headers);
}
```

**When you add a new event type:**
1. Add `OrderRefunded.avsc` to `schemas/order-events/`
2. Regenerate code
3. Compilation fails until you implement `handleOrderRefunded`

## Unified Serde

A single serializer/deserializer handles all event types:

```java
// Serde works for any OrderEvents variant
OrderEventsSerde serde = OrderEventsSerde.instance();

// Serializes based on actual type
byte[] bytes = serde.serializer().serialize("topic", orderPlaced);
byte[] bytes = serde.serializer().serialize("topic", orderCancelled);

// Deserializes to correct variant
OrderEvents event = serde.deserializer().deserialize("topic", bytes);
```

## Topic Definition

```java
public final class Topics {
    public static final TypedTopic<String, OrderEvents> ORDER_EVENTS =
        new TypedTopic<>("order-events", OrderEventsSerde.instance());
}
```

## Standalone Records

Schemas at the root level (not in a subdirectory) generate standalone records without a sealed interface:

```
schemas/
├── order-events/        # → sealed interface
│   └── ...
└── Address.avsc         # → standalone record
```

```java
// No interface, just a record
public record Address(
    String street,
    String city,
    String zipCode
) { }
```
