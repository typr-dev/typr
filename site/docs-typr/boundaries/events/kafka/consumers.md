---
title: Typed Consumers
---

# Typed Consumers

Typr Events generates type-safe Kafka consumers with exhaustive event handling.

## Handler Interface

For multi-event topics, implement the generated handler interface:

```java
class OrderHandler implements OrderEventsHandler {
    @Override
    public void handleOrderPlaced(String key, OrderPlaced event, StandardHeaders headers) {
        System.out.printf("Order %s placed by customer %d%n",
            event.orderId(), event.customerId());
    }

    @Override
    public void handleOrderUpdated(String key, OrderUpdated event, StandardHeaders headers) {
        System.out.printf("Order %s updated to %s%n",
            event.orderId(), event.newStatus());
    }

    @Override
    public void handleOrderCancelled(String key, OrderCancelled event, StandardHeaders headers) {
        System.out.printf("Order %s cancelled: %s%n",
            event.orderId(), event.reason());
    }
}
```

**Key benefit:** The compiler ensures you handle every event type. When you add a new event to the schema, compilation fails until you add the handler method.

## Consumer Setup

### Java

```java
var props = new Properties();
props.put("bootstrap.servers", "localhost:9092");
props.put("schema.registry.url", "http://localhost:8081");
props.put("group.id", "order-processor");
props.put("auto.offset.reset", "earliest");

var kafkaConsumer = new KafkaConsumer<>(props,
    new StringDeserializer(),
    Topics.ORDER_EVENTS.valueSerde().deserializer());

var consumer = new OrderEventsConsumer(
    kafkaConsumer,
    new OrderHandler(),
    Topics.ORDER_EVENTS.name()
);

kafkaConsumer.subscribe(List.of(Topics.ORDER_EVENTS.name()));

// Poll loop
while (running) {
    consumer.poll(Duration.ofMillis(100));
}
```

### Kotlin

```kotlin
val consumer = OrderEventsConsumer(
    kafkaConsumer,
    object : OrderEventsHandler {
        override fun handleOrderPlaced(key: String, event: OrderPlaced, headers: StandardHeaders) {
            println("Order ${event.orderId} placed")
        }
        override fun handleOrderUpdated(key: String, event: OrderUpdated, headers: StandardHeaders) {
            println("Order ${event.orderId} updated")
        }
        override fun handleOrderCancelled(key: String, event: OrderCancelled, headers: StandardHeaders) {
            println("Order ${event.orderId} cancelled")
        }
    },
    Topics.ORDER_EVENTS.name()
)
```

### Scala

```scala
val handler = new OrderEventsHandler {
  def handleOrderPlaced(key: String, event: OrderPlaced, headers: StandardHeaders): Unit =
    println(s"Order ${event.orderId} placed")

  def handleOrderUpdated(key: String, event: OrderUpdated, headers: StandardHeaders): Unit =
    println(s"Order ${event.orderId} updated")

  def handleOrderCancelled(key: String, event: OrderCancelled, headers: StandardHeaders): Unit =
    println(s"Order ${event.orderId} cancelled")
}

val consumer = OrderEventsConsumer(kafkaConsumer, handler, Topics.ORDER_EVENTS.name)
```

## Single-Event Topics

For topics with a single event type, use direct deserialization:

```java
var consumer = new KafkaConsumer<>(props,
    new StringDeserializer(),
    AddressSerde.instance().deserializer());

consumer.subscribe(List.of("addresses"));

while (running) {
    var records = consumer.poll(Duration.ofMillis(100));
    for (var record : records) {
        Address address = record.value();  // Directly typed
        process(address);
    }
}
```

## Async Handlers

With async effect types, handlers return the effect:

### CompletableFuture

```java
class AsyncOrderHandler implements OrderEventsHandler {
    @Override
    public CompletableFuture<Void> handleOrderPlaced(
            String key, OrderPlaced event, StandardHeaders headers) {
        return processAsync(event);
    }
}
```

### Mutiny (Quarkus)

```java
class ReactiveOrderHandler implements OrderEventsHandler {
    @Override
    public Uni<Void> handleOrderPlaced(
            String key, OrderPlaced event, Metadata metadata) {
        return processReactive(event);
    }
}
```

## Error Handling

Handle deserialization errors separately from processing errors:

```java
try {
    consumer.poll(Duration.ofMillis(100));
} catch (SerializationException e) {
    // Schema mismatch or corrupt message
    log.error("Failed to deserialize message", e);
    // Skip or dead-letter the message
}
```
