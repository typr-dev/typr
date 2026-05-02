---
title: Enums
---

import ShowcaseSnippet from '@site/src/components/ShowcaseSnippet';

# Enums

Typr generates type-safe enum types from your database schema. Support varies by database.

## Database Support

| Database | Native Enums | Inline Enums | Open Enums |
|----------|--------------|--------------|------------|
| PostgreSQL | `CREATE TYPE ... AS ENUM` | - | Table-based |
| DuckDB | `CREATE TYPE ... AS ENUM` | - | Table-based |
| MariaDB/MySQL | - | `ENUM('a','b')` column type | Table-based |
| Oracle | - | - | Table-based |
| SQL Server | - | - | Table-based |

## Native Enums (PostgreSQL, DuckDB)

PostgreSQL and DuckDB support named enum types at the database level:

```sql
CREATE TYPE order_status AS ENUM (
  'pending', 'confirmed', 'processing',
  'shipped', 'delivered', 'cancelled', 'refunded'
);
```

Typr generates a type-safe enum:

<ShowcaseSnippet file="OrderStatus" databases={['postgres']} />

The generated enum includes:
- Type-safe values that the compiler validates
- `force()` method for parsing strings (throws on invalid values)
- Database type definitions for reading/writing

## Inline Enums (MariaDB/MySQL)

MariaDB defines enums inline in the column definition rather than as named types:

```sql
CREATE TABLE customer_order (
  status ENUM('pending', 'confirmed', 'processing',
              'shipped', 'delivered', 'cancelled', 'refunded')
);
```

Since inline enums don't have a schema-level name, Typr maps them to `String`. The allowed values are still enforced by the database, but there's no compile-time type checking.

## No Native Enums (Oracle, SQL Server, DB2)

Oracle, SQL Server, and DB2 do not have native enum types. Common alternatives:

- **CHECK constraints** on string columns (validated by database, mapped to String)
- **Lookup tables** with foreign key constraints (see [Open Enums](/typr/boundaries/databases/type-safety/open-enums))
- **Application-level enums** using wrapper types

For type-safe enums in these databases, consider using [Open Enums](/typr/boundaries/databases/type-safety/open-enums) with a lookup table.
