---
title: Wire Formats
---

# Wire Formats

Typr Events supports multiple serialization formats for Kafka messages.

## Avro + Confluent Schema Registry

The default format. Uses binary Avro with Confluent Schema Registry for schema evolution.

```scala
val options = AvroOptions.default(...).copy(
  wireFormat = AvroWireFormat.ConfluentAvro
)
```

**Requirements:**
- Confluent Schema Registry running
- `io.confluent:kafka-avro-serializer` dependency

**Kafka properties:**
```java
props.put("schema.registry.url", "http://localhost:8081");
```

## Plain Avro

Binary Avro without Schema Registry. Schema is embedded or agreed upon out-of-band.

```scala
val options = AvroOptions.default(...).copy(
  wireFormat = AvroWireFormat.PlainAvro
)
```

**Use when:**
- No Schema Registry available
- Schema evolution handled manually
- Testing without infrastructure

## JSON

JSON serialization using your preferred JSON library.

```scala
val options = AvroOptions.default(...).copy(
  wireFormat = AvroWireFormat.JsonEncoded(JsonLib.Jackson)
)
```

**Supported JSON libraries:**

| Library | Language | Configuration |
|---------|----------|---------------|
| Jackson | Java, Kotlin, Scala | `JsonLib.Jackson` |
| Circe | Scala | `JsonLib.Circe` |
| ZIO JSON | Scala | `JsonLib.ZioJson` |

**Use when:**
- Human-readable messages preferred
- Debugging or development
- Interoperability with non-JVM systems

## Comparison

| Format | Size | Schema Registry | Human Readable | Schema Evolution |
|--------|------|-----------------|----------------|------------------|
| Avro + Confluent | Small | Required | No | Automatic |
| Plain Avro | Small | No | No | Manual |
| JSON | Large | No | Yes | Manual |
