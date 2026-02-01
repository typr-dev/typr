---
title: Precise Types
---

# Precise Types

Typr Events can generate precise decimal types with compile-time constraint validation.

## The Problem

`BigDecimal` has no constraints:

```java
BigDecimal price = new BigDecimal("12345678901234567890.123456789");
// Might overflow your database column!
```

## The Solution

Enable precise types for decimals:

```scala
val options = AvroOptions.default(...).copy(
  enablePreciseTypes = true
)
```

## Schema

```json
{
  "name": "amount",
  "type": {
    "type": "bytes",
    "logicalType": "decimal",
    "precision": 10,
    "scale": 2
  }
}
```

## Generated Type

### Java

```java
public record Decimal10_2(BigDecimal decimalValue) {
    public static final int PRECISION = 10;
    public static final int SCALE = 2;

    // Safe construction - validates constraints
    public static Optional<Decimal10_2> from(BigDecimal value) {
        if (value.precision() > PRECISION || value.scale() > SCALE) {
            return Optional.empty();
        }
        return Optional.of(new Decimal10_2(value.setScale(SCALE)));
    }

    // Unsafe construction - throws on invalid
    public static Decimal10_2 unsafeForce(BigDecimal value) {
        return from(value).orElseThrow(() ->
            new IllegalArgumentException("Value exceeds precision/scale"));
    }
}
```

### Kotlin

```kotlin
@JvmInline
value class Decimal10_2(val decimalValue: BigDecimal) {
    companion object {
        const val PRECISION = 10
        const val SCALE = 2

        fun from(value: BigDecimal): Decimal10_2? {
            if (value.precision() > PRECISION || value.scale() > SCALE) {
                return null
            }
            return Decimal10_2(value.setScale(SCALE))
        }

        fun unsafeForce(value: BigDecimal): Decimal10_2 =
            from(value) ?: throw IllegalArgumentException("Value exceeds precision/scale")
    }
}
```

## Usage

```java
// Safe construction
Optional<Decimal10_2> maybeAmount = Decimal10_2.from(new BigDecimal("99.99"));
maybeAmount.ifPresent(amount -> {
    var order = new OrderPlaced(orderId, customerId, amount, ...);
});

// When you're confident the value is valid
Decimal10_2 amount = Decimal10_2.unsafeForce(new BigDecimal("99.99"));
```

## Type Names

Precise types are named after their precision and scale:

| Decimal Definition | Generated Type |
|-------------------|----------------|
| `decimal(10, 2)` | `Decimal10_2` |
| `decimal(18, 4)` | `Decimal18_4` |
| `decimal(38, 6)` | `Decimal38_6` |

## Arithmetic

The underlying `BigDecimal` is accessible for arithmetic:

```java
Decimal10_2 price = Decimal10_2.unsafeForce(new BigDecimal("99.99"));
Decimal10_2 tax = Decimal10_2.unsafeForce(new BigDecimal("8.00"));

BigDecimal total = price.decimalValue().add(tax.decimalValue());
Decimal10_2 totalTyped = Decimal10_2.unsafeForce(total);
```

## Without Precise Types

If `enablePreciseTypes = false`, decimals generate as plain `BigDecimal`:

```java
public record OrderPlaced(
    UUID orderId,
    BigDecimal amount  // No compile-time constraints
) { }
```
