---
title: SQL Server Types
---

# SQL Server Type Support

Foundations JDBC provides comprehensive support for SQL Server data types, including geography, geometry, hierarchyid, and Unicode types.

## Key Differences

- **TINYINT is UNSIGNED** in SQL Server (0-255), unlike most other databases
- **Separate Unicode types** (NCHAR, NVARCHAR, NTEXT) vs non-Unicode
- **DATETIMEOFFSET** for timezone-aware timestamps
- **UNIQUEIDENTIFIER** for UUIDs/GUIDs
- **No native array support** - use table-valued parameters instead

## Integer Types

| SQL Server Type | Java Type | Range | Notes |
|-----------------|-----------|-------|-------|
| `TINYINT` | `Short` | 0-255 | **Unsigned!** |
| `SMALLINT` | `Short` | -32,768 to 32,767 | |
| `INT` | `Integer` | -2^31 to 2^31-1 | |
| `BIGINT` | `Long` | -2^63 to 2^63-1 | |

```java
SqlServerType<Short> tinyType = SqlServerTypes.tinyint;   // Note: unsigned!
SqlServerType<Integer> intType = SqlServerTypes.int_;
SqlServerType<Long> bigType = SqlServerTypes.bigint;
```

## Fixed-Point Types

| SQL Server Type | Java Type | Notes |
|-----------------|-----------|-------|
| `DECIMAL(p,s)` | `BigDecimal` | Exact numeric |
| `NUMERIC(p,s)` | `BigDecimal` | Alias for DECIMAL |
| `MONEY` | `BigDecimal` | Currency (4 decimal places) |
| `SMALLMONEY` | `BigDecimal` | Smaller currency range |

```java
SqlServerType<BigDecimal> decimalType = SqlServerTypes.decimal;
SqlServerType<BigDecimal> precise = SqlServerTypes.decimal(18, 4);
SqlServerType<BigDecimal> moneyType = SqlServerTypes.money;
```

## Floating-Point Types

| SQL Server Type | Java Type | Notes |
|-----------------|-----------|-------|
| `REAL` | `Float` | 32-bit IEEE 754 |
| `FLOAT` | `Double` | 64-bit IEEE 754 |

```java
SqlServerType<Float> realType = SqlServerTypes.real;
SqlServerType<Double> floatType = SqlServerTypes.float_;
```

## Boolean Type

| SQL Server Type | Java Type |
|-----------------|-----------|
| `BIT` | `Boolean` |

```java
SqlServerType<Boolean> bitType = SqlServerTypes.bit;
```

## String Types (Non-Unicode)

| SQL Server Type | Java Type | Max Length | Notes |
|-----------------|-----------|------------|-------|
| `CHAR(n)` | `String` | 8,000 chars | Fixed-length |
| `VARCHAR(n)` | `String` | 8,000 chars | Variable-length |
| `VARCHAR(MAX)` | `String` | 2 GB | Large variable-length |
| `TEXT` | `String` | 2 GB | Deprecated, use VARCHAR(MAX) |

```java
SqlServerType<String> charType = SqlServerTypes.char_(10);
SqlServerType<String> varcharType = SqlServerTypes.varchar(255);
SqlServerType<String> varcharMax = SqlServerTypes.varcharMax;
```

## String Types (Unicode)

| SQL Server Type | Java Type | Max Length | Notes |
|-----------------|-----------|------------|-------|
| `NCHAR(n)` | `String` | 4,000 chars | Fixed-length Unicode |
| `NVARCHAR(n)` | `String` | 4,000 chars | Variable-length Unicode |
| `NVARCHAR(MAX)` | `String` | 2 GB | Large Unicode |
| `NTEXT` | `String` | 2 GB | Deprecated |

```java
SqlServerType<String> ncharType = SqlServerTypes.nchar(10);
SqlServerType<String> nvarcharType = SqlServerTypes.nvarchar(255);
SqlServerType<String> nvarcharMax = SqlServerTypes.nvarcharMax;
```

## Binary Types

| SQL Server Type | Java Type | Max Length |
|-----------------|-----------|------------|
| `BINARY(n)` | `byte[]` | 8,000 bytes |
| `VARBINARY(n)` | `byte[]` | 8,000 bytes |
| `VARBINARY(MAX)` | `byte[]` | 2 GB |
| `IMAGE` | `byte[]` | 2 GB (deprecated) |

```java
SqlServerType<byte[]> binaryType = SqlServerTypes.binary(16);
SqlServerType<byte[]> varbinaryType = SqlServerTypes.varbinary(255);
SqlServerType<byte[]> varbinaryMax = SqlServerTypes.varbinaryMax;
```

## Date/Time Types

| SQL Server Type | Java Type | Precision | Notes |
|-----------------|-----------|-----------|-------|
| `DATE` | `LocalDate` | Day | Date only |
| `TIME` | `LocalTime` | 100ns | Time only |
| `DATETIME` | `LocalDateTime` | 3.33ms | Legacy |
| `SMALLDATETIME` | `LocalDateTime` | Minute | Legacy |
| `DATETIME2` | `LocalDateTime` | 100ns | Modern |
| `DATETIMEOFFSET` | `OffsetDateTime` | 100ns | With timezone |

```java
SqlServerType<LocalDate> dateType = SqlServerTypes.date;
SqlServerType<LocalTime> timeType = SqlServerTypes.time;
SqlServerType<LocalTime> time3 = SqlServerTypes.time(3);  // TIME(3)

// Legacy types
SqlServerType<LocalDateTime> datetimeType = SqlServerTypes.datetime;
SqlServerType<LocalDateTime> smalldtType = SqlServerTypes.smalldatetime;

// Modern types (recommended)
SqlServerType<LocalDateTime> datetime2Type = SqlServerTypes.datetime2;
SqlServerType<LocalDateTime> datetime2_3 = SqlServerTypes.datetime2(3);

// Timezone-aware
SqlServerType<OffsetDateTime> dtoType = SqlServerTypes.datetimeoffset;
SqlServerType<OffsetDateTime> dto3 = SqlServerTypes.datetimeoffset(3);
```

## UNIQUEIDENTIFIER (UUID/GUID)

| SQL Server Type | Java Type |
|-----------------|-----------|
| `UNIQUEIDENTIFIER` | `java.util.UUID` |

```java
SqlServerType<UUID> uuidType = SqlServerTypes.uniqueidentifier;
```

## XML Type

| SQL Server Type | Java Type |
|-----------------|-----------|
| `XML` | `String` |

```java
SqlServerType<String> xmlType = SqlServerTypes.xml;
```

## JSON Type

SQL Server 2016+ stores JSON as NVARCHAR(MAX):

| SQL Server Type | Java Type | Notes |
|-----------------|-----------|-------|
| `NVARCHAR(MAX)` | `String` | JSON stored as Unicode string |

```java
SqlServerType<String> jsonType = SqlServerTypes.json;
```

## Spatial Types

SQL Server spatial types use the JDBC driver's native classes:

| SQL Server Type | Java Type | Notes |
|-----------------|-----------|-------|
| `GEOGRAPHY` | `Geography` | Geodetic (round earth) |
| `GEOMETRY` | `Geometry` | Planar (flat earth) |

```java
import com.microsoft.sqlserver.jdbc.Geography;
import com.microsoft.sqlserver.jdbc.Geometry;

SqlServerType<Geography> geoType = SqlServerTypes.geography;
SqlServerType<Geometry> geomType = SqlServerTypes.geometry;

// Create spatial objects using WKT
Geography point = Geography.STPointFromText("POINT(-122.34900 47.65100)", 4326);
```

## HIERARCHYID

For hierarchical tree structures:

| SQL Server Type | Java Type | Notes |
|-----------------|-----------|-------|
| `HIERARCHYID` | `String` | Path notation like `/1/2/3/` |

```java
SqlServerType<String> hierarchyType = SqlServerTypes.hierarchyid;
```

## ROWVERSION / TIMESTAMP

| SQL Server Type | Java Type | Notes |
|-----------------|-----------|-------|
| `ROWVERSION` | `byte[]` | 8-byte version number |
| `TIMESTAMP` | `byte[]` | Alias for ROWVERSION |

```java
SqlServerType<byte[]> rowversionType = SqlServerTypes.rowversion;
```

## SQL_VARIANT

| SQL Server Type | Java Type | Notes |
|-----------------|-----------|-------|
| `SQL_VARIANT` | `Object` | Can store various types |

```java
SqlServerType<Object> variantType = SqlServerTypes.sqlVariant;
```

## VECTOR (SQL Server 2025)

| SQL Server Type | Java Type | Notes |
|-----------------|-----------|-------|
| `VECTOR` | `byte[]` | For embeddings/ML |

```java
SqlServerType<byte[]> vectorType = SqlServerTypes.vector;
```

## Nullable Types

Any type can be made nullable using `.nullable()`:

```java
SqlServerType<Integer> notNull = SqlServerTypes.int_;
SqlServerType<Integer> nullable = SqlServerTypes.int_.nullable();
```

## Custom Domain Types

Wrap base types with custom Java types using `bimap`:

```java
// Wrapper type
public record OrderId(Integer value) {}

// Create SqlServerType from INT
SqlServerType<OrderId> orderIdType =
    SqlServerTypes.int_.bimap(OrderId::new, OrderId::value);
```
