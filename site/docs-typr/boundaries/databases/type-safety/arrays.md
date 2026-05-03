---
title: Arrays and Lists
---

import ShowcaseSnippet from '@site/src/components/ShowcaseSnippet';

# Arrays and Lists

Typr provides full support for array and list types across databases that support them.

## Database Support

| Database | Array Syntax | Element Types |
|----------|--------------|---------------|
| PostgreSQL | `text[]`, `int4[]` | All scalar types |
| DuckDB | `LIST(varchar)`, `LIST(integer)` | All scalar types |
| MariaDB | - | No array support |
| Oracle | VARRAY, Nested Table | Via collection types |
| SQL Server | - | No array support |
| DB2 | - | No array support |

## PostgreSQL Arrays

PostgreSQL arrays are mapped to Java/Kotlin/Scala arrays:

<ShowcaseSnippet
  file="employee/EmployeeRow"
  databases={['postgres']}
  fromByLang={{ java: "Optional<List<String>> skills", kotlin: "val skills:", scala: "skills: Option[List[String]]" }}
  toByLang={{ java: "Optional<Jsonb>", kotlin: "val metadata:", scala: "metadata: Option[Jsonb]" }}
/>

Arrays work seamlessly:
- `text[]` → `List<String>`
- `int4[]` → `List<Integer>` / `List<Int>`
- `boolean[]` → `List<Boolean>`

## DuckDB Lists

DuckDB uses `LIST` types which map similarly to Java arrays:

<ShowcaseSnippet
  file="employee/EmployeeRow"
  databases={['duckdb']}
  fromByLang={{ java: "Optional<List<String>> skills", kotlin: "val skills:", scala: "skills: Option[List[String]]" }}
  toByLang={{ java: "Optional<EmployeeContactInfo> contactInfo", kotlin: "val contactInfo:", scala: "contactInfo: Option[EmployeeContactInfo]" }}
/>

DuckDB also supports nested types like:
- `STRUCT` types → Generated as record/data class
- `MAP` types → See [Map Types](/typr/boundaries/databases/type-safety/maps)

## Limitations

**Multidimensional arrays**: PostgreSQL supports multidimensional arrays, but Typr currently generates single-dimension arrays. Nested arrays may be added in the future.

**NULL elements**: Arrays can contain NULL elements. Use `Optional` inside the array or check for nulls when processing.
