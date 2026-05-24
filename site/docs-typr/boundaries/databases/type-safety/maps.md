---
title: Map Types
---

# Map Types

Map types store key-value pairs within a single column. Support varies by database.

## Database Support

| Database | Mechanism | Status |
|----------|-----------|--------|
| PostgreSQL | `hstore` extension | Mapped to `Map<String, String>` |
| DuckDB | `MAP(key, value)` | Full support with typed `Map<K, V>` |
| MariaDB | - | No map support |
| Oracle | - | No map support |
| SQL Server | - | No map support |
| DB2 | - | No map support |
| SQLite | - | No map support |

## PostgreSQL hstore

PostgreSQL's `hstore` extension provides key-value storage with string keys and values:

```sql
CREATE EXTENSION IF NOT EXISTS hstore;

CREATE TABLE product (
  id SERIAL PRIMARY KEY,
  attributes hstore
);

INSERT INTO product (attributes)
VALUES ('color => red, size => large');
```

Typr maps `hstore` to `Map<String, String>`:

```java
// Java
Map<String, String> attributes = product.attributes();
```

```kotlin
// Kotlin
val attributes: Map<String, String>? = product.attributes
```

```scala
// Scala
val attributes: Option[Map[String, String]] = product.attributes
```

## DuckDB MAP

DuckDB supports typed `MAP` columns with arbitrary key and value types:

```sql
CREATE TABLE sensor_readings (
  sensor_id UUID PRIMARY KEY,
  hourly_temps MAP(INTEGER, DOUBLE),     -- hour -> temperature
  daily_counts MAP(DATE, INTEGER)         -- date -> count
);
```

Typr generates typed `Map<K, V>` with full read/write support:

```java
// Java - typed map access
Map<Integer, Double> temps = reading.hourlyTemps();
Double noonTemp = temps.get(12);

Map<LocalDate, Integer> counts = reading.dailyCounts();
```

```kotlin
// Kotlin
val temps: Map<Int, Double> = reading.hourlyTemps
val noonTemp = temps[12]
```

```scala
// Scala
val temps: Map[Int, Double] = reading.hourlyTemps
val noonTemp = temps.get(12)
```

DuckDB MAPs support any combination of key and value types that DuckDB supports.

## Databases Without Map Support

For databases without native map types, alternatives include:
- **JSON columns** - Store maps as JSON objects
- **Separate tables** - Use a key-value table with foreign keys
- **Delimited strings** - Simple but limited (no type safety)
