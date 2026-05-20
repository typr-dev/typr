---
title: Struct Types
---

import ShowcaseSnippet from '@site/src/components/ShowcaseSnippet';

# Struct Types

A **struct type** (or composite type) combines multiple fields into a single structured value. These represent things like addresses, coordinates, or any multi-field value object.

## Database Support

| Database | Mechanism | Notes |
|----------|-----------|-------|
| PostgreSQL | **Composite Types** | User-defined record types |
| Oracle | **OBJECT Types** | Full object-relational types with methods |
| DuckDB | **STRUCT** | Inline composite types |
| SQL Server | **Table Types** | For table-valued parameters only |
| MariaDB/MySQL | None | No composite type support |
| DB2 | None | No composite type support |
| SQLite | None | No composite type support |

## PostgreSQL Composite Types

PostgreSQL composite types are defined at the schema level:

```sql
CREATE TYPE contact_info AS (
  phone text,
  mobile text,
  emergency_contact text,
  emergency_phone text
);
```

Typr generates a record type with full read/write support:

<ShowcaseSnippet file="ContactInfo" databases={['postgres']} />

## Oracle OBJECT Types

Oracle OBJECT types represent a single structured value with named fields - similar to a record or struct:

```sql
CREATE TYPE contact_info_t AS OBJECT (
  phone VARCHAR2(20),
  mobile VARCHAR2(20),
  emergency_contact VARCHAR2(100),
  emergency_phone VARCHAR2(20)
);
```

<ShowcaseSnippet file="ContactInfoT" databases={['oracle']} />

:::tip
For Oracle collection types (VARRAYs and Nested Tables), see [Collection Types](./collection-types.md).
:::

## DuckDB STRUCT

DuckDB supports inline composite types defined in column definitions:

```sql
CREATE TABLE employee (
  contact_info STRUCT(
    phone VARCHAR,
    mobile VARCHAR,
    emergency_contact VARCHAR,
    emergency_phone VARCHAR
  )
);
```

Typr generates a record type for each distinct struct:

<ShowcaseSnippet file="EmployeeContactInfo" databases={['duckdb']} />

## Databases Without Struct Support

MariaDB/MySQL, SQL Server (outside of table types), DB2, and SQLite don't support composite types. Alternatives:
- Use separate columns
- Store as JSON
- Use multiple related tables
