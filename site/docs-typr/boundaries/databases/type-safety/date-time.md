---
title: Date/Time Types
---

# Date/Time Types

Typr maps database date/time types to `java.time` classes, with database-specific handling for precision and timezone semantics.

## Type Mappings

| SQL Type | Java Type | Notes |
|----------|-----------|-------|
| `DATE` | `LocalDate` | Date without time |
| `TIME` | `LocalTime` | Time without timezone |
| `TIMESTAMP` | `LocalDateTime` | Date+time without timezone |
| `TIMESTAMPTZ` | `OffsetDateTime` / `Instant` | Date+time with timezone |
| `TIMETZ` | `OffsetTime` | Time with timezone (PostgreSQL) |
| `INTERVAL` | `Duration` / `PGInterval` | Time duration |

## Database-Specific Behavior

### PostgreSQL

PostgreSQL stores `TIMESTAMPTZ` in UTC internally. Typr uses `OffsetDateTime` to preserve full precision:

- **Microsecond precision**: PostgreSQL supports microsecond precision; Typr preserves this
- **Timezone handling**: `TIMESTAMPTZ` values include timezone offsets
- **Array support**: Date/time arrays are fully supported

#### Range Types

PostgreSQL supports range types for date/time values. Typr maps these to `Range<T>`:

| SQL Type | Java Type | Description |
|----------|-----------|-------------|
| `daterange` | `Range<LocalDate>` | Range of dates |
| `tsrange` | `Range<LocalDateTime>` | Range of timestamps without timezone |
| `tstzrange` | `Range<Instant>` | Range of timestamps with timezone |

Ranges support open/closed bounds and can be empty:

```java
// Create a date range [2024-01-01, 2024-12-31)
Range<LocalDate> range = Range.date(
    RangeBound.closed(LocalDate.of(2024, 1, 1)),
    RangeBound.open(LocalDate.of(2025, 1, 1))
);

// Check containment
boolean inRange = range.contains(LocalDate.of(2024, 6, 15)); // true
```

### MariaDB/MySQL

MariaDB has some date/time limitations:

- **DATETIME** and **TIMESTAMP** differ in storage and behavior
- **TIMESTAMP** has range limitations (1970-2038)
- Timezone handling is connection-dependent

### DuckDB

DuckDB has modern date/time handling:

- `INTERVAL` types for durations
- Microsecond precision
- No timezone conversion issues

### Oracle

Oracle uses different type names:

- `DATE` includes time (unlike SQL standard)
- `TIMESTAMP WITH TIME ZONE` for timezone-aware timestamps

#### Interval Types

Oracle has two distinct interval types, each mapped to a dedicated Java record:

| SQL Type | Java Type | Description |
|----------|-----------|-------------|
| `INTERVAL YEAR TO MONTH` | `OracleIntervalYM` | Years and months (e.g., 2 years 5 months) |
| `INTERVAL DAY TO SECOND` | `OracleIntervalDS` | Days, hours, minutes, seconds, nanos |

These types can parse both Oracle's native format and ISO-8601 duration format:

```java
// Parse Oracle format: +02-05 (2 years, 5 months)
OracleIntervalYM ym = OracleIntervalYM.parse("+02-05");
Period period = ym.toPeriod();

// Parse Oracle format: +03 14:30:45.123456 (3 days, 14h, 30m, 45.123456s)
OracleIntervalDS ds = OracleIntervalDS.parse("+03 14:30:45.123456");
Duration duration = ds.toDuration();

// Also supports ISO-8601: P2Y5M, P3DT14H30M45S
OracleIntervalYM fromIso = OracleIntervalYM.parse("P2Y5M");
```

### SQL Server

SQL Server has several date/time types:

- `DATETIMEOFFSET` for timezone-aware timestamps
- `DATETIME2` for high-precision local timestamps
- No interval types (use computed columns)

## Best Practices

1. **Use `TIMESTAMPTZ` for events**: Always use timezone-aware types for user-facing timestamps
2. **Store in UTC**: Let the database handle timezone conversion
3. **Avoid `DATE` for timestamps**: It loses time information
4. **Consider precision**: Some databases truncate to milliseconds; use appropriate types for microsecond precision
