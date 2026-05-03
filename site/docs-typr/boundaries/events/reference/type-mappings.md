---
title: Type Mappings
---

# Type Mappings

How Avro types map to JVM types across languages.

## Primitive Types

| Avro Type | Java | Kotlin | Scala |
|-----------|------|--------|-------|
| `null` | `Void` | `Nothing?` | `Null` |
| `boolean` | `boolean` | `Boolean` | `Boolean` |
| `int` | `int` | `Int` | `Int` |
| `long` | `long` | `Long` | `Long` |
| `float` | `float` | `Float` | `Float` |
| `double` | `double` | `Double` | `Double` |
| `bytes` | `byte[]` | `ByteArray` | `Array[Byte]` |
| `string` | `String` | `String` | `String` |

## Logical Types

| Avro Logical Type | Java | Kotlin | Scala |
|-------------------|------|--------|-------|
| `uuid` | `UUID` | `UUID` | `UUID` |
| `date` | `LocalDate` | `LocalDate` | `LocalDate` |
| `time-millis` | `LocalTime` | `LocalTime` | `LocalTime` |
| `time-micros` | `LocalTime` | `LocalTime` | `LocalTime` |
| `timestamp-millis` | `Instant` | `Instant` | `Instant` |
| `timestamp-micros` | `Instant` | `Instant` | `Instant` |
| `local-timestamp-millis` | `LocalDateTime` | `LocalDateTime` | `LocalDateTime` |
| `local-timestamp-micros` | `LocalDateTime` | `LocalDateTime` | `LocalDateTime` |
| `decimal(p, s)` | `BigDecimal` | `BigDecimal` | `BigDecimal` |
| `decimal(p, s)` (precise) | `DecimalP_S` | `DecimalP_S` | `DecimalP_S` |

## Complex Types

| Avro Type | Java | Kotlin | Scala |
|-----------|------|--------|-------|
| `record` | `record` | `data class` | `case class` |
| `enum` | `enum` | `enum class` | `enum` |
| `array<T>` | `List<T>` | `List<T>` | `List[T]` |
| `map<T>` | `Map<String, T>` | `Map<String, T>` | `Map[String, T]` |
| `fixed(n)` | `byte[]` | `ByteArray` | `Array[Byte]` |

## Optional Types

| Avro Union | Java | Kotlin | Scala |
|------------|------|--------|-------|
| `["null", "T"]` | `Optional<T>` | `T?` | `Option[T]` |
| `["null", "string"]` | `Optional<String>` | `String?` | `Option[String]` |
| `["null", "long"]` | `Optional<Long>` | `Long?` | `Option[Long]` |

## Complex Unions

| Avro Union | Generated Type |
|------------|----------------|
| `["string", "int"]` | `StringOrInt` (sealed interface) |
| `["string", "int", "boolean"]` | `StringOrIntOrBoolean` |
| `["null", "string", "int"]` | `Optional<StringOrInt>` / `StringOrInt?` / `Option[StringOrInt]` |

## Wrapper Types

With `x-typr-wrapper` annotation:

| Base Type | Wrapper (Java) | Wrapper (Kotlin) | Wrapper (Scala) |
|-----------|----------------|------------------|-----------------|
| `string` | `record Foo(String value)` | `value class Foo(val value: String)` | `opaque type Foo = String` |
| `long` | `record Foo(long value)` | `value class Foo(val value: Long)` | `opaque type Foo = Long` |
| `uuid` | `record Foo(UUID value)` | `value class Foo(val value: UUID)` | `opaque type Foo = UUID` |

## Records

| Feature | Java | Kotlin | Scala |
|---------|------|--------|-------|
| Base type | `record` | `data class` | `case class` |
| Immutable | Yes | Yes | Yes |
| Pattern matching | Yes (21+) | Yes | Yes |
| Copy/wither | `withField()` | `copy()` | `copy()` |

## Enums

| Feature | Java | Kotlin | Scala |
|---------|------|--------|-------|
| Base type | `enum` | `enum class` | `enum` |
| Ordinal access | `ordinal()` | `ordinal` | `ordinal` |
| Name access | `name()` | `name` | `toString` |

## Sealed Interfaces (Multi-Event Topics)

| Feature | Java | Kotlin | Scala |
|---------|------|--------|-------|
| Interface | `sealed interface` | `sealed interface` | `sealed trait` |
| Exhaustive matching | Yes (21+) | Yes | Yes |
| Permits clause | Explicit | Implicit | Implicit |
