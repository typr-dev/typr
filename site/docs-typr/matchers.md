---
title: Matchers
sidebar_position: 4
---

# Matchers

Matchers control which database objects are included in code generation and which features are enabled for each object.

## Overview

Matchers use pattern matching to include or exclude:
- Database schemas
- Tables and views
- Features per table (mock repos, test inserts, etc.)

## Pattern Syntax

Patterns support glob-style matching with negation:

| Pattern | Matches |
|---------|---------|
| `users` | Exactly "users" |
| `user*` | "users", "user_profiles", "user_sessions" |
| `*_backup` | "orders_backup", "users_backup" |
| `*order*` | "orders", "order_items", "customer_orders" |
| `public.*` | All tables in "public" schema |
| `!*_temp` | Negation: excludes anything ending in "_temp" |

### Negation with `!`

Prefix any pattern with `!` to exclude matches. This is powerful when combined with wildcards:

```yaml
matchers:
  tables:
    # Include all email columns EXCEPT email_process
    - "*email*"
    - "!email_process"
    - "!*_backup"
```

Negation patterns are evaluated after positive patterns. A match against a negation pattern removes items that would otherwise be included.

```yaml
types:
  Email:
    db:
      # Match all columns containing 'email', except internal ones
      column: ["*email*", "!email_hash", "!email_verified_at"]
    model:
      name: ["*email*", "!emailInternal"]
```

## Basic Syntax

### Include All

```yaml
matchers:
  tables: all
```

### Include Specific Items

```yaml
matchers:
  tables:
    - public.users
    - public.orders
    - sales.customers
```

### Exclude Patterns

```yaml
matchers:
  tables:
    include: all
    exclude:
      - "*_backup"
      - "*_temp"
      - flyway_*
```

### Schema Matching

```yaml
matchers:
  schemas: [public, sales, inventory]

  # Or with exclude
  schemas:
    include: all
    exclude: [pg_catalog, information_schema]
```

## Feature Matchers

Each feature can be independently enabled per table.

### Mock Repositories

Generate mock implementations for testing:

```yaml
matchers:
  mock_repos:
    include: all
    exclude:
      - "*_audit"
      - "*_log"
```

**Generated code:**
```java
// Real repository (uses database)
public class UserRepoImpl implements UserRepo { ... }

// Mock repository (in-memory, for tests)
public class UserRepoMock implements UserRepo { ... }
```

### Primary Key Types

Generate type-safe ID wrappers:

```yaml
matchers:
  primary_key_types:
    include: all
    exclude:
      - migrations
      - schema_versions
```

**Generated code:**
```java
// Instead of: Long id
public record UserId(Long value) {}
public record OrderId(Long value) {}

// Type-safe - can't mix IDs
userRepo.selectById(new UserId(1));     // OK
userRepo.selectById(new OrderId(1));    // Compile error!
```

### Test Inserts

Generate test data factories:

```yaml
matchers:
  test_inserts:
    include:
      - public.users
      - public.products
      - public.orders
    exclude: []
```

**Generated code:**
```java
// Generate valid test data
UserRowUnsaved testUser = UserRowUnsaved.random();

// Or with specific overrides
UserRowUnsaved testUser = UserRowUnsaved.random()
    .withEmail(new Email("test@example.com"))
    .withIsActive(new IsActive(true));
```

### Read-Only Repositories

Generate repositories without mutation methods:

```yaml
matchers:
  readonly:
    include:
      - "*_view"
      - "audit_*"
      - public.reports
```

**Generated code:**
```java
// Only has select methods, no insert/update/delete
public interface AuditLogRepo {
    List<AuditLogRow> selectAll();
    Optional<AuditLogRow> selectById(AuditLogId id);
    // No insert, update, or delete methods
}
```

### Precise Types

Generate length-constrained string types:

```yaml
matchers:
  precise_types:
    include: all
```

**Generated code:**
```java
// For VARCHAR(50) columns
public record VarcharMax50(String value) {
    public VarcharMax50 {
        if (value != null && value.length() > 50) {
            throw new IllegalArgumentException("Value exceeds max length 50");
        }
    }
}

// For CHAR(10) columns (padded)
public record PaddedString10(String value) { ... }
```

### Field Values

Generate field-level update types:

```yaml
matchers:
  field_values:
    include:
      - public.users
      - public.products
```

**Generated code:**
```java
// Update specific fields only
userRepo.update(userId, List.of(
    UserFields.email(new Email("new@example.com")),
    UserFields.isActive(new IsActive(false))
));
```

## Combining Matchers

Matchers can be combined for fine-grained control:

```yaml
matchers:
  # Base table matching
  schemas: [public, sales]

  tables:
    include: all
    exclude:
      - "*_backup"
      - "*_temp"
      - flyway_*
      - databasechangelog*

  # Feature matching
  mock_repos:
    include: all
    exclude: ["*_audit", "*_log"]

  primary_key_types:
    include: all

  test_inserts:
    include:
      - public.users
      - public.products
      - sales.orders
      - sales.customers

  readonly:
    include:
      - "*_view"
      - "public.reports_*"

  precise_types:
    include: all

  field_values:
    include:
      - public.users
      - public.products
```

## Common Patterns

### Exclude System Tables

```yaml
matchers:
  tables:
    include: all
    exclude:
      # PostgreSQL system
      - pg_catalog.*
      - information_schema.*

      # Migration tools
      - flyway_*
      - databasechangelog*
      - schema_migrations
      - __*                    # Rails internal

      # Temporary tables
      - "*_temp"
      - "*_backup"
      - "*_old"
```

### Development vs Production

```yaml
# typr-dev.yaml
matchers:
  test_inserts:
    include: all
  mock_repos:
    include: all

# typr-prod.yaml
matchers:
  test_inserts:
    include: []              # No test helpers in prod
  mock_repos:
    include: []              # No mocks in prod
```

### Microservice Boundaries

```yaml
# user-service/typr.yaml
matchers:
  schemas: [users]
  tables:
    include:
      - users.users
      - users.sessions
      - users.roles

# order-service/typr.yaml
matchers:
  schemas: [orders]
  tables:
    include:
      - orders.orders
      - orders.order_items
      - orders.shipping
```

### Legacy Table Handling

```yaml
matchers:
  # Generate for all tables
  tables:
    include: all

  # But make legacy tables read-only
  readonly:
    include:
      - legacy_*
      - old_*

  # Skip ID types for legacy (might have composite keys)
  primary_key_types:
    include: all
    exclude:
      - legacy_*
      - old_*
```

## Matcher Precedence

When patterns overlap:

1. **Negation patterns win** - `!pattern` always excludes, regardless of other matches
2. **More specific wins** - `public.users` takes precedence over `public.*`
3. **Order matters** - Later patterns override earlier ones in the same list

```yaml
matchers:
  tables:
    include:
      - public.*           # Include all public tables
    exclude:
      - public.temp_*      # But exclude temp tables
      - public.users       # This specific exclusion wins
```

Or using inline negation:

```yaml
matchers:
  tables:
    - public.*
    - "!public.temp_*"
    - "!public.users"
```
