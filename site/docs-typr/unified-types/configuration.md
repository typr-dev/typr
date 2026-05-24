---
title: Configuration
sidebar_position: 4
---

# Configuration

Typr Bridge is configured via `typr.yaml`. Write it by hand using the reference below.

## YAML Reference

### File Structure

```yaml
version: 1

sources:
  postgres: { ... }
  mariadb: { ... }
  api: { ... }

# Domain types - your canonical model (THE feature)
domainTypes:
  Customer: { ... }
  Order: { ... }

# Field types - type safety enhancement (optional but recommended)
fieldTypes:
  CustomerId: { ... }
  FirstName: { ... }

output:
  path: ./generated
  language: java
```

---

### Sources

#### PostgreSQL

```yaml
sources:
  postgres:
    type: postgresql
    url: ${POSTGRES_URL}
    # Or individual params:
    host: localhost
    port: 5432
    database: myapp
    username: ${POSTGRES_USER}
    password: ${POSTGRES_PASSWORD}
    schemas: [public, sales]    # Optional
```

#### MariaDB / MySQL

```yaml
sources:
  mariadb:
    type: mariadb
    host: localhost
    port: 3306
    database: myapp
    username: ${MARIADB_USER}
    password: ${MARIADB_PASSWORD}
```

#### Oracle

```yaml
sources:
  oracle:
    type: oracle
    host: localhost
    port: 1521
    service: FREEPDB1
    username: ${ORACLE_USER}
    password: ${ORACLE_PASSWORD}
```

#### SQL Server

```yaml
sources:
  sqlserver:
    type: sqlserver
    host: localhost
    port: 1433
    database: myapp
    username: sa
    password: ${SQLSERVER_PASSWORD}
```

#### DuckDB

```yaml
sources:
  duckdb:
    type: duckdb
    path: ./data/analytics.duckdb
```

#### SQLite

```yaml
sources:
  sqlite:
    type: sqlite
    path: ./data/app.db    # or :memory:
```

#### OpenAPI

```yaml
sources:
  api:
    type: openapi
    spec: ./api/openapi.yaml
```

---

### Domain Types

Domain types define your canonical model with a primary source and aligned sources. See [Domain Types](/typr/unified-types/domain-types) for full documentation.

```yaml
domainTypes:
  TypeName:
    primary: source:entity            # The anchor source
    fields:
      fieldName: FieldType
      optionalField: FieldType?       # ? = optional
    alignedSources:                   # Other sources aligned to this type
      source:entity: mode
```

**Alignment modes:**
- `exact` - Fields must match exactly
- `superset` - Source may have extra fields
- `subset` - Source may have fewer fields

**Field mappings** for non-standard names:

```yaml
domainTypes:
  Customer:
    primary: postgres:sales.customer
    fields:
      id: CustomerId
      firstName: FirstName
      email: Email?
    alignedSources:
      mariadb:legacy.customers:
        mode: superset
        mappings:
          id: cust_id
          email: electronic_mail
      api:Customer: exact
```

---

### Field Types

Field types add type safety at the field level. See [Field Types](/typr/unified-types/field-types) for full documentation.

```yaml
fieldTypes:
  TypeName:
    db:
      column: [column_name]      # Match column names
      table: [table_name]        # Limit to specific tables
      schema: [schema_name]      # Limit to specific schemas
      db_type: [uuid, bigint]    # Match database types
      primary_key: true          # Only match PKs
      annotation: ["@tag"]       # Match column comments
    model:
      name: [fieldName]          # Match API field names
      schema: [ModelName]        # Limit to specific models
      format: [email, uuid]      # Match OpenAPI format
```

**Patterns:**
- `*` matches any sequence: `"*email*"` matches `email`, `user_email`, `email_address`
- `!` negates: `["*email*", "!email_hash"]` matches email columns except `email_hash`

**Examples:**

```yaml
fieldTypes:
  # Primary key type
  CustomerId:
    db:
      column: [customer_id, cust_id]
      primary_key: true
    model:
      name: [customerId]

  # Pattern matching
  Email:
    db:
      column: ["*email*", "!*_hash"]
    model:
      name: ["*email*"]

  # Boolean flags
  IsActive:
    db:
      column: [is_active, active, enabled]
    model:
      name: [isActive, active]

  # Audit timestamps
  CreatedAt:
    db:
      column: [created_at, created_date, date_created]
    model:
      name: [createdAt]
```

---

### Output

```yaml
output:
  path: ./generated
  language: java              # java, kotlin, scala
  json: jackson               # jackson, circe, play-json, zio-json

  # Optional: per-source paths
  sources:
    postgres:
      package: com.myapp.db.postgres
      path: ./generated/postgres
    api:
      package: com.myapp.api
      path: ./generated/api

  # Optional: shared types location
  shared:
    package: com.myapp.shared
    path: ./generated/shared

  options:
    enable_dsl: true
    enable_test_inserts: true
```

---

### Environment Variables

Use `${VAR}` or `${VAR:-default}`:

```yaml
sources:
  postgres:
    host: ${POSTGRES_HOST:-localhost}
    password: ${POSTGRES_PASSWORD}
```

---

### Multiple Files

Split large configs with includes:

```yaml
version: 1
include:
  - ./config/sources.yaml
  - ./config/field-types.yaml
  - ./config/domain-types.yaml
```

---

## Complete Example

```yaml
version: 1

sources:
  postgres:
    type: postgresql
    url: ${POSTGRES_URL}
  mariadb:
    type: mariadb
    url: ${MARIADB_URL}
  api:
    type: openapi
    spec: ./api/openapi.yaml

# Domain types - your canonical model
domainTypes:
  Customer:
    primary: postgres:sales.customer
    fields:
      id: CustomerId
      firstName: FirstName
      lastName: LastName
      email: Email?
      active: IsActive
    alignedSources:
      mariadb:customers: superset
      api:Customer: exact

  Order:
    primary: postgres:sales.order
    fields:
      id: OrderId
      customerId: CustomerId
      total: BigDecimal
      status: String
    alignedSources:
      api:Order: exact

# Field types - type safety at the field level
fieldTypes:
  CustomerId:
    db: { column: [customer_id], primary_key: true }
    model: { name: [customerId] }
  OrderId:
    db: { column: [order_id], primary_key: true }
    model: { name: [orderId] }
  FirstName:
    db: { column: [first_name, firstname] }
    model: { name: [firstName] }
  LastName:
    db: { column: [last_name, lastname] }
    model: { name: [lastName] }
  Email:
    db: { column: ["*email*"] }
    model: { name: ["*email*"] }
  IsActive:
    db: { column: [is_active, active] }
    model: { name: [isActive] }

output:
  path: ./generated
  language: java
  json: jackson
```

---

## CLI Commands

```bash
# Validate configuration
typr validate

# Generate code
typr generate
```

See [CLI Reference](/typr/cli) for full documentation.
