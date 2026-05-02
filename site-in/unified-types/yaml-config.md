---
title: YAML Configuration
sidebar_position: 2
---

# YAML Configuration

While you can define unified types programmatically in Scala/Java/Kotlin, Typr also supports a declarative YAML format that makes type definitions easy to read, share, and version control.

## Basic Structure

```yaml
# typr-types.yaml
version: 1

sources:
  postgres:
    type: postgresql
    host: localhost
    port: 5432
    database: production
    username: ${POSTGRES_USER}
    password: ${POSTGRES_PASSWORD}

  mariadb:
    type: mariadb
    host: localhost
    port: 3306
    database: legacy
    username: ${MARIADB_USER}
    password: ${MARIADB_PASSWORD}

  api:
    type: openapi
    spec: ./api/openapi.yaml

types:
  # Simple column name matching
  FirstName:
    db:
      column: [first_name, firstname]
    api:
      name: [firstName]

  # Pattern matching with globs
  Email:
    db:
      column: ["*email*"]
    api:
      name: ["*email*", "*Email*"]

  # Boolean flags
  IsActive:
    db:
      column: [is_active, active]
    api:
      name: [isActive, active]
```

## Data Source Configuration

### PostgreSQL

```yaml
sources:
  postgres:
    type: postgresql
    host: localhost
    port: 5432
    database: myapp
    username: ${POSTGRES_USER}
    password: ${POSTGRES_PASSWORD}
    # Optional settings
    ssl: require
    schemas: [public, app]
```

### MariaDB / MySQL

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

### SQL Server

```yaml
sources:
  sqlserver:
    type: sqlserver
    host: localhost
    port: 1433
    database: myapp
    username: sa
    password: ${SQLSERVER_PASSWORD}
    encrypt: false
```

### Oracle

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

### DuckDB

```yaml
sources:
  duckdb:
    type: duckdb
    path: ./data/analytics.duckdb
```

### OpenAPI

```yaml
sources:
  api:
    type: openapi
    spec: ./api/openapi.yaml
    # Or multiple specs
    specs:
      - ./api/customers.yaml
      - ./api/products.yaml
```

## Type Definitions

### Column/Field Name Matching

```yaml
types:
  CustomerId:
    db:
      column: [customer_id, cust_id]
    api:
      name: [customerId]

  # Glob patterns
  Email:
    db:
      column: ["*email*"]
    api:
      name: ["*email*", "*Email*"]
```

### Scoped Matching

```yaml
types:
  # Only match in specific tables
  ProductPrice:
    db:
      table: [products, product_variants]
      column: [price, unit_price]
    api:
      name: [price, unitPrice]

  # Only match in specific schemas
  AuditUser:
    db:
      schema: [audit]
      column: [user_id, modified_by]
    api:
      path: ["/audit/*"]
      name: [userId]
```

### Primary Key Types

```yaml
types:
  CustomerId:
    db:
      column: [customer_id]
      primary_key: true
    api:
      name: [customerId]
```

### Boolean Flags

```yaml
types:
  IsActive:
    db:
      column: [is_active, active]
    api:
      name: [isActive, active]

  IsVerified:
    db:
      column: [is_verified, verified, email_verified]
    api:
      name: [isVerified, verified, emailVerified]
```

### API Location Matching

```yaml
types:
  AuthToken:
    api:
      location: [header]
      name: [Authorization, X-Auth-Token]

  PageSize:
    api:
      location: [query]
      name: [pageSize, limit]

  RequestBody:
    api:
      location: [request_body, response_body]
      name: [data, payload]
```

### Format and Type Matching

```yaml
types:
  UUID:
    db:
      db_type: [uuid]
    api:
      format: [uuid]

  DateTime:
    db:
      db_type: [timestamp, timestamptz]
    api:
      format: [date-time]
```

### Comment Annotations

```yaml
types:
  Currency:
    db:
      annotation: ["@currency"]
    api:
      extension:
        x-currency: "true"

  Sensitive:
    db:
      annotation: ["@sensitive", "@pii"]
    api:
      extension:
        x-sensitive: "true"
```

## Output Configuration

```yaml
output:
  # Shared types package
  shared:
    package: com.myapp.shared
    path: ./generated/shared

  # Per-source output
  sources:
    postgres:
      package: com.myapp.db.postgres
      path: ./generated/postgres

    mariadb:
      package: com.myapp.db.mariadb
      path: ./generated/mariadb

    api:
      package: com.myapp.api
      path: ./generated/api

  # Generation options
  options:
    lang: java           # java, kotlin, scala
    json: jackson        # jackson, circe, play-json, zio-json
    enable_dsl: true
    enable_test_inserts: true
    enable_mock_repos: true
```

## Environment Variables

Use `${VAR}` or `${VAR:-default}` syntax for sensitive values:

```yaml
sources:
  postgres:
    type: postgresql
    host: ${POSTGRES_HOST:-localhost}
    port: ${POSTGRES_PORT:-5432}
    database: ${POSTGRES_DB}
    username: ${POSTGRES_USER}
    password: ${POSTGRES_PASSWORD}
```

## Multiple Configurations

You can split configurations across files:

```yaml
# typr-types.yaml
version: 1
include:
  - ./config/sources.yaml
  - ./config/types-identity.yaml
  - ./config/types-common.yaml
  - ./config/types-audit.yaml
```

```yaml
# config/sources.yaml
sources:
  postgres:
    type: postgresql
    # ...
```

```yaml
# config/types-identity.yaml
types:
  CustomerId:
    db:
      column: [customer_id]
      primary_key: true
    api:
      name: [customerId]
```

## Validation

The YAML configuration is validated at load time:

```
$ typr validate typr-types.yaml

Validating typr-types.yaml...

Sources:
  postgres: Connected (PostgreSQL 16.1)
  mariadb: Connected (MariaDB 11.2)
  api: Loaded (3 endpoints, 12 schemas)

Types:
  FirstName: Matched 4 columns, 3 API fields
  LastName: Matched 4 columns, 3 API fields
  Email: Matched 6 columns, 4 API fields
  IsActive: Matched 8 columns, 5 API fields

Warnings:
  - Type 'PhoneNumber' has no matches in 'postgres'

Configuration valid.
```

## Complete Example

```yaml
version: 1

sources:
  postgres:
    type: postgresql
    host: ${POSTGRES_HOST:-localhost}
    port: ${POSTGRES_PORT:-5432}
    database: production
    username: ${POSTGRES_USER}
    password: ${POSTGRES_PASSWORD}
    schemas: [public, person, sales]

  mariadb:
    type: mariadb
    host: ${MARIADB_HOST:-localhost}
    port: ${MARIADB_PORT:-3306}
    database: legacy
    username: ${MARIADB_USER}
    password: ${MARIADB_PASSWORD}

  customers-api:
    type: openapi
    spec: ./api/customers.yaml

  products-api:
    type: openapi
    spec: ./api/products.yaml

types:
  # Identity types
  CustomerId:
    db:
      column: [customer_id, cust_id]
      primary_key: true
    api:
      name: [customerId]

  EmployeeId:
    db:
      column: [employee_id, emp_id]
      primary_key: true
    api:
      name: [employeeId]

  ProductId:
    db:
      column: [product_id, prod_id]
      primary_key: true
    api:
      name: [productId]

  # String types
  FirstName:
    db:
      column: [first_name, firstname, fname]
    api:
      name: [firstName, fname]

  LastName:
    db:
      column: [last_name, lastname, lname]
    api:
      name: [lastName, lname]

  Email:
    db:
      column: ["*email*"]
    api:
      name: ["*email*", "*Email*"]

  PhoneNumber:
    db:
      column: [phone, phone_number, mobile]
    api:
      name: [phone, phoneNumber, mobile]

  # Boolean flags
  IsActive:
    db:
      column: [is_active, active]
    api:
      name: [isActive, active]

  IsVerified:
    db:
      column: [is_verified, verified]
    api:
      name: [isVerified, verified]

  IsPrimary:
    db:
      column: [is_primary, is_default]
    api:
      name: [isPrimary, isDefault]

  # Audit fields
  CreatedAt:
    db:
      column: [created_at, created_date, create_date]
    api:
      name: [createdAt]

  UpdatedAt:
    db:
      column: [updated_at, modified_at, last_modified]
    api:
      name: [updatedAt, modifiedAt]

  CreatedBy:
    db:
      column: [created_by, creator]
    api:
      name: [createdBy]

output:
  shared:
    package: com.acme.shared.types
    path: ./generated/shared

  sources:
    postgres:
      package: com.acme.db.postgres
      path: ./generated/postgres
    mariadb:
      package: com.acme.db.mariadb
      path: ./generated/mariadb
    customers-api:
      package: com.acme.api.customers
      path: ./generated/api/customers
    products-api:
      package: com.acme.api.products
      path: ./generated/api/products

  options:
    lang: java
    json: jackson
    enable_dsl: true
    enable_test_inserts: true
    enable_mock_repos: true
    enable_precise_types: true
```
