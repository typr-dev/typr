---
title: PostgreSQL Types
---

# PostgreSQL Type Support

Foundations JDBC provides comprehensive support for all PostgreSQL data types, including the many exotic types that make PostgreSQL unique.

## Numeric Types

| PostgreSQL Type | Java Type | Notes |
|-----------------|-----------|-------|
| `int2` / `smallint` | `Short` | 16-bit signed integer |
| `int4` / `integer` | `Integer` | 32-bit signed integer |
| `int8` / `bigint` | `Long` | 64-bit signed integer |
| `float4` / `real` | `Float` | 32-bit IEEE 754 |
| `float8` / `double precision` | `Double` | 64-bit IEEE 754 |
| `numeric` / `decimal` | `BigDecimal` | Arbitrary precision |
| `money` | `Money` | Currency with 2 decimal places |

```java
PgType<Integer> intType = PgTypes.int4;
PgType<BigDecimal> decimalType = PgTypes.numeric;
PgType<Money> moneyType = PgTypes.money;
```

## Boolean Type

| PostgreSQL Type | Java Type |
|-----------------|-----------|
| `bool` / `boolean` | `Boolean` |

```java
PgType<Boolean> boolType = PgTypes.bool;
```

## String Types

| PostgreSQL Type | Java Type | Notes |
|-----------------|-----------|-------|
| `text` | `String` | Variable unlimited length |
| `varchar(n)` | `String` | Variable length with limit |
| `bpchar` / `char(n)` | `String` | Fixed-length, blank-padded |
| `name` | `String` | 63-character identifier |

```java
PgType<String> textType = PgTypes.text;
PgType<String> charType = PgTypes.bpchar(10);  // char(10)
```

## Binary Types

| PostgreSQL Type | Java Type | Notes |
|-----------------|-----------|-------|
| `bytea` | `byte[]` | Variable-length binary |

```java
PgType<byte[]> bytesType = PgTypes.bytea;
```

## Date/Time Types

| PostgreSQL Type | Java Type | Notes |
|-----------------|-----------|-------|
| `date` | `LocalDate` | Date without time |
| `time` | `LocalTime` | Time without timezone |
| `timetz` | `OffsetTime` | Time with timezone |
| `timestamp` | `LocalDateTime` | Date and time without timezone |
| `timestamptz` | `Instant` | Date and time with timezone (stored as UTC) |
| `interval` | `PGInterval` | Time duration |

```java
PgType<LocalDate> dateType = PgTypes.date;
PgType<Instant> timestamptzType = PgTypes.timestamptz;
PgType<PGInterval> intervalType = PgTypes.interval;
```

## UUID Type

| PostgreSQL Type | Java Type |
|-----------------|-----------|
| `uuid` | `java.util.UUID` |

```java
PgType<UUID> uuidType = PgTypes.uuid;
```

## JSON Types

| PostgreSQL Type | Java Type | Notes |
|-----------------|-----------|-------|
| `json` | `Json` | Stored as-is, validated on input |
| `jsonb` | `Jsonb` | Binary format, indexed, normalized |

```java
PgType<Json> jsonType = PgTypes.json;
PgType<Jsonb> jsonbType = PgTypes.jsonb;

// Parse and use JSON
Json data = new Json("{\"name\": \"John\"}");
```

## Array Types

Any PostgreSQL type can be used as an array. Foundations JDBC supports both boxed and unboxed array variants:

| PostgreSQL Type | Java Type (Boxed) | Java Type (Unboxed) |
|-----------------|-------------------|---------------------|
| `int4[]` | `Integer[]` | `int[]` |
| `int8[]` | `Long[]` | `long[]` |
| `float4[]` | `Float[]` | `float[]` |
| `float8[]` | `Double[]` | `double[]` |
| `bool[]` | `Boolean[]` | `boolean[]` |
| `text[]` | `String[]` | - |
| `uuid[]` | `UUID[]` | - |

```java
// Boxed arrays
PgType<Integer[]> intArrayBoxed = PgTypes.int4Array;

// Unboxed arrays (more efficient)
PgType<int[]> intArrayUnboxed = PgTypes.int4ArrayUnboxed;

// Text arrays
PgType<String[]> textArray = PgTypes.textArray;

// Any type can be made into an array
PgType<UUID[]> uuidArray = PgTypes.uuidArray;
```

## Range Types

PostgreSQL's range types represent intervals of values with inclusive/exclusive bounds:

| PostgreSQL Type | Java Type | Element Type |
|-----------------|-----------|--------------|
| `int4range` | `Range<Integer>` | Integer |
| `int8range` | `Range<Long>` | Long |
| `numrange` | `Range<BigDecimal>` | BigDecimal |
| `daterange` | `Range<LocalDate>` | LocalDate |
| `tsrange` | `Range<LocalDateTime>` | LocalDateTime |
| `tstzrange` | `Range<Instant>` | Instant |

```java
PgType<Range<Integer>> intRangeType = PgTypes.int4range;
PgType<Range<LocalDate>> dateRangeType = PgTypes.daterange;

// Create ranges
Range<Integer> range = Range.INT4.closed(1, 10);      // [1, 10]
Range<Integer> halfOpen = Range.INT4.closedOpen(1, 10); // [1, 10)

// Check containment
boolean contains = range.contains(5);  // true
```

## Geometric Types

PostgreSQL's geometric types for 2D shapes:

| PostgreSQL Type | Java Type | Description |
|-----------------|-----------|-------------|
| `point` | `PGpoint` | (x, y) coordinate |
| `line` | `PGline` | Infinite line |
| `lseg` | `PGlseg` | Line segment |
| `box` | `PGbox` | Rectangular box |
| `path` | `PGpath` | Open or closed path |
| `polygon` | `PGpolygon` | Closed polygon |
| `circle` | `PGcircle` | Circle with center and radius |

```java
PgType<PGpoint> pointType = PgTypes.point;
PgType<PGcircle> circleType = PgTypes.circle;
PgType<PGpolygon> polygonType = PgTypes.polygon;

// Create geometric objects
PGpoint point = new PGpoint(1.0, 2.0);
PGcircle circle = new PGcircle(point, 5.0);
```

## Network Types

Types for storing network addresses:

| PostgreSQL Type | Java Type | Description |
|-----------------|-----------|-------------|
| `inet` | `Inet` | IPv4 or IPv6 host address |
| `cidr` | `Cidr` | IPv4 or IPv6 network |
| `macaddr` | `Macaddr` | MAC address (6 bytes) |
| `macaddr8` | `Macaddr8` | MAC address (8 bytes, EUI-64) |

```java
PgType<Inet> inetType = PgTypes.inet;
PgType<Cidr> cidrType = PgTypes.cidr;

Inet addr = new Inet("192.168.1.1/24");
```

## Text Search Types

Full-text search types:

| PostgreSQL Type | Java Type | Description |
|-----------------|-----------|-------------|
| `tsvector` | `Tsvector` | Text search document |
| `tsquery` | `Tsquery` | Text search query |

```java
PgType<Tsvector> vectorType = PgTypes.tsvector;
PgType<Tsquery> queryType = PgTypes.tsquery;
```

## XML Type

| PostgreSQL Type | Java Type |
|-----------------|-----------|
| `xml` | `Xml` |

```java
PgType<Xml> xmlType = PgTypes.xml;
Xml doc = new Xml("<root><child>text</child></root>");
```

## Other Special Types

| PostgreSQL Type | Java Type | Description |
|-----------------|-----------|-------------|
| `hstore` | `Map<String, String>` | Key-value store |
| `vector` | `Vector` | pgvector extension |
| `record` | `Record` | Anonymous composite type |

```java
PgType<Map<String, String>> hstoreType = PgTypes.hstore;
PgType<Vector> vectorType = PgTypes.vector;
```

## System Types

Types used internally by PostgreSQL:

| PostgreSQL Type | Java Type | Description |
|-----------------|-----------|-------------|
| `oid` | `Long` | Object identifier |
| `xid` | `Xid` | Transaction ID |
| `regclass` | `Regclass` | Relation name/OID |
| `regtype` | `Regtype` | Type name/OID |
| `regproc` | `Regproc` | Function name/OID |

## Enum Types

PostgreSQL enums are mapped to Java enums:

```java
// Define your Java enum
public enum Status { PENDING, ACTIVE, COMPLETED }

// Create a PgType for it
PgType<Status> statusType = PgTypes.ofEnum("status", Status::valueOf);
```

## Custom Domain Types

Wrap base types with custom Java types using `bimap`:

```java
// Wrapper type
public record Email(String value) {}

// Create PgType from text
PgType<Email> emailType = PgTypes.text.bimap(Email::new, Email::value);
```

## Nullable Types

Any type can be made nullable using `.nullable()`:

```java
PgType<Integer> notNull = PgTypes.int4;
PgType<Integer> nullable = PgTypes.int4.nullable();  // null values allowed
```
