---
title: DuckDB Types
---

# DuckDB Type Support

Foundations JDBC provides comprehensive support for DuckDB's rich type system, including nested types (LIST, STRUCT, MAP, UNION) and extended integer types.

## Integer Types (Signed)

| DuckDB Type | Java Type | Range |
|-------------|-----------|-------|
| `TINYINT` | `Byte` | -128 to 127 |
| `SMALLINT` | `Short` | -32,768 to 32,767 |
| `INTEGER` / `INT` | `Integer` | -2^31 to 2^31-1 |
| `BIGINT` | `Long` | -2^63 to 2^63-1 |
| `HUGEINT` | `BigInteger` | -2^127 to 2^127-1 |

```java
DuckDbType<Byte> tinyType = DuckDbTypes.tinyint;
DuckDbType<Integer> intType = DuckDbTypes.integer;
DuckDbType<BigInteger> hugeType = DuckDbTypes.hugeint;
```

## Integer Types (Unsigned)

| DuckDB Type | Java Type | Range |
|-------------|-----------|-------|
| `UTINYINT` | `Short` | 0 to 255 |
| `USMALLINT` | `Integer` | 0 to 65,535 |
| `UINTEGER` | `Long` | 0 to 2^32-1 |
| `UBIGINT` | `BigInteger` | 0 to 2^64-1 |
| `UHUGEINT` | `BigInteger` | 0 to 2^128-1 |

```java
DuckDbType<Short> utinyType = DuckDbTypes.utinyint;
DuckDbType<Long> uintType = DuckDbTypes.uinteger;
DuckDbType<BigInteger> uhugeType = DuckDbTypes.uhugeint;
```

## Floating-Point Types

| DuckDB Type | Java Type | Notes |
|-------------|-----------|-------|
| `FLOAT` / `FLOAT4` / `REAL` | `Float` | 32-bit IEEE 754 |
| `DOUBLE` / `FLOAT8` | `Double` | 64-bit IEEE 754 |

```java
DuckDbType<Float> floatType = DuckDbTypes.float_;
DuckDbType<Double> doubleType = DuckDbTypes.double_;
```

## Fixed-Point Types

| DuckDB Type | Java Type | Notes |
|-------------|-----------|-------|
| `DECIMAL(p,s)` | `BigDecimal` | Arbitrary precision |
| `NUMERIC(p,s)` | `BigDecimal` | Alias for DECIMAL |

```java
DuckDbType<BigDecimal> decimalType = DuckDbTypes.decimal;
DuckDbType<BigDecimal> precise = DuckDbTypes.decimal(18, 6);  // DECIMAL(18,6)
```

## Boolean Type

| DuckDB Type | Java Type |
|-------------|-----------|
| `BOOLEAN` / `BOOL` | `Boolean` |

```java
DuckDbType<Boolean> boolType = DuckDbTypes.boolean_;
```

## String Types

| DuckDB Type | Java Type | Notes |
|-------------|-----------|-------|
| `VARCHAR` / `STRING` / `TEXT` | `String` | Variable length |
| `CHAR(n)` | `String` | Fixed length |

```java
DuckDbType<String> varcharType = DuckDbTypes.varchar;
DuckDbType<String> charType = DuckDbTypes.char_(10);
```

## Binary Types

| DuckDB Type | Java Type |
|-------------|-----------|
| `BLOB` / `BYTEA` / `BINARY` / `VARBINARY` | `byte[]` |

```java
DuckDbType<byte[]> blobType = DuckDbTypes.blob;
```

## Bit String Type

| DuckDB Type | Java Type | Notes |
|-------------|-----------|-------|
| `BIT` / `BITSTRING` | `String` | String of 0s and 1s |

```java
DuckDbType<String> bitType = DuckDbTypes.bit;
DuckDbType<String> bit8 = DuckDbTypes.bit(8);  // BIT(8)
```

## Date/Time Types

| DuckDB Type | Java Type | Notes |
|-------------|-----------|-------|
| `DATE` | `LocalDate` | Date only |
| `TIME` | `LocalTime` | Time only |
| `TIMESTAMP` / `DATETIME` | `LocalDateTime` | Date and time |
| `TIMESTAMP WITH TIME ZONE` | `OffsetDateTime` | With timezone |
| `TIME WITH TIME ZONE` | `OffsetDateTime` | Time with timezone |
| `INTERVAL` | `Duration` | Time duration |

```java
DuckDbType<LocalDate> dateType = DuckDbTypes.date;
DuckDbType<LocalDateTime> tsType = DuckDbTypes.timestamp;
DuckDbType<OffsetDateTime> tstzType = DuckDbTypes.timestamptz;
DuckDbType<Duration> intervalType = DuckDbTypes.interval;
```

### Timestamp Precision Variants

| DuckDB Type | Java Type | Precision |
|-------------|-----------|-----------|
| `TIMESTAMP_S` | `LocalDateTime` | Seconds |
| `TIMESTAMP_MS` | `LocalDateTime` | Milliseconds |
| `TIMESTAMP` | `LocalDateTime` | Microseconds (default) |
| `TIMESTAMP_NS` | `LocalDateTime` | Nanoseconds |

```java
DuckDbType<LocalDateTime> tsSeconds = DuckDbTypes.timestamp_s;
DuckDbType<LocalDateTime> tsMillis = DuckDbTypes.timestamp_ms;
DuckDbType<LocalDateTime> tsNanos = DuckDbTypes.timestamp_ns;
```

## UUID Type

| DuckDB Type | Java Type |
|-------------|-----------|
| `UUID` | `java.util.UUID` |

```java
DuckDbType<UUID> uuidType = DuckDbTypes.uuid;
```

## JSON Type

| DuckDB Type | Java Type |
|-------------|-----------|
| `JSON` | `Json` |

```java
DuckDbType<Json> jsonType = DuckDbTypes.json;

Json data = new Json("{\"name\": \"DuckDB\"}");
```

## Enum Type

```java
// Define your Java enum
public enum Status { PENDING, ACTIVE, COMPLETED }

// Create DuckDbType for it
DuckDbType<Status> statusType = DuckDbTypes.ofEnum("status", Status::valueOf);
```

## Array Types

Any type can be converted to an array type using `.array()`:

| DuckDB Type | Java Type |
|-------------|-----------|
| `INTEGER[]` | `Integer[]` |
| `VARCHAR[]` | `String[]` |
| `BOOLEAN[]` | `Boolean[]` |
| ... | ... |

```java
DuckDbType<Integer[]> intArray = DuckDbTypes.integerArray;
DuckDbType<String[]> strArray = DuckDbTypes.varcharArray;
DuckDbType<UUID[]> uuidArray = DuckDbTypes.uuidArray;

// Create array for any type
DuckDbType<MyType[]> customArray = myType.array();
```

## LIST Types

DuckDB's LIST type is similar to arrays but with different semantics:

| DuckDB Type | Java Type |
|-------------|-----------|
| `LIST<INTEGER>` | `List<Integer>` |
| `LIST<VARCHAR>` | `List<String>` |
| `LIST<DATE>` | `List<LocalDate>` |
| ... | ... |

```java
// Pre-defined list types with optimized native JNI support
DuckDbType<List<Integer>> listInt = DuckDbTypes.listInteger;
DuckDbType<List<String>> listStr = DuckDbTypes.listVarchar;
DuckDbType<List<Double>> listDouble = DuckDbTypes.listDouble;

// Types that use SQL literal conversion (slightly slower but correct)
DuckDbType<List<UUID>> listUuid = DuckDbTypes.listUuid;
DuckDbType<List<LocalDate>> listDate = DuckDbTypes.listDate;
DuckDbType<List<BigDecimal>> listDecimal = DuckDbTypes.listDecimal;
```

## MAP Types

DuckDB's MAP type for key-value pairs:

| DuckDB Type | Java Type |
|-------------|-----------|
| `MAP(VARCHAR, INTEGER)` | `Map<String, Integer>` |
| `MAP(VARCHAR, VARCHAR)` | `Map<String, String>` |

```java
// Create map types
DuckDbType<Map<String, Integer>> mapStrInt = DuckDbTypes.mapVarcharInteger();
DuckDbType<Map<String, String>> mapStrStr = DuckDbTypes.mapVarcharVarchar();

// Generic map creation
DuckDbType<Map<K, V>> mapType = keyType.mapToNative(valueType, keyClass, valueClass);
```

## STRUCT Types

DuckDB's STRUCT type for composite values:

```java
// Structs are typically handled via generated code
// The structure is defined by the table schema
```

## UNION Types

DuckDB's UNION type for tagged unions:

```java
// Unions are typically handled via generated code
// The variants are defined by the table schema
```

## Nullable Types

Any type can be made nullable using `.nullable()`:

```java
DuckDbType<Integer> notNull = DuckDbTypes.integer;
DuckDbType<Integer> nullable = DuckDbTypes.integer.nullable();
```

## Custom Domain Types

Wrap base types with custom Java types using `bimap`:

```java
// Wrapper type
public record ProductId(Long value) {}

// Create DuckDbType from bigint
DuckDbType<ProductId> productIdType = DuckDbTypes.bigint.bimap(ProductId::new, ProductId::value);
```
