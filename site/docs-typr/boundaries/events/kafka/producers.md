---
title: Typed Producers
---

# Typed Producers

Typr Events generates type-safe Kafka producers from your Avro schemas.

## Basic Usage

### Java

```java
// Create typed producer
var props = new Properties();
props.put("bootstrap.servers", "localhost:9092");
props.put("schema.registry.url", "http://localhost:8081");

var kafkaProducer = new KafkaProducer<>(props,
    new StringSerializer(),
    Topics.ORDER_EVENTS.valueSerde().serializer());

var producer = new OrderEventsProducer(kafkaProducer, Topics.ORDER_EVENTS.name());

// Send with full type safety
var event = new OrderPlaced(
    UUID.randomUUID(),
    12345L,
    Decimal10_2.unsafeForce(new BigDecimal("99.99")),
    Instant.now(),
    List.of("SKU-001", "SKU-002"),
    Optional.empty()
);

producer.send("order-123", event, headers).get();
```

### Kotlin

```kotlin
val producer = OrderEventsProducer(kafkaProducer, Topics.ORDER_EVENTS.name())

val event = OrderPlaced(
    orderId = UUID.randomUUID(),
    customerId = 12345L,
    totalAmount = Decimal10_2.unsafeForce(BigDecimal("99.99")),
    placedAt = Instant.now(),
    items = listOf("SKU-001", "SKU-002"),
    shippingAddress = null
)

producer.send("order-123", event, headers)
```

### Scala

```scala
val producer = OrderEventsProducer(kafkaProducer, Topics.ORDER_EVENTS.name)

val event = OrderPlaced(
  orderId = UUID.randomUUID(),
  customerId = 12345L,
  totalAmount = Decimal10_2.unsafeForce(BigDecimal("99.99")),
  placedAt = Instant.now(),
  items = List("SKU-001", "SKU-002"),
  shippingAddress = None
)

producer.send("order-123", event, headers)
```

## Multi-Event Topics

When a topic can have multiple event types, the producer accepts any event from the sealed interface:

```java
// All these are valid - producer accepts any OrderEvents type
producer.send("order-123", new OrderPlaced(...), headers);
producer.send("order-123", new OrderUpdated(...), headers);
producer.send("order-123", new OrderCancelled(...), headers);
```

The compiler ensures you can only send valid event types for this topic.

## Typed Headers

Producers include typed headers for correlation and tracing:

```java
var headers = new StandardHeaders(
    UUID.randomUUID(),           // correlationId
    Instant.now(),               // timestamp
    Optional.of("order-service") // source (optional)
);

producer.send("order-123", event, headers);
```

## Async Sending

### CompletableFuture

```java
producer.send("key", event, headers)
    .thenAccept(metadata -> {
        log.info("Sent to partition {} offset {}",
            metadata.partition(), metadata.offset());
    })
    .exceptionally(ex -> {
        log.error("Send failed", ex);
        return null;
    });
```

### Batch Sending

```java
var futures = events.stream()
    .map(event -> producer.send(event.orderId().toString(), event, headers))
    .toList();

CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
```

## Topic Configuration

Topics are defined as constants with their serializers:

```java
public final class Topics {
    public static final TypedTopic<String, OrderEvents> ORDER_EVENTS =
        new TypedTopic<>("order-events", OrderEventsSerde.instance());
}
```

Use the topic constant for both producers and consumers to ensure consistency.
