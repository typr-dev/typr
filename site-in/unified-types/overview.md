---
title: Unified Types
sidebar_position: 1
---

# Unified Types: One Type System Across Your Entire Stack

**Unified Types** is one of Typr's most powerful features. It lets you define semantic types once and automatically apply them across multiple databases and your OpenAPI specifications.

Imagine having `FirstName`, `Email`, `IsActive`, and `CustomerId` types that work seamlessly whether the data comes from PostgreSQL, MariaDB, or your REST API. No more manual synchronization. No more type mismatches. Just pure, compile-time safety across your entire system.

## The Problem

Modern applications rarely have a single data source:

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   PostgreSQL    │     │     MariaDB     │     │   REST API      │
│                 │     │                 │     │   (OpenAPI)     │
│ person.firstname│     │ customers.      │     │ Customer.       │
│ (varchar 50)    │     │   first_name    │     │   firstName     │
│                 │     │   (varchar 50)  │     │   (string)      │
└────────┬────────┘     └────────┬────────┘     └────────┬────────┘
         │                       │                       │
         ▼                       ▼                       ▼
   String?                 String?                 String?
```

Without unified types, you end up with three different `String` types representing the same concept. There's no compile-time connection between them.

## The Solution

With Unified Types:

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   PostgreSQL    │     │     MariaDB     │     │   REST API      │
│                 │     │                 │     │   (OpenAPI)     │
│ person.firstname│     │ customers.      │     │ Customer.       │
│                 │     │   first_name    │     │   firstName     │
└────────┬────────┘     └────────┬────────┘     └────────┬────────┘
         │                       │                       │
         └───────────────────────┼───────────────────────┘
                                 │
                                 ▼
                        ┌────────────────┐
                        │   FirstName    │
                        │                │
                        │ value: String  │
                        │ max: 50 chars  │
                        └────────────────┘
```

One type. Three sources. Complete type safety.

## Generated Code

When you define unified types, Typr generates a shared type with documentation showing all its sources:

```java
/**
 * Shared type `FirstName` aligned across sources:
 * - postgres (PostgreSQL): person.person.firstname
 * - mariadb (MariaDB): customers.first_name
 * - api (OpenAPI): Customer.firstName (model), CustomerCreate.firstName (model)
 */
public record FirstName(@JsonValue String value) {
    // Database type adapters
    public static MariaType<FirstName> mariaType =
        MariaTypes.varchar.bimap(FirstName::new, FirstName::value);

    public static PgType<FirstName> pgType =
        PgTypes.text.bimap(FirstName::new, FirstName::value);
}
```

The generated code includes:
- Documentation listing every source location
- Database type adapters for each matched database
- JSON serialization support
- Full interoperability across all sources

## Defining Type Mappings

Type definitions use **predicates** that match columns and fields. When a match is found, Typr uses your named type instead of the default.

### Basic Example

```scala
import typr.*

val types = TypeDefinitions(
  // Match by column/field name
  TypeEntry("FirstName",
    db = DbMatch.column("first_name", "firstname"),
    api = ApiMatch.name("firstName")
  ),

  // Match by name pattern (glob syntax)
  TypeEntry("Email",
    db = DbMatch.column("*email*"),
    api = ApiMatch.name("*email*", "*Email*")
  ),

  // Match boolean flags
  TypeEntry("IsActive",
    db = DbMatch.column("is_active"),
    api = ApiMatch.name("isActive", "active")
  )
)
```

### Advanced Matching

```scala
// Match by database and schema
TypeEntry("CustomerId",
  db = DbMatch.Empty.copy(
    database = List("production"),
    schema = List("customers"),
    column = List("customer_id", "cust_id"),
    primaryKey = Some(true)
  ),
  api = ApiMatch.name("customerId")
)

// Match by OpenAPI format
TypeEntry("UUID",
  db = DbMatch.Empty.copy(dbType = List("uuid")),
  api = ApiMatch.format("uuid")
)

// Match by API location
TypeEntry("AuthToken",
  db = DbMatch.Empty,
  api = ApiMatch.Empty.copy(
    location = List(ApiLocation.HeaderParam),
    name = List("Authorization", "X-Auth-Token")
  )
)

// Match by column comment annotation
TypeEntry("Currency",
  db = DbMatch.annotation("@currency"),
  api = ApiMatch.extension("x-currency", "true")
)
```

## Match Semantics

The matching system is designed to be both powerful and intuitive:

| Rule | Meaning |
|------|---------|
| Empty list | Matches anything (wildcard) |
| Non-empty list | Matches if **any** pattern matches (OR) |
| Multiple fields | **All** non-empty fields must match (AND) |
| Glob patterns | `*` matches any sequence, `?` matches single char |

### Examples

```scala
// Matches ANY column named 'email' in ANY database
DbMatch.column("email")

// Matches 'email' columns in the 'users' table only
DbMatch.Empty.copy(table = List("users"), column = List("email"))

// Matches 'email' OR 'user_email' columns
DbMatch.column("email", "user_email")

// Matches any column ending in '_email'
DbMatch.column("*_email")
```

## Using with Code Generation

Pass your type definitions to the code generation:

```scala
import typr.*

// Define your shared types
val sharedTypes = TypeDefinitions(
  TypeEntry("FirstName",
    db = DbMatch.column("first_name", "firstname"),
    api = ApiMatch.name("firstName")
  ),
  TypeEntry("Email",
    db = DbMatch.column("*email*"),
    api = ApiMatch.name("*email*")
  )
)

// Generate database code
typr.generateFromDb(
  dataSource = postgresDataSource,
  options = Options(
    pkg = "myapp.db.postgres",
    lang = Lang.Java,
    dbLib = Some(DbLibName.Typo),
    typeDefinitions = sharedTypes
  ),
  targetFolder = generatedPath / "postgres",
  selector = Selector.All
)

// Generate OpenAPI code
typr.openapi.generateFromSpec(
  specPath = Path.of("api/openapi.yaml"),
  options = OpenApiOptions(
    pkg = "myapp.api",
    lang = Lang.Java,
    typeDefinitions = sharedTypes
  ),
  targetFolder = generatedPath / "api"
)
```

## Real-World Example

Here's a complete example combining PostgreSQL, MariaDB, and an OpenAPI spec:

```scala
val enterpriseTypes = TypeDefinitions(
  // Identity types
  TypeEntry("CustomerId",
    db = DbMatch.column("customer_id").copy(primaryKey = Some(true)),
    api = ApiMatch.name("customerId")
  ),
  TypeEntry("EmployeeId",
    db = DbMatch.column("employee_id", "emp_id").copy(primaryKey = Some(true)),
    api = ApiMatch.name("employeeId")
  ),

  // Common string types
  TypeEntry("FirstName",
    db = DbMatch.column("first_name", "firstname", "fname"),
    api = ApiMatch.name("firstName", "fname")
  ),
  TypeEntry("LastName",
    db = DbMatch.column("last_name", "lastname", "lname"),
    api = ApiMatch.name("lastName", "lname")
  ),
  TypeEntry("Email",
    db = DbMatch.column("*email*"),
    api = ApiMatch.name("*email*", "*Email*")
  ),

  // Boolean flags
  TypeEntry("IsActive",
    db = DbMatch.column("is_active", "active"),
    api = ApiMatch.name("isActive", "active")
  ),
  TypeEntry("IsVerified",
    db = DbMatch.column("is_verified", "verified"),
    api = ApiMatch.name("isVerified", "verified")
  ),

  // Audit fields
  TypeEntry("CreatedAt",
    db = DbMatch.column("created_at", "created_date"),
    api = ApiMatch.name("createdAt")
  ),
  TypeEntry("UpdatedAt",
    db = DbMatch.column("updated_at", "modified_at", "last_modified"),
    api = ApiMatch.name("updatedAt", "modifiedAt")
  )
)
```

## Benefits

1. **Single Source of Truth**: Define your semantic types once, use everywhere
2. **Compile-Time Safety**: The compiler catches type mismatches across all systems
3. **Self-Documenting**: Generated code shows exactly which sources use each type
4. **Refactoring Confidence**: Rename a type and see all affected code instantly
5. **Team Alignment**: Clear contracts between database and API teams
6. **Migration Safety**: Add a new database and types automatically align

## Next Steps

- [YAML Configuration](./yaml-config.md) - Define types in a configuration file
- [CLI Tool](./cli.md) - Manage data sources and type matching interactively
- [Best Practices](./best-practices.md) - Patterns for organizing type definitions
