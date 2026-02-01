---
title: Complex Unions
---

# Complex Unions

Typr Events handles Avro unions beyond simple `["null", T]` optionals.

## Simple Nullable Fields

The most common union - nullable fields:

```json
{"name": "email", "type": ["null", "string"], "default": null}
```

Generates idiomatic optional types:
- Java: `Optional<String>`
- Kotlin: `String?`
- Scala: `Option[String]`

## Multi-Type Unions

Unions with multiple non-null types generate sealed types:

### Schema

```json
{
  "name": "value",
  "type": ["string", "int", "boolean"]
}
```

### Generated (Java)

```java
public sealed interface StringOrIntOrBoolean
    permits StringOrIntOrBoolean.StringValue,
            StringOrIntOrBoolean.IntValue,
            StringOrIntOrBoolean.BooleanValue {

    record StringValue(String value) implements StringOrIntOrBoolean {}
    record IntValue(int value) implements StringOrIntOrBoolean {}
    record BooleanValue(boolean value) implements StringOrIntOrBoolean {}
}
```

### Generated (Kotlin)

```kotlin
sealed interface StringOrIntOrBoolean {
    @JvmInline value class StringValue(val value: String) : StringOrIntOrBoolean
    @JvmInline value class IntValue(val value: Int) : StringOrIntOrBoolean
    @JvmInline value class BooleanValue(val value: Boolean) : StringOrIntOrBoolean
}
```

### Generated (Scala)

```scala
enum StringOrIntOrBoolean:
  case StringValue(value: String)
  case IntValue(value: Int)
  case BooleanValue(value: Boolean)
```

## Usage

### Creating

```java
StringOrIntOrBoolean value1 = new StringOrIntOrBoolean.StringValue("hello");
StringOrIntOrBoolean value2 = new StringOrIntOrBoolean.IntValue(42);
StringOrIntOrBoolean value3 = new StringOrIntOrBoolean.BooleanValue(true);
```

### Pattern Matching

```java
switch (value) {
    case StringOrIntOrBoolean.StringValue(var s) -> process(s);
    case StringOrIntOrBoolean.IntValue(var i) -> process(i);
    case StringOrIntOrBoolean.BooleanValue(var b) -> process(b);
}
```

## Named Record Unions

Unions of named records generate a sealed interface:

### Schema

```json
{
  "name": "identifier",
  "type": ["string", "long"]
}
```

With `x-typr-wrapper`:

```json
{
  "name": "identifier",
  "type": ["string", "long"],
  "x-typr-union-name": "Identifier"
}
```

### Generated

```java
public sealed interface Identifier
    permits Identifier.StringId, Identifier.LongId {

    record StringId(String value) implements Identifier {}
    record LongId(long value) implements Identifier {}
}
```

## Nullable Multi-Type Unions

Unions with null and multiple types:

```json
{"type": ["null", "string", "int"]}
```

Generates `Optional<StringOrInt>` (Java) or `StringOrInt?` (Kotlin).

## Dynamic Values

For truly dynamic JSON-like structures:

```json
{
  "name": "metadata",
  "type": ["null", "string", "long", "double", "boolean",
           {"type": "array", "items": "DynamicValue"},
           {"type": "map", "values": "DynamicValue"}]
}
```

Generates a `DynamicValue` type that can hold any of these recursively.
