---
title: Schema Types
---

# Schema Types

Typr Events generates type-safe code from Avro schema definitions.

## Records

Every Avro record generates an immutable data class:

### Java

```java
public record OrderPlaced(
    UUID orderId,
    Long customerId,
    BigDecimal totalAmount,
    Instant placedAt,
    List<String> items,
    Optional<String> shippingAddress
) implements OrderEvents {

    // Wither methods for immutable updates
    public OrderPlaced withOrderId(UUID orderId) {
        return new OrderPlaced(orderId, this.customerId, this.totalAmount,
            this.placedAt, this.items, this.shippingAddress);
    }

    // Serialization
    public GenericRecord toGenericRecord() { ... }
    public static OrderPlaced fromGenericRecord(GenericRecord record) { ... }

    // Embedded schema
    public static final Schema SCHEMA = new Schema.Parser().parse("...");
}
```

### Kotlin

```kotlin
data class OrderPlaced(
    val orderId: UUID,
    val customerId: Long,
    val totalAmount: BigDecimal,
    val placedAt: Instant,
    val items: List<String>,
    val shippingAddress: String?    // Nullable instead of Optional
) : OrderEvents {

    fun toGenericRecord(): GenericRecord { ... }

    companion object {
        val SCHEMA: Schema = ...
        fun fromGenericRecord(record: GenericRecord): OrderPlaced { ... }
    }
}
```

### Scala

```scala
case class OrderPlaced(
    orderId: UUID,
    customerId: Long,
    totalAmount: BigDecimal,
    placedAt: Instant,
    items: List[String],
    shippingAddress: Option[String]
) extends OrderEvents {
    def toGenericRecord: GenericRecord = { ... }
}

object OrderPlaced {
    val SCHEMA: Schema = ...
    def fromGenericRecord(record: GenericRecord): OrderPlaced = { ... }
}
```

## Enums

Avro enums generate type-safe enumerations:

**Schema:**
```json
{
  "type": "enum",
  "name": "OrderStatus",
  "symbols": ["PENDING", "CONFIRMED", "SHIPPED", "DELIVERED", "CANCELLED"]
}
```

**Generated (Java):**
```java
public enum OrderStatus {
    PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED
}
```

## Arrays and Maps

| Avro | Java | Kotlin | Scala |
|------|------|--------|-------|
| `array<T>` | `List<T>` | `List<T>` | `List[T]` |
| `map<T>` | `Map<String, T>` | `Map<String, T>` | `Map[String, T]` |

## Logical Types

Avro logical types map to appropriate JVM types:

| Avro Logical Type | Java/Kotlin/Scala |
|-------------------|-------------------|
| `uuid` | `UUID` |
| `date` | `LocalDate` |
| `time-millis` | `LocalTime` |
| `time-micros` | `LocalTime` |
| `timestamp-millis` | `Instant` |
| `timestamp-micros` | `Instant` |
| `local-timestamp-millis` | `LocalDateTime` |
| `local-timestamp-micros` | `LocalDateTime` |
| `decimal(p, s)` | `BigDecimal` (or `DecimalP_S` with precise types) |

## Optional Fields

Avro unions with null generate optional types:

**Schema:**
```json
{"name": "shippingAddress", "type": ["null", "string"], "default": null}
```

**Generated:**
| Language | Type |
|----------|------|
| Java | `Optional<String>` |
| Kotlin | `String?` |
| Scala | `Option[String]` |
