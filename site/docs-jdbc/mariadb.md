---
title: MariaDB/MySQL Types
---

# MariaDB/MySQL Type Support

Foundations JDBC provides comprehensive support for MariaDB and MySQL data types, including unsigned integers, spatial types, and MySQL-specific features.

## Integer Types (Signed)

| MariaDB Type | Java Type | Range |
|--------------|-----------|-------|
| `TINYINT` | `Byte` | -128 to 127 |
| `SMALLINT` | `Short` | -32,768 to 32,767 |
| `MEDIUMINT` | `Integer` | -8,388,608 to 8,388,607 |
| `INT` | `Integer` | -2,147,483,648 to 2,147,483,647 |
| `BIGINT` | `Long` | -2^63 to 2^63-1 |

```java
MariaType<Byte> tinyType = MariaTypes.tinyint;
MariaType<Integer> intType = MariaTypes.int_;
MariaType<Long> bigType = MariaTypes.bigint;
```

## Integer Types (Unsigned)

MariaDB supports unsigned integers, which are mapped to the next larger Java type:

| MariaDB Type | Java Type | Range |
|--------------|-----------|-------|
| `TINYINT UNSIGNED` | `Short` | 0 to 255 |
| `SMALLINT UNSIGNED` | `Integer` | 0 to 65,535 |
| `MEDIUMINT UNSIGNED` | `Integer` | 0 to 16,777,215 |
| `INT UNSIGNED` | `Long` | 0 to 4,294,967,295 |
| `BIGINT UNSIGNED` | `BigInteger` | 0 to 2^64-1 |

```java
MariaType<Short> unsignedTiny = MariaTypes.tinyintUnsigned;
MariaType<Long> unsignedInt = MariaTypes.intUnsigned;
MariaType<BigInteger> unsignedBig = MariaTypes.bigintUnsigned;
```

## Fixed-Point Types

| MariaDB Type | Java Type | Notes |
|--------------|-----------|-------|
| `DECIMAL(p,s)` | `BigDecimal` | Exact numeric |
| `NUMERIC(p,s)` | `BigDecimal` | Alias for DECIMAL |

```java
MariaType<BigDecimal> decimalType = MariaTypes.decimal;
MariaType<BigDecimal> preciseDecimal = MariaTypes.decimal(10, 2);  // DECIMAL(10,2)
```

## Floating-Point Types

| MariaDB Type | Java Type | Notes |
|--------------|-----------|-------|
| `FLOAT` | `Float` | 32-bit IEEE 754 |
| `DOUBLE` | `Double` | 64-bit IEEE 754 |

```java
MariaType<Float> floatType = MariaTypes.float_;
MariaType<Double> doubleType = MariaTypes.double_;
```

## Boolean Type

| MariaDB Type | Java Type | Notes |
|--------------|-----------|-------|
| `BOOLEAN` / `BOOL` | `Boolean` | Alias for TINYINT(1) |
| `BIT(1)` | `Boolean` | Single bit as boolean |

```java
MariaType<Boolean> boolType = MariaTypes.bool;
MariaType<Boolean> bitBool = MariaTypes.bit1;
```

## Bit Types

| MariaDB Type | Java Type | Notes |
|--------------|-----------|-------|
| `BIT(n)` | `byte[]` | Bit field (n > 1) |

```java
MariaType<byte[]> bitType = MariaTypes.bit;
```

## String Types

| MariaDB Type | Java Type | Max Length |
|--------------|-----------|------------|
| `CHAR(n)` | `String` | 255 chars |
| `VARCHAR(n)` | `String` | 65,535 bytes |
| `TINYTEXT` | `String` | 255 bytes |
| `TEXT` | `String` | 65,535 bytes |
| `MEDIUMTEXT` | `String` | 16 MB |
| `LONGTEXT` | `String` | 4 GB |

```java
MariaType<String> charType = MariaTypes.char_(10);   // CHAR(10)
MariaType<String> varcharType = MariaTypes.varchar(255);  // VARCHAR(255)
MariaType<String> textType = MariaTypes.text;
MariaType<String> longType = MariaTypes.longtext;
```

## Binary Types

| MariaDB Type | Java Type | Max Length |
|--------------|-----------|------------|
| `BINARY(n)` | `byte[]` | Fixed n bytes |
| `VARBINARY(n)` | `byte[]` | Up to n bytes |
| `TINYBLOB` | `byte[]` | 255 bytes |
| `BLOB` | `byte[]` | 65,535 bytes |
| `MEDIUMBLOB` | `byte[]` | 16 MB |
| `LONGBLOB` | `byte[]` | 4 GB |

```java
MariaType<byte[]> binaryType = MariaTypes.binary(16);
MariaType<byte[]> varbinaryType = MariaTypes.varbinary(255);
MariaType<byte[]> blobType = MariaTypes.blob;
```

## Date/Time Types

| MariaDB Type | Java Type | Notes |
|--------------|-----------|-------|
| `DATE` | `LocalDate` | Date only |
| `TIME` | `LocalTime` | Time only |
| `DATETIME` | `LocalDateTime` | Date and time |
| `TIMESTAMP` | `LocalDateTime` | With auto-update |
| `YEAR` | `Year` | 4-digit year |

```java
MariaType<LocalDate> dateType = MariaTypes.date;
MariaType<LocalTime> timeType = MariaTypes.time;
MariaType<LocalDateTime> datetimeType = MariaTypes.datetime;
MariaType<Year> yearType = MariaTypes.year;

// With fractional seconds precision
MariaType<LocalTime> timeFsp = MariaTypes.time(6);       // TIME(6)
MariaType<LocalDateTime> dtFsp = MariaTypes.datetime(3);  // DATETIME(3)
```

## ENUM Type

| MariaDB Type | Java Type |
|--------------|-----------|
| `ENUM('a','b','c')` | Java Enum |

```java
// Define your Java enum
public enum Status { PENDING, ACTIVE, COMPLETED }

// Create MariaType for it
MariaType<Status> statusType = MariaTypes.ofEnum("status", Status::valueOf);
```

## SET Type

| MariaDB Type | Java Type |
|--------------|-----------|
| `SET('a','b','c')` | `MariaSet` |

```java
MariaType<MariaSet> setType = MariaTypes.set;

// Create and use sets
MariaSet values = MariaSet.of("read", "write");
String csv = values.toCommaSeparated();  // "read,write"
```

## JSON Type

| MariaDB Type | Java Type |
|--------------|-----------|
| `JSON` | `Json` |

```java
MariaType<Json> jsonType = MariaTypes.json;

Json data = new Json("{\"name\": \"John\", \"age\": 30}");
```

## Network Types (MariaDB 10.10+)

| MariaDB Type | Java Type | Description |
|--------------|-----------|-------------|
| `INET4` | `Inet4` | IPv4 address |
| `INET6` | `Inet6` | IPv6 address |

```java
MariaType<Inet4> inet4Type = MariaTypes.inet4;
MariaType<Inet6> inet6Type = MariaTypes.inet6;

Inet4 ipv4 = Inet4.parse("192.168.1.1");
Inet6 ipv6 = Inet6.parse("::1");
```

## Spatial Types

MariaDB spatial types use the MariaDB Connector/J geometry classes:

| MariaDB Type | Java Type | Description |
|--------------|-----------|-------------|
| `GEOMETRY` | `Geometry` | Any geometry |
| `POINT` | `Point` | Single point |
| `LINESTRING` | `LineString` | Line of points |
| `POLYGON` | `Polygon` | Closed polygon |
| `MULTIPOINT` | `MultiPoint` | Multiple points |
| `MULTILINESTRING` | `MultiLineString` | Multiple lines |
| `MULTIPOLYGON` | `MultiPolygon` | Multiple polygons |
| `GEOMETRYCOLLECTION` | `GeometryCollection` | Mixed geometries |

```java
MariaType<Point> pointType = MariaTypes.point;
MariaType<Polygon> polygonType = MariaTypes.polygon;
MariaType<GeometryCollection> gcType = MariaTypes.geometrycollection;

// Create a point
Point p = new Point(1.0, 2.0);
```

## Nullable Types

Any type can be made nullable using `.nullable()`:

```java
MariaType<Integer> notNull = MariaTypes.int_;
MariaType<Integer> nullable = MariaTypes.int_.nullable();
```

## Custom Domain Types

Wrap base types with custom Java types using `bimap`:

```java
// Wrapper type
public record UserId(Long value) {}

// Create MariaType from bigint
MariaType<UserId> userIdType = MariaTypes.bigint.bimap(UserId::new, UserId::value);
```
