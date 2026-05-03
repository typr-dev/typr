---
title: Introduction to Typr DB
---

Your database schema is a contract. Typr DB enforces it.

Typr DB generates type-safe code from your database schema—row types, ID wrappers, repositories, and a SQL DSL—for Java, Kotlin, and Scala. Six databases supported. One type system.

## The Problem: The Database Boundary

The compiler checks your application code. But at the database boundary, you're on your own:

- Column names matched by strings that can silently break
- ID types mixed up (`UserId` vs `OrderId` vs `Long`)
- Nullable columns accessed without null checks
- Schema changes that break code at runtime, not compile time
- New team members who don't know which types go where

**The result**: Bugs that compile, ship, and break in production.

## The Solution: Schema as Contract

Typr DB treats your database schema as the source of truth:

1. **Read your schema** from PostgreSQL, MariaDB, Oracle, SQL Server, DuckDB, or DB2
2. **Generate typed code** for every table, view, and relationship
3. **Enforce at compile time** that all database access uses the correct types

When you change a column, rename a table, or modify a relationship—the compiler shows every impact. No grep. No prayer. Just fix the compile errors.

## Who Benefits

**New team members** are productive on day one. The types guide them to correct usage. The compiler catches their mistakes before code review.

**Contractors** work within well-defined boundaries. They can't accidentally use the wrong ID type or forget a nullable check. The contract is explicit.

**Seniors** review schema changes—10 lines of migration—not 1000 lines of implementation. High-leverage review where it matters.

## What Gets Generated

| Component | Description |
|-----------|-------------|
| **Row types** | Immutable data classes matching your table structure |
| **ID types** | `UserId`, `OrderId`—distinct types that can't be mixed up |
| **Repositories** | Type-safe CRUD operations for every table |
| **SQL DSL** | Compose queries with full type checking |
| **SQL file bindings** | Write `.sql` files, get typed methods |
| **Test infrastructure** | Mock repositories, test data generators |

## Supported Databases

| Database | Status |
|----------|--------|
| PostgreSQL | Full support |
| MariaDB / MySQL | Full support |
| Oracle | Full support |
| SQL Server | Full support |
| DuckDB | Full support |
| IBM DB2 | Full support |

## Supported Languages

| Language | Features |
|----------|----------|
| Java 17+ | Records, sealed interfaces, modern idioms |
| Kotlin | Value classes, null safety, data classes |
| Scala 2.13 / 3.x | Case classes, Option types, functional style |

## Example: What You Get

From a simple table, Typr generates everything you need:

import ShowcaseSnippet from '@site/src/components/ShowcaseSnippet';

<ShowcaseSnippet file="company/CompanyId" />

<ShowcaseSnippet
  file="company/CompanyRow"
  toByLang={{ java: "public CompanyRow with", kotlin: "override fun", scala: "def toUnsavedRow" }}
/>

<ShowcaseSnippet file="company/CompanyRepo" />

### Video Demo

Write your SQL in `.sql` files. Typr regenerates correct mapping code on save.

<video
width="100%"
controls
src="https://github.com/oyvindberg/typr/assets/247937/df7c4f2d-b118-4081-81c6-dd03dfe62ee2"
/>

## Types of Database Interactions

1. **CRUD Operations**: [Repository methods](what-is/relations.md) for simple and safe CRUD, plus the [SQL DSL](what-is/dsl.md) for batch operations.
2. **Simple Reads**: Joins and filters using the [SQL DSL](what-is/dsl.md).
3. **Complex Reads**: Aggregations, window functions, CTEs—handled by [writing SQL files](what-is/sql-is-king.md).
4. **Dynamic Queries**: For truly dynamic queries, Typr integrates with your [existing database library](other-features/flexible.md).

Ready to make your database boundary type-safe? Keep reading to discover what Typr DB can do.
