---
title: Wrapper Types
---

# Wrapper Types

Typr Events generates wrapper types for type-safe IDs and domain values using the `x-typr-wrapper` annotation.

## The Problem

Without wrapper types, IDs are just primitives:

```java
// Easy to mix up!
void processOrder(String orderId, String customerId, String productId) {
    // Which is which?
}

processOrder(customerId, orderId, productId);  // Compiles but wrong!
```

## The Solution

Add `x-typr-wrapper` to your schema fields:

```json
{
  "type": "record",
  "name": "CustomerOrder",
  "fields": [
    {
      "name": "orderId",
      "type": "string",
      "x-typr-wrapper": "OrderId"
    },
    {
      "name": "customerId",
      "type": "long",
      "x-typr-wrapper": "CustomerId"
    },
    {
      "name": "email",
      "type": ["null", "string"],
      "default": null,
      "x-typr-wrapper": "Email"
    }
  ]
}
```

## Generated Wrapper Types

### Java

```java
public record OrderId(String value) {
    public static OrderId of(String value) {
        return new OrderId(value);
    }
}

public record CustomerId(long value) {
    public static CustomerId of(long value) {
        return new CustomerId(value);
    }
}

public record CustomerOrder(
    OrderId orderId,
    CustomerId customerId,
    Optional<Email> email
) { }
```

### Kotlin

```kotlin
@JvmInline
value class OrderId(val value: String)

@JvmInline
value class CustomerId(val value: Long)

data class CustomerOrder(
    val orderId: OrderId,
    val customerId: CustomerId,
    val email: Email?
)
```

### Scala

```scala
opaque type OrderId = String
object OrderId:
  def apply(value: String): OrderId = value
  extension (id: OrderId) def value: String = id

opaque type CustomerId = Long
object CustomerId:
  def apply(value: Long): CustomerId = value
  extension (id: CustomerId) def value: Long = id

case class CustomerOrder(
  orderId: OrderId,
  customerId: CustomerId,
  email: Option[Email]
)
```

## Type Safety in Action

```java
void processOrder(OrderId orderId, CustomerId customerId) {
    // Types are distinct
}

OrderId orderId = OrderId.of("ORD-123");
CustomerId customerId = CustomerId.of(456L);

processOrder(orderId, customerId);     // Compiles
processOrder(customerId, orderId);     // Compilation error!
```

## Supported Base Types

| Base Type | Java Wrapper | Kotlin | Scala |
|-----------|--------------|--------|-------|
| `string` | `record Foo(String value)` | `value class Foo(val value: String)` | `opaque type Foo = String` |
| `long` | `record Foo(long value)` | `value class Foo(val value: Long)` | `opaque type Foo = Long` |
| `int` | `record Foo(int value)` | `value class Foo(val value: Int)` | `opaque type Foo = Int` |
| `uuid` | `record Foo(UUID value)` | `value class Foo(val value: UUID)` | `opaque type Foo = UUID` |

## Optional Wrapper Types

Wrapper types work with optional fields:

```json
{
  "name": "email",
  "type": ["null", "string"],
  "default": null,
  "x-typr-wrapper": "Email"
}
```

Generates:
- Java: `Optional<Email>`
- Kotlin: `Email?`
- Scala: `Option[Email]`

## Serialization

Wrappers serialize to their underlying type in Avro/JSON:

```json
{
  "orderId": "ORD-123",
  "customerId": 456
}
```

The wrapper is purely a compile-time construct for type safety.
