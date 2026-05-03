---
title: Typed Headers
---

# Typed Headers

Typr Events generates strongly-typed Kafka headers for correlation, tracing, and metadata.

## Defining Header Schemas

Configure header schemas in your generation options:

```scala
val options = AvroOptions.default(...).copy(
  headerSchemas = Map(
    "standard" -> HeaderSchema(List(
      HeaderField("correlationId", HeaderType.UUID, required = true),
      HeaderField("timestamp", HeaderType.Instant, required = true),
      HeaderField("source", HeaderType.String, required = false)
    ))
  ),
  defaultHeaderSchema = Some("standard")
)
```

## Generated Header Class

```java
public record StandardHeaders(
    UUID correlationId,
    Instant timestamp,
    Optional<String> source
) {
    // Serialize to Kafka headers
    public Headers toKafkaHeaders() { ... }

    // Deserialize from Kafka headers
    public static StandardHeaders fromKafkaHeaders(Headers headers) { ... }
}
```

## Supported Header Types

| Header Type | Java Type | Serialization |
|-------------|-----------|---------------|
| `HeaderType.String` | `String` | UTF-8 bytes |
| `HeaderType.UUID` | `UUID` | String representation |
| `HeaderType.Instant` | `Instant` | ISO-8601 string |
| `HeaderType.Long` | `Long` | String representation |
| `HeaderType.Int` | `Integer` | String representation |
| `HeaderType.Boolean` | `Boolean` | "true" / "false" |

## Using Headers

### Producing

```java
var headers = new StandardHeaders(
    UUID.randomUUID(),
    Instant.now(),
    Optional.of("order-service")
);

producer.send("order-123", event, headers);
```

### Consuming

Headers are automatically deserialized and passed to handlers:

```java
@Override
public void handleOrderPlaced(String key, OrderPlaced event, StandardHeaders headers) {
    log.info("Processing order {} with correlation {}",
        event.orderId(), headers.correlationId());

    // Propagate correlation ID to downstream calls
    downstreamClient.call(headers.correlationId());
}
```

## Multiple Header Schemas

You can define multiple header schemas for different use cases:

```scala
val options = AvroOptions.default(...).copy(
  headerSchemas = Map(
    "standard" -> HeaderSchema(List(
      HeaderField("correlationId", HeaderType.UUID, required = true),
      HeaderField("timestamp", HeaderType.Instant, required = true)
    )),
    "audit" -> HeaderSchema(List(
      HeaderField("correlationId", HeaderType.UUID, required = true),
      HeaderField("userId", HeaderType.String, required = true),
      HeaderField("action", HeaderType.String, required = true),
      HeaderField("timestamp", HeaderType.Instant, required = true)
    ))
  ),
  defaultHeaderSchema = Some("standard")
)
```

## Tracing Integration

Headers are ideal for distributed tracing context:

```java
var headers = new StandardHeaders(
    UUID.randomUUID(),
    Instant.now(),
    Optional.of(Span.current().getSpanContext().getTraceId())
);
```
