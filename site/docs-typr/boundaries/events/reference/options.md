---
title: Configuration Options
---

# Configuration Options

Complete reference for Avro/Kafka event boundary configuration in `typr.yaml`.

## Basic Configuration

```yaml
boundaries:
  events:
    type: avro
    schemas: ./schemas/**/*.avsc
```

| Option | Required | Description |
|--------|----------|-------------|
| `type` | Yes | Must be `avro` |
| `schemas` | Yes* | Path or glob pattern for `.avsc` files |
| `schema_registry` | Yes* | URL of Confluent Schema Registry |

*Either `schemas` or `schema_registry` is required.

## Schema Sources

### From Files

```yaml
boundaries:
  events:
    type: avro
    schemas: ./schemas/**/*.avsc    # Glob pattern
```

Or explicit list:

```yaml
boundaries:
  events:
    type: avro
    schemas:
      - ./schemas/OrderPlaced.avsc
      - ./schemas/OrderUpdated.avsc
      - ./schemas/UserService.avpr
```

### From Schema Registry

```yaml
boundaries:
  events:
    type: avro
    schema_registry: http://localhost:8081
    subjects:
      - "order-*"      # Glob patterns supported
      - "user-events"
```

## Wire Format

```yaml
boundaries:
  events:
    type: avro
    wire_format: confluent_registry
```

| Wire Format | Description |
|-------------|-------------|
| `confluent_registry` | Binary Avro with Schema Registry (default) |
| `binary` | Raw binary Avro without registry |
| `json` | JSON serialization |

For JSON wire format, specify the JSON library:

```yaml
boundaries:
  events:
    type: avro
    wire_format: json
    json_lib: jackson    # jackson, circe, zio_json
```

## Generation Options

```yaml
boundaries:
  events:
    type: avro
    schemas: ./schemas/**/*.avsc

    generate_records: true
    generate_serdes: true
    generate_producers: true
    generate_consumers: true
    generate_schema_validator: true
    enable_precise_types: true
```

| Option | Default | Description |
|--------|---------|-------------|
| `generate_records` | `true` | Generate record classes (event types) |
| `generate_serdes` | `true` | Generate Serializer/Deserializer classes |
| `generate_producers` | `true` | Generate type-safe producer classes |
| `generate_consumers` | `true` | Generate type-safe consumer classes |
| `generate_schema_validator` | `false` | Generate schema compatibility validator |
| `enable_precise_types` | `false` | Generate `Decimal10_2` instead of `BigDecimal` |

## Topic Configuration

### Multi-Event Topics

Group related events into a sealed interface:

```yaml
boundaries:
  events:
    type: avro
    schemas: ./schemas/**/*.avsc

    topic_groups:
      order-events:
        - com.example.events.OrderPlaced
        - com.example.events.OrderUpdated
        - com.example.events.OrderCancelled
      user-events:
        - com.example.events.UserCreated
        - com.example.events.UserUpdated
```

### Topic Key Types

```yaml
boundaries:
  events:
    type: avro
    schemas: ./schemas/**/*.avsc

    default_key_type: string

    topic_keys:
      order-events: uuid
      user-events: string
      metrics: long
```

| Key Type | JVM Type |
|----------|----------|
| `string` | `String` (default) |
| `uuid` | `UUID` |
| `long` | `Long` |
| `int` | `Integer` |
| `bytes` | `byte[]` |

## Header Schemas

Define typed Kafka headers:

```yaml
boundaries:
  events:
    type: avro
    schemas: ./schemas/**/*.avsc

    header_schemas:
      standard:
        fields:
          - name: correlationId
            type: uuid
            required: true
          - name: timestamp
            type: instant
            required: true
          - name: source
            type: string
            required: false

    default_header_schema: standard

    topic_headers:
      order-events: standard
      audit-events: standard
```

| Header Type | JVM Type |
|-------------|----------|
| `string` | `String` |
| `uuid` | `UUID` |
| `instant` | `Instant` |
| `long` | `Long` |
| `int` | `Integer` |
| `boolean` | `Boolean` |

## Framework Integration

Framework integration is configured at the output level in `typr.yaml`:

```yaml
output:
  framework: spring
  effect_type: completable_future

boundaries:
  events:
    type: avro
    schemas: ./schemas/**/*.avsc
    generate_kafka_events: true
    generate_kafka_rpc: true
```

| Framework | Annotations | Kafka Types | Effect Type |
|-----------|-------------|-------------|-------------|
| `none` | None | Raw `KafkaProducer`/`KafkaConsumer` | Configurable |
| `spring` | `@Service`, `@KafkaListener` | `KafkaTemplate`, `ReplyingKafkaTemplate` | `completable_future` |
| `quarkus` | `@ApplicationScoped`, `@Incoming` | `MutinyEmitter`, `KafkaRequestReply` | `mutiny_uni` |
| `cats` | None (constructor injection) | fs2-kafka `KafkaProducer[IO, K, V]` | `cats_io` |

### Framework Generation Options

| Option | Default | Description |
|--------|---------|-------------|
| `generate_kafka_events` | `false` | Generate framework-specific Publishers and Listeners |
| `generate_kafka_rpc` | `false` | Generate framework-specific RPC Client and Server |

**Note:** Cats framework only supports events (`generate_kafka_events`), not RPC. fs2-kafka does not have a built-in request-reply pattern.

### What Each Framework Generates

**None (vanilla):**
- `OrderEventsProducer` - wraps raw `KafkaProducer`
- `OrderEventsConsumer` - poll-based consumer with handler dispatch

**Spring:**
- `OrderEventsPublisher` - uses `KafkaTemplate`, `@Service` annotation
- `OrderEventsListener` - interface with `@KafkaListener` on receive method

**Quarkus:**
- `OrderEventsPublisher` - uses `MutinyEmitter` with `@Channel`
- `OrderEventsListener` - interface with `@Incoming` annotation

**Cats:**
- `OrderEventsPublisher` - uses `KafkaProducer[IO, K, V]` from fs2-kafka
- `OrderEventsListener` - trait with `receive(record: ConsumerRecord[String, Any]): IO[Unit]`

For Cats, wire the Listener to your fs2-kafka consumer stream:
```scala
KafkaConsumer.stream(settings)
  .subscribeTo("order-events")
  .records
  .evalMap(record => listener.receive(record))
```

## Effect Type

Effect type is configured at the output level:

```yaml
output:
  effect_type: cats_io
```

| Effect Type | Return Type | Use Case |
|-------------|-------------|----------|
| `blocking` | `T` | Synchronous |
| `completable_future` | `CompletableFuture<T>` | Async Java |
| `mutiny_uni` | `Uni<T>` | Quarkus |
| `cats_io` | `IO[T]` | Cats Effect |
| `zio` | `Task[T]` | ZIO |

## JSON Library

```yaml
output:
  json_lib: jackson
```

| JSON Library | Languages | Description |
|--------------|-----------|-------------|
| `jackson` | Java, Kotlin, Scala | Jackson databind |
| `circe` | Scala | Circe codecs |
| `zio_json` | Scala | ZIO JSON |

## Full Example

```yaml
version: 1

output:
  path: ./generated
  package: com.example
  language: java
  framework: spring
  effect_type: completable_future
  json_lib: jackson

boundaries:
  events:
    type: avro
    schemas: ./schemas/**/*.avsc
    wire_format: confluent_registry

    # Generation options
    generate_records: true
    generate_serdes: true
    generate_producers: true
    generate_consumers: true
    generate_schema_validator: true
    enable_precise_types: true
    generate_kafka_events: true
    generate_kafka_rpc: true

    # Topic configuration
    topic_groups:
      order-events:
        - com.example.events.OrderPlaced
        - com.example.events.OrderUpdated
        - com.example.events.OrderCancelled

    default_key_type: string
    topic_keys:
      order-events: string

    # Header configuration
    header_schemas:
      standard:
        fields:
          - name: correlationId
            type: uuid
            required: true
          - name: timestamp
            type: instant
            required: true
          - name: source
            type: string
            required: false

    default_header_schema: standard
```
