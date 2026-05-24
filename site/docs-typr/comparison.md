---
title: How Typr Compares
sidebar_position: 4
---

# How Typr Compares

Typr isn't the first tool to generate code from database schemas. Here's an honest comparison with the alternatives.

## Quick Comparison

| Feature | Typr | jOOQ | Exposed | Hibernate |
|---------|------|------|---------|-----------|
| **Approach** | Schema-first codegen | Schema-first codegen | Code-first DSL | Code-first ORM |
| **Languages** | Java, Kotlin, Scala | Java (Kotlin via Java) | Kotlin | Java, Kotlin |
| **Databases** | 7 (Pg, Maria, Oracle, SQL Server, DuckDB, DB2, SQLite) | 30+ | 6 | Most |
| **ID types** | Generated distinct types | No | No | No |
| **SQL files** | First-class, typed | Via plain SQL | No | Via native queries |
| **API generation** | Yes (OpenAPI) | No | No | No |
| **License** | BSL 1.1 | Dual (commercial for Oracle/SQL Server) | Apache 2.0 | LGPL |

## jOOQ

[jOOQ](https://www.jooq.org/) is the most established schema-first code generator for Java. It's mature, well-documented, and has excellent database support.

**Where jOOQ excels:**
- 30+ databases supported
- Mature, battle-tested (10+ years)
- Excellent documentation
- Active development and support

**Where Typr differs:**

| Aspect | jOOQ | Typr |
|--------|------|------|
| **ID types** | All IDs are raw types (`Long`, `UUID`) | Distinct types (`UserId`, `OrderId`) that can't be mixed up |
| **SQL files** | Write DSL or embed strings | Write `.sql` files, get typed methods |
| **Multi-language** | Java-first, Kotlin via interop | Native idioms for Java, Kotlin, and Scala |
| **Nullability** | Nullable by default | Precise: `Optional`/`Option`/`?` only where schema allows null |
| **API layer** | Database only | Database + OpenAPI in one type system |
| **Oracle/SQL Server** | Commercial license required | Supported |

**Choose jOOQ if:** You need maximum database coverage, prefer a mature ecosystem, or your team is already using it.

**Choose Typr if:** You want distinct ID types, native multi-language support, or unified database + API types.

## Exposed (Kotlin)

[Exposed](https://github.com/JetBrains/Exposed) is JetBrains' SQL library for Kotlin. It's code-first: you define your schema in Kotlin, then use it.

**Where Exposed excels:**
- Pure Kotlin, idiomatic API
- JetBrains backing
- Good for greenfield Kotlin projects
- DSL and DAO patterns

**Where Typr differs:**

| Aspect | Exposed | Typr |
|--------|---------|------|
| **Direction** | Code defines schema | Schema defines code |
| **Existing schemas** | Must recreate in code | Point at existing DB, generate |
| **Type generation** | Manual | Automatic |
| **ID types** | Manual wrapper classes | Generated automatically |
| **SQL files** | Not supported | First-class |

**Choose Exposed if:** You're starting fresh with Kotlin and want to define schema in code.

**Choose Typr if:** You have an existing schema, want generated types, or need Java/Scala support.

## Hibernate / JPA

[Hibernate](https://hibernate.org/) is the incumbent ORM. Most Java developers have used it.

**Where Hibernate excels:**
- Ubiquitous, everyone knows it
- Massive ecosystem
- Handles complex object graphs
- Caching, lazy loading, change tracking

**Where Typr differs:**

| Aspect | Hibernate | Typr |
|--------|-----------|------|
| **Philosophy** | Objects are truth, DB is persistence | DB schema is truth, code reflects it |
| **Mapping** | Annotations on entities | Generated from schema |
| **ID safety** | All `Long` or `UUID` | Distinct types per table |
| **SQL control** | Hidden (mostly) | Explicit |
| **Schema changes** | Runtime errors | Compile errors |
| **N+1 queries** | Easy to create accidentally | Explicit fetching |

**Choose Hibernate if:** You need an ORM with caching, lazy loading, and your team knows it.

**Choose Typr if:** You want schema-first, explicit SQL, and compile-time safety over runtime magic.

## sqlc (Go)

[sqlc](https://sqlc.dev/) is a SQL-first code generator for Go. It's philosophically similar to Typr.

**Where sqlc excels:**
- SQL files as first-class citizens
- Fast, simple
- Growing ecosystem

**Where Typr differs:**

| Aspect | sqlc | Typr |
|--------|------|------|
| **Language** | Go | Java, Kotlin, Scala |
| **Databases** | PostgreSQL, MySQL, SQLite | 7 including Oracle, SQL Server, DB2, SQLite |
| **Repository generation** | No | Yes (CRUD for every table) |
| **ID types** | No | Yes |
| **DSL** | No | Yes |
| **API generation** | No | Yes (OpenAPI) |

**Choose sqlc if:** You're in the Go ecosystem.

**Choose Typr if:** You're on the JVM.

## pgtyped (TypeScript)

[pgtyped](https://pgtyped.dev/) generates TypeScript types from SQL files for PostgreSQL.

**Where pgtyped excels:**
- Great TypeScript integration
- SQL file approach
- Active development

**Where Typr differs:**

| Aspect | pgtyped | Typr |
|--------|---------|------|
| **Language** | TypeScript | Java, Kotlin, Scala |
| **Databases** | PostgreSQL only | 7 databases |
| **Repository generation** | No | Yes |
| **ID types** | No | Yes |

**Choose pgtyped if:** You're in TypeScript + PostgreSQL.

**Choose Typr if:** You're on the JVM or need multiple databases.

## The Typr Difference

Across all comparisons, Typr's unique combination is:

1. **Distinct ID types** - `UserId` and `OrderId` are different types. No other JVM tool does this automatically.

2. **SQL files with typed output** - Write `.sql`, get methods with correct parameter and return types.

3. **Database + API in one type system** - The same `UserId` in your database code and your OpenAPI handlers.

4. **Multi-language, native idioms** - Not "Java with Kotlin interop" but actual idiomatic code for each language.

5. **Enterprise databases supported out of the box** - Oracle, SQL Server, DB2 alongside the open-source ones.

## When NOT to Use Typr

Be honest with yourself:

- **Greenfield with simple schema?** Exposed or even raw JDBC might be simpler.
- **Need 30+ databases?** jOOQ has broader coverage.
- **Heavy ORM features (caching, lazy loading)?** Hibernate is purpose-built for that.
- **Team already productive with existing tool?** Migration cost may not be worth it.
- **Not on the JVM?** Look at sqlc (Go), pgtyped (TypeScript), or Prisma.

Typr shines when you have:
- An existing schema you can't change
- Multiple team members with varying experience
- A need for compile-time safety at database and API boundaries
- Oracle, SQL Server, or DB2 at a fraction of jOOQ's enterprise pricing
