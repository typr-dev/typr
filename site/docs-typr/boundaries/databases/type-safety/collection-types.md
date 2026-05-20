---
title: Collection Types
---

import ShowcaseSnippet from '@site/src/components/ShowcaseSnippet';

# Collection Types

Some databases support collection types for storing multiple values in a single column.

## Database Support

| Database | Mechanism | Notes |
|----------|-----------|-------|
| PostgreSQL | Native arrays (`integer[]`, `text[]`) | First-class support |
| Oracle | **Nested Tables**, **VARRAYs** | Collection types |
| DuckDB | **LIST**, **MAP** | Modern collection types |
| SQL Server | Table-valued parameters | Procedure parameters only |
| MariaDB/MySQL | JSON arrays | No native arrays |
| DB2 | - | No native collections |
| SQLite | - | No native collections |

## PostgreSQL Arrays

PostgreSQL has native array support for any scalar type. Arrays are mapped to Java/Kotlin/Scala arrays:

<ShowcaseSnippet
  file="employee/EmployeeRow"
  databases={['postgres']}
  fromByLang={{ java: "Optional<List<String>> skills", kotlin: "val skills:", scala: "skills: Option[List[String]]" }}
  toByLang={{ java: "Optional<Inet>", kotlin: "val ipAddress:", scala: "ipAddress: Option[Inet]" }}
/>

Supported array types include:
- `text[]` → `List<String>`
- `int4[]` → `List<Integer>`
- `boolean[]` → `List<Boolean>`
- Any other scalar type

## Oracle VARRAYs

Oracle VARRAYs are fixed-size arrays defined as named types:

```sql
CREATE TYPE skills_array AS VARRAY(10) OF VARCHAR2(100);
```

Typr generates a wrapper record:

<ShowcaseSnippet file="SkillsArray" databases={['oracle']} />

## Oracle Nested Tables

Oracle Nested Tables are unbounded collections. They can contain scalar values or OBJECT types:

```sql
-- Object type for each certification
CREATE TYPE certification_t AS OBJECT (
  name VARCHAR2(100),
  issuer VARCHAR2(100),
  year_obtained NUMBER(4)
);

-- Nested table of certification objects
CREATE TYPE certifications_table AS TABLE OF certification_t;
```

Typr generates both the element type and the collection wrapper:

<ShowcaseSnippet file="CertificationT" databases={['oracle']} />

<ShowcaseSnippet file="CertificationsTable" databases={['oracle']} />

## DuckDB LIST

DuckDB `LIST` types are similar to PostgreSQL arrays:

<ShowcaseSnippet
  file="employee/EmployeeRow"
  databases={['duckdb']}
  fromByLang={{ java: "Optional<List<String>> skills", kotlin: "val skills:", scala: "skills: Option[List[String]]" }}
  toByLang={{ java: "Optional<EmployeeContactInfo>", kotlin: "val contactInfo:", scala: "contactInfo: Option[EmployeeContactInfo]" }}
/>

Lists support arbitrary element types including nested structs.

## DuckDB MAP

DuckDB supports `MAP` types for key-value pairs:

```sql
CREATE TABLE employee (
  settings MAP(VARCHAR, VARCHAR)
);
```

Currently mapped to String for flexibility. Native Map support is planned.

## Databases Without Collection Support

MariaDB/MySQL, SQL Server (outside of table types), DB2, and SQLite don't have native array types. Alternatives:
- **JSON arrays** - Flexible but less type-safe
- **Junction tables** - For many-to-many relationships
- **Table-valued parameters** (SQL Server) - For procedure parameters
