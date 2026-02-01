---
title: Configuration Options
---

# Configuration Options

Complete reference for `AvroOptions` configuration.

## Basic Options

```scala
val options = AvroOptions.default(
  pkg = jvm.QIdent.parse("com.example.events"),
  schemaSource = SchemaSource.Directory(Path.of("schemas"))
)
```

| Option | Type | Description |
|--------|------|-------------|
| `pkg` | `jvm.QIdent` | Base package for generated code |
| `schemaSource` | `SchemaSource` | Where to find Avro schemas |

## Schema Source

```scala
// Directory containing .avsc and .avpr files
SchemaSource.Directory(Path.of("schemas"))

// Explicit list of files
SchemaSource.Files(List(
  Path.of("OrderPlaced.avsc"),
  Path.of("UserService.avpr")
))
```

## Code Generation Options

```scala
options.copy(
  generateSchemaValidator = true,
  enablePreciseTypes = true,
  generateMockRepos = false
)
```

| Option | Default | Description |
|--------|---------|-------------|
| `generateSchemaValidator` | `false` | Generate schema validation utility |
| `enablePreciseTypes` | `false` | Generate `Decimal10_2` instead of `BigDecimal` |
| `generateMockRepos` | `false` | Generate mock implementations for testing |

## Wire Format

```scala
options.copy(
  wireFormat = AvroWireFormat.ConfluentAvro
)
```

| Wire Format | Description |
|-------------|-------------|
| `AvroWireFormat.ConfluentAvro` | Binary Avro with Confluent Schema Registry |
| `AvroWireFormat.PlainAvro` | Binary Avro without registry |
| `AvroWireFormat.JsonEncoded(jsonLib)` | JSON serialization |

## Effect Type

```scala
options.copy(
  effectType = EffectType.CompletableFuture
)
```

| Effect Type | Return Type | Use Case |
|-------------|-------------|----------|
| `EffectType.Blocking` | `T` | Synchronous |
| `EffectType.CompletableFuture` | `CompletableFuture<T>` | Async Java |
| `EffectType.Mutiny` | `Uni<T>` | Quarkus |
| `EffectType.CatsIO` | `IO[T]` | Cats Effect |
| `EffectType.ZIO` | `Task[T]` | ZIO |

## Header Schemas

```scala
options.copy(
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

## Framework Integration

```scala
options.copy(
  frameworkIntegration = FrameworkIntegration.Spring,
  generateKafkaEvents = true,
  generateKafkaRpc = true
)
```

| Framework | Description |
|-----------|-------------|
| `FrameworkIntegration.None` | No framework annotations |
| `FrameworkIntegration.Spring` | Spring Boot annotations |
| `FrameworkIntegration.Quarkus` | Quarkus CDI annotations |

## JSON Library

```scala
options.copy(
  jsonLibs = List(JsonLib.Jackson)
)
```

| JSON Library | Languages | Description |
|--------------|-----------|-------------|
| `JsonLib.Jackson` | Java, Kotlin, Scala | Jackson databind |
| `JsonLib.Circe` | Scala | Circe codecs |
| `JsonLib.ZioJson` | Scala | ZIO JSON |

## Full Example

```scala
val options = AvroOptions.default(
  pkg = jvm.QIdent.parse("com.example.events"),
  schemaSource = SchemaSource.Directory(Path.of("schemas"))
).copy(
  wireFormat = AvroWireFormat.ConfluentAvro,
  effectType = EffectType.CompletableFuture,
  generateSchemaValidator = true,
  enablePreciseTypes = true,
  headerSchemas = Map(
    "standard" -> HeaderSchema(List(
      HeaderField("correlationId", HeaderType.UUID, required = true),
      HeaderField("timestamp", HeaderType.Instant, required = true)
    ))
  ),
  defaultHeaderSchema = Some("standard"),
  frameworkIntegration = FrameworkIntegration.Spring,
  generateKafkaEvents = true,
  generateKafkaRpc = true,
  jsonLibs = List(JsonLib.Jackson)
)
```
