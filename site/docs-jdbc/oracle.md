---
title: Oracle Types
---

# Oracle Type Support

Foundations JDBC provides comprehensive support for Oracle data types, including OBJECT types, nested tables, intervals, and LOB types.

## Numeric Types

### Universal NUMBER Type

| Oracle Type | Java Type | Notes |
|-------------|-----------|-------|
| `NUMBER` | `BigDecimal` | Arbitrary precision |
| `NUMBER(p,0)` where p≤9 | `Integer` | 32-bit integer |
| `NUMBER(p,0)` where 9<p≤18 | `Long` | 64-bit integer |
| `NUMBER(p,s)` | `BigDecimal` | Fixed precision/scale |

```java
OracleType<BigDecimal> numberType = OracleTypes.number;
OracleType<BigDecimal> decimal = OracleTypes.number(10, 2);  // NUMBER(10,2)
OracleType<Integer> intType = OracleTypes.numberAsInt(9);    // NUMBER(9)
OracleType<Long> longType = OracleTypes.numberAsLong(18);    // NUMBER(18)
```

### IEEE 754 Floating Point

| Oracle Type | Java Type | Notes |
|-------------|-----------|-------|
| `BINARY_FLOAT` | `Float` | 32-bit IEEE 754 |
| `BINARY_DOUBLE` | `Double` | 64-bit IEEE 754 |
| `FLOAT(p)` | `Double` | Maps to NUMBER internally |

```java
OracleType<Float> binaryFloat = OracleTypes.binaryFloat;
OracleType<Double> binaryDouble = OracleTypes.binaryDouble;
OracleType<Double> floatType = OracleTypes.float_(126);  // FLOAT(126)
```

## Boolean Type

| Oracle Type | Java Type | Notes |
|-------------|-----------|-------|
| `BOOLEAN` | `Boolean` | Oracle 23c+ native |
| `NUMBER(1)` | `Boolean` | Traditional 0/1 convention |

```java
OracleType<Boolean> boolNative = OracleTypes.boolean_;        // Oracle 23c+
OracleType<Boolean> boolNumber = OracleTypes.numberAsBoolean; // NUMBER(1)
```

## Character Types

| Oracle Type | Java Type | Max Length | Notes |
|-------------|-----------|------------|-------|
| `VARCHAR2(n)` | `String` | 4000 bytes | Variable-length |
| `CHAR(n)` | `String` | 2000 bytes | Fixed-length, blank-padded |
| `NVARCHAR2(n)` | `String` | 4000 bytes | National character set |
| `NCHAR(n)` | `String` | 2000 bytes | National fixed-length |
| `LONG` | `String` | 2 GB | Deprecated, use CLOB |

```java
OracleType<String> varcharType = OracleTypes.varchar2;
OracleType<String> varchar100 = OracleTypes.varchar2(100);
OracleType<String> charType = OracleTypes.char_(10);
OracleType<String> nvarcharType = OracleTypes.nvarchar2(100);
```

### Non-Empty String Variants

For NOT NULL columns, use `NonEmptyString` to guarantee non-empty values:

```java
OracleType<NonEmptyString> nonEmpty = OracleTypes.varchar2NonEmpty(100);
OracleType<NonEmptyString> nvarNonEmpty = OracleTypes.nvarchar2NonEmpty(100);
```

### Padded String for CHAR

For CHAR columns preserving padding:

```java
OracleType<PaddedString> padded = OracleTypes.charPadded(10);  // CHAR(10)
OracleType<PaddedString> npadded = OracleTypes.ncharPadded(10); // NCHAR(10)
```

## Large Object (LOB) Types

| Oracle Type | Java Type | Max Size | Notes |
|-------------|-----------|----------|-------|
| `CLOB` | `String` | 4 GB | Character LOB |
| `NCLOB` | `String` | 4 GB | National character LOB |
| `BLOB` | `byte[]` | 4 GB | Binary LOB |

```java
OracleType<String> clobType = OracleTypes.clob;
OracleType<String> nclobType = OracleTypes.nclob;
OracleType<byte[]> blobType = OracleTypes.blob;

// Non-empty variants
OracleType<NonEmptyString> clobNonEmpty = OracleTypes.clobNonEmpty;
OracleType<NonEmptyBlob> blobNonEmpty = OracleTypes.blobNonEmpty;
```

## Binary Types

| Oracle Type | Java Type | Max Length | Notes |
|-------------|-----------|------------|-------|
| `RAW(n)` | `byte[]` | 2000 bytes | Variable-length binary |
| `LONG RAW` | `byte[]` | 2 GB | Deprecated, use BLOB |

```java
OracleType<byte[]> rawType = OracleTypes.raw;
OracleType<byte[]> raw100 = OracleTypes.raw(100);  // RAW(100)

// Non-empty variant
OracleType<NonEmptyBlob> rawNonEmpty = OracleTypes.rawNonEmpty(100);
```

## Date/Time Types

| Oracle Type | Java Type | Notes |
|-------------|-----------|-------|
| `DATE` | `LocalDateTime` | Date + time (second precision) |
| `TIMESTAMP` | `LocalDateTime` | Fractional seconds (default: 6) |
| `TIMESTAMP WITH TIME ZONE` | `OffsetDateTime` | Explicit timezone |
| `TIMESTAMP WITH LOCAL TIME ZONE` | `OffsetDateTime` | Session timezone |

```java
OracleType<LocalDateTime> dateType = OracleTypes.date;
OracleType<LocalDateTime> tsType = OracleTypes.timestamp;
OracleType<LocalDateTime> ts3 = OracleTypes.timestamp(3);  // TIMESTAMP(3)
OracleType<OffsetDateTime> tstz = OracleTypes.timestampWithTimeZone;
OracleType<OffsetDateTime> tsltz = OracleTypes.timestampWithLocalTimeZone;
```

**Note:** Oracle `DATE` includes time (unlike SQL standard), so it maps to `LocalDateTime`, not `LocalDate`.

## Interval Types

| Oracle Type | Java Type | Notes |
|-------------|-----------|-------|
| `INTERVAL YEAR TO MONTH` | `OracleIntervalYM` | Years and months |
| `INTERVAL DAY TO SECOND` | `OracleIntervalDS` | Days, hours, minutes, seconds |

```java
OracleType<OracleIntervalYM> ymType = OracleTypes.intervalYearToMonth;
OracleType<OracleIntervalYM> ym4 = OracleTypes.intervalYearToMonth(4);

OracleType<OracleIntervalDS> dsType = OracleTypes.intervalDayToSecond;
OracleType<OracleIntervalDS> ds96 = OracleTypes.intervalDayToSecond(9, 6);

// Create and use intervals
OracleIntervalYM interval = OracleIntervalYM.parse("+02-05");  // 2 years, 5 months
String oracle = interval.toOracleFormat();  // "+02-05"
String iso = interval.toIso8601();          // "P2Y5M"
```

## ROWID Types

| Oracle Type | Java Type | Notes |
|-------------|-----------|-------|
| `ROWID` | `String` | Physical row address (18 chars) |
| `UROWID` | `String` | Universal ROWID (max 4000 bytes) |

```java
OracleType<String> rowidType = OracleTypes.rowId;
OracleType<String> urowidType = OracleTypes.uRowId;
OracleType<String> urowid1000 = OracleTypes.uRowId(1000);
```

## XML and JSON Types

| Oracle Type | Java Type | Notes |
|-------------|-----------|-------|
| `XMLTYPE` | `String` | XML document storage |
| `JSON` | `Json` | Native JSON (Oracle 21c+) |

```java
OracleType<String> xmlType = OracleTypes.xmlType;
OracleType<Json> jsonType = OracleTypes.json;

Json data = new Json("{\"name\": \"Oracle\"}");
```

## OBJECT Types

Oracle OBJECT types (user-defined types) are supported via generated code:

```java
// Generated code creates OracleType for your OBJECT type
// Example for ADDRESS_T type:
OracleType<AddressT> addressType = AddressT.oracleType;

// Insert using the generated type
AddressT addr = new AddressT("123 Main St", "City", "12345");
```

## Nested Tables and VARRAYs

Oracle collection types are fully supported:

```java
// Nested tables - generated as List<Element>
OracleType<List<String>> stringTable = // generated

// VARRAYs - generated as arrays
OracleType<String[]> stringVarray = // generated
```

## Nullable Types

Any type can be made nullable using `.nullable()`:

```java
OracleType<Integer> notNull = OracleTypes.numberInt;
OracleType<Integer> nullable = OracleTypes.numberInt.nullable();
```

## Custom Domain Types

Wrap base types with custom Java types using `bimap`:

```java
// Wrapper type
public record EmployeeId(Long value) {}

// Create OracleType from NUMBER
OracleType<EmployeeId> empIdType =
    OracleTypes.numberLong.bimap(EmployeeId::new, EmployeeId::value);
```
