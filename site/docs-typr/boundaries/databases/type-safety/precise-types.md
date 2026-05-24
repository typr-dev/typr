---
title: Precise Types
sidebar_position: 2
---

# Precise Types

**Precise Types** bring your database's size and precision constraints directly into your type system. Instead of `String` for a `VARCHAR(50)`, you get `String50` - a type that the compiler guarantees fits within 50 characters.

This is type safety taken to the next level.

## The Problem

Consider this common scenario:

```java
// Database: VARCHAR(50)
record Customer(String firstName, String lastName) {}

// Compiles fine, but will fail at runtime!
Customer customer = new Customer(
    "A".repeat(100),  // 100 chars into VARCHAR(50) - boom!
    "Smith"
);
```

Runtime failures are expensive. They happen in production, require debugging, and erode trust in your code.

## The Solution

With Precise Types enabled:

```java
// Database: VARCHAR(50)
record Customer(String50 firstName, String50 lastName) {}

// This won't even compile - the type system protects you
Customer customer = new Customer(
    String50.unsafeForce("A".repeat(100)),  // Throws IllegalArgumentException
    String50.of("Smith").orElseThrow()      // Safe: returns Optional<String50>
);
```

## Supported Precise Types

| Type | Database Source | Constraints |
|------|-----------------|-------------|
| `StringN` | `VARCHAR(n)`, `NVARCHAR(n)` | Maximum length |
| `NonEmptyStringN` | `VARCHAR(n) NOT NULL` with CHECK | Non-empty, max length |
| `PaddedStringN` | `CHAR(n)` | Exact length, space-padded |
| `NonEmptyPaddedStringN` | `CHAR(n)` with CHECK | Exact length, non-empty |
| `BinaryN` | `BINARY(n)`, `VARBINARY(n)` | Maximum byte length |
| `DecimalN` | `DECIMAL(p,s)`, `NUMERIC(p,s)` | Precision and scale |
| `LocalDateTimeN` | `DATETIME(n)` | Fractional seconds precision |
| `LocalTimeN` | `TIME(n)` | Fractional seconds precision |
| `InstantN` | `TIMESTAMP(n)` | Fractional seconds precision |
| `OffsetDateTimeN` | `TIMESTAMP(n) WITH TIME ZONE` | Fractional seconds precision |

## Generated Type API

Each precise type provides a rich API for safe value construction:

### Safe Construction

```java
// Returns Optional.empty() if validation fails
Optional<String50> maybeValid = String50.of("Hello World");

// Use in functional chains
String50 name = String50.of(userInput)
    .orElseThrow(() -> new ValidationException("Name too long"));
```

### Truncation

```java
// Automatically truncates to fit - great for user input
String50 truncated = String50.truncate("This is a very long string...");
```

### Forced (Fail-Fast)

```java
// Throws IllegalArgumentException if validation fails
// Use when you know the value is valid (e.g., from database)
String50 forced = String50.unsafeForce("Known valid string");
```

### Common Interface

All string types implement `StringN`, enabling generic code:

```java
public <T extends StringN> void logLength(T value) {
    System.out.println("Value: " + value.rawValue() +
                       " (max: " + value.maxLength() + ")");
}
```

## Decimal Precision

Decimal types enforce both precision (total digits) and scale (decimal places):

```java
// DECIMAL(10,2) - perfect for currency
Optional<Decimal10_2> price = Decimal10_2.of(new BigDecimal("99.99"));
Decimal10_2 zero = Decimal10_2.Zero;  // Convenient constant

// Integer convenience methods
Decimal10_2 fromInt = Decimal10_2.of(100);  // Safe for small values

// Automatic scaling
Decimal10_2.of(new BigDecimal("99.999"));  // Rounds to 99.99
```

## Semantic Equality

Precise types support semantic equality - two values are equal if their content matches, regardless of declared constraints:

```java
String10 short_ = String10.unsafeForce("hello");
String50 long_ = String50.unsafeForce("hello");

// Different types, but semantically equal
short_.semanticEquals(long_);  // true

// Works in collections too
Set<StringN> names = new HashSet<>();
names.add(short_);
names.contains(long_);  // true (using semanticHashCode)
```

## Enabling Precise Types

```scala
import typr.*

val options = Options(
  pkg = "myapp",
  lang = Lang.Java,
  dbLib = Some(DbLibName.Typo),
  enablePreciseTypes = Selector.All  // Enable for all tables
)
```

### Selective Enablement

```scala
// Only for specific schemas
enablePreciseTypes = Selector.schemas("production", "sales")

// Only for specific tables
enablePreciseTypes = Selector.relationNames("customers", "products")

// Custom predicate
enablePreciseTypes = Selector.predicate { relation =>
  relation.name.value.endsWith("_strict")
}
```

## Database Support

Precise Types work across all supported databases:

| Database | VARCHAR | CHAR | DECIMAL | TIME | TIMESTAMP |
|----------|---------|------|---------|------|-----------|
| PostgreSQL | `StringN` | `PaddedStringN` | `DecimalN` | `LocalTimeN` | `InstantN` / `OffsetDateTimeN` |
| MariaDB | `StringN` | `PaddedStringN` | `DecimalN` | `LocalTimeN` | `LocalDateTimeN` |
| SQL Server | `StringN` | `PaddedStringN` | `DecimalN` | `LocalTimeN` | `LocalDateTimeN` / `OffsetDateTimeN` |
| Oracle | `StringN` | `PaddedStringN` | `DecimalN` | - | `InstantN` |
| DuckDB | `StringN` | - | `DecimalN` | `LocalTimeN` | `InstantN` |
| DB2 | `StringN` | `PaddedStringN` | `DecimalN` | - | `LocalDateTimeN` |
| SQLite | `StringN` | `PaddedStringN` | `DecimalN` | - | - |

## Language Support

Precise Types generate idiomatically for each language:

### Java

```java
public record String50(String value) implements StringN {
    public static Optional<String50> of(String value) { ... }
    public static String50 truncate(String value) { ... }
    public static String50 unsafeForce(String value) { ... }
}
```

### Kotlin

```kotlin
@JvmInline
value class String50(val value: String) : StringN {
    companion object {
        fun of(value: String): String50? = ...
        fun truncate(value: String): String50 = ...
        fun unsafeForce(value: String): String50 = ...
    }
}
```

### Scala

```scala
opaque type String50 = String
object String50 extends StringN[String50]:
  def of(value: String): Option[String50] = ...
  def truncate(value: String): String50 = ...
  def unsafeForce(value: String): String50 = ...
```

## Why This Matters

Precise Types transform database constraints from runtime concerns to compile-time guarantees:

1. **Catch bugs earlier**: Size violations are caught during development, not in production
2. **Self-documenting**: `String50` tells you more than `String`
3. **IDE support**: Autocomplete shows exact constraints
4. **Refactoring safety**: Change a column's size and the compiler shows all affected code
5. **API contracts**: Public APIs communicate constraints through types

This is the difference between hoping your data is valid and knowing it is.
