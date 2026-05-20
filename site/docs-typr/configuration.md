---
title: Configuration Reference
sidebar_position: 3
---

# Configuration Reference

Typr uses a YAML configuration file (typically `typr.yaml`) to define data sources, type mappings, and output options.

## Configuration Structure

```yaml
version: 1

# Data sources (databases and OpenAPI specs)
sources:
  # ...

# Unified type definitions
types:
  # ...

# Matchers for filtering what to generate
matchers:
  # ...

# Output configuration
output:
  # ...
```

## Sources

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
    ssl: require                    # disable, allow, prefer, require
    schemas: [public, app]          # Schemas to include (default: all)
    connection_timeout: 30          # Seconds
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

    # Optional settings
    ssl: false
    connection_timeout: 30
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

    # Optional settings
    encrypt: true
    trust_server_certificate: false
    schemas: [dbo]
```

### Oracle

```yaml
sources:
  oracle:
    type: oracle
    host: localhost
    port: 1521
    service: FREEPDB1              # Or use 'sid' instead
    username: ${ORACLE_USER}
    password: ${ORACLE_PASSWORD}

    # Optional settings
    schemas: [HR, SALES]
```

### DuckDB

```yaml
sources:
  duckdb:
    type: duckdb
    path: ./data/analytics.duckdb  # Path to DuckDB file

    # For in-memory database
    # path: :memory:
```

### DB2

```yaml
sources:
  db2:
    type: db2
    host: localhost
    port: 50000
    database: myapp
    username: ${DB2_USER}
    password: ${DB2_PASSWORD}

    # Optional settings
    schemas: [DB2ADMIN]
```

### SQLite

```yaml
sources:
  sqlite:
    type: sqlite
    path: ./data/app.db          # File-backed

    # For in-memory database
    # path: :memory:

    # Optional: load schema from a SQL file on connect (useful for :memory:)
    schema_sql: sql-init/sqlite/00-schema.sql
```

SQLite has no schemas in the SQL-standard sense — typr generates everything under a single namespace. The xerial sqlite-jdbc driver is bundled in the typr CLI.

### OpenAPI

```yaml
sources:
  api:
    type: openapi
    spec: ./api/openapi.yaml

  # Multiple specs
  customers-api:
    type: openapi
    spec: ./api/customers.yaml

  products-api:
    type: openapi
    spec: ./api/products.yaml

  # From URL
  external-api:
    type: openapi
    spec: https://api.example.com/openapi.json
```

### JSON Schema

```yaml
sources:
  # Single schema file
  models:
    type: jsonschema
    spec: ./schemas/models.json

  # Multiple schema files
  domain-models:
    type: jsonschema
    specs:
      - ./schemas/user.json
      - ./schemas/order.json
      - ./schemas/*.schema.json    # Glob patterns supported
```

### Avro / Kafka Events

```yaml
boundaries:
  events:
    type: avro
    schemas: ./schemas/**/*.avsc   # Glob pattern for .avsc files

    # Or from Schema Registry
    schema_registry: http://localhost:8081
    subjects: ["order-*", "user-*"]

    # Wire format
    wire_format: confluent_registry  # confluent_registry, binary, json

    # Topic configuration
    topic_groups:
      order-events:
        - com.example.events.OrderPlaced
        - com.example.events.OrderUpdated
        - com.example.events.OrderCancelled

    topic_keys:
      order-events: string
      user-events: uuid

    # Header schema
    header_schemas:
      standard:
        fields:
          - name: correlationId
            type: uuid
            required: true
          - name: timestamp
            type: instant
            required: true
    default_header_schema: standard

    # Generation options
    generate_records: true
    generate_serdes: true
    generate_producers: true
    generate_consumers: true
    generate_kafka_events: true    # Framework-specific publishers/listeners
    generate_kafka_rpc: false      # RPC client/server (Spring/Quarkus only)
    enable_precise_types: true
```

### Connection Strings

For complex connections, use a JDBC URL directly:

```yaml
sources:
  postgres:
    type: postgresql
    url: jdbc:postgresql://localhost:5432/myapp?ssl=true&sslmode=require
    username: ${POSTGRES_USER}
    password: ${POSTGRES_PASSWORD}
```

## Types

Type definitions match columns and API fields to unified types.

### Basic Matching

```yaml
types:
  # Match by column/field name
  FirstName:
    db:
      column: [first_name, firstname]
    model:
      name: [firstName]

  # Glob patterns
  Email:
    db:
      column: ["*email*"]
    model:
      name: ["*email*", "*Email*"]
```

### Database Matching Options

```yaml
types:
  CustomerId:
    db:
      # Match specific databases (for multi-db setups)
      database: [production, staging]

      # Match specific schemas
      schema: [customers, public]

      # Match specific tables
      table: [customers, users, accounts]

      # Match column names (glob patterns supported)
      column: [customer_id, cust_id]

      # Match database types
      db_type: [uuid, int4, bigint]

      # Match only primary keys
      primary_key: true

      # Match nullability
      nullable: false

      # Match foreign key references
      references: [customers]

      # Match column comments containing text
      comment: ["*customer*"]

      # Match annotations in comments (e.g., -- @customer)
      annotation: ["@customer"]
```

### Model Matching Options

The `model` matcher works for both OpenAPI schemas and standalone JSON Schema sources.

```yaml
types:
  Email:
    model:
      # Match specific specs (OpenAPI or JSON Schema)
      spec: [customers-api, domain-models]

      # Match schema names / $ref
      schema: [User, Customer, "*Request", "*Response"]

      # Match property names (glob patterns supported)
      name: ["*email*", "*Email*"]

      # Match JSON path within schema
      json_path: ["$.properties.contact.*"]

      # Match schema type
      schema_type: [string]

      # Match format
      format: [email]

      # Match required status
      required: true

      # Match x-* extensions
      extension:
        x-pii: "true"
```

### API Matching Options

The `api` matcher is for OpenAPI parameters only (header, query, path, cookie). For request/response bodies, use the `model` matcher.

```yaml
types:
  AuthToken:
    api:
      # Match specific OpenAPI specs
      spec: [customers-api]

      # Match parameter location
      location: [header, query, path, cookie]

      # Match operation IDs
      operation_id: ["get*", "create*"]

      # Match HTTP methods
      http_method: [GET, POST]

      # Match URL paths
      path: ["/users/*", "/api/v1/*"]

      # Match parameter names
      name: [Authorization, X-Auth-Token]

      # Match required status
      required: true

      # Match x-* extensions
      extension:
        x-auth: "true"
```

### Match Semantics

| Rule | Meaning |
|------|---------|
| Empty list `[]` | Matches anything (wildcard) |
| Non-empty list | Matches if **any** pattern matches (OR) |
| Multiple fields | **All** non-empty fields must match (AND) |
| `*` in pattern | Matches any sequence of characters |
| `?` in pattern | Matches single character |
| `!` prefix | Negation: excludes matches |

### Negation Patterns

Prefix any pattern with `!` to exclude matches:

```yaml
types:
  Email:
    db:
      # Match all columns containing 'email', except specific ones
      column: ["*email*", "!email_hash", "!email_verified_at"]
    model:
      name: ["*email*", "!emailInternal"]
```

Negation is evaluated after positive patterns—a negation removes items that would otherwise match.

## Matchers

Matchers control which database objects to include in code generation.

### Table Matching

```yaml
matchers:
  # Include specific schemas
  schemas: [public, app, sales]

  # Include specific tables
  tables:
    - public.users
    - public.orders
    - sales.*              # All tables in sales schema

  # Exclude patterns
  exclude:
    - "*_backup"           # Exclude backup tables
    - "*_temp"             # Exclude temp tables
    - pg_catalog.*         # Exclude system tables
    - information_schema.*
```

### Feature Matching

Control which features to enable per-table:

```yaml
matchers:
  # Generate mock repositories for testing
  mock_repos:
    include: [public.*]
    exclude: [public.audit_*]

  # Generate type-safe ID wrappers
  primary_key_types:
    include: all
    exclude: [public.migrations]

  # Generate test insert helpers
  test_inserts:
    include:
      - public.users
      - public.products
    exclude: []

  # Generate read-only repositories (no insert/update/delete)
  readonly:
    include:
      - public.audit_*
      - public.*_view

  # Generate precise types (PaddedString50, etc.)
  precise_types:
    include: all

  # Generate field value types for updates
  field_values:
    include: [public.users, public.products]
```

### Shorthand Syntax

For simple cases:

```yaml
matchers:
  # Include all
  tables: all

  # Include specific list
  tables: [public.users, public.orders]

  # Exclude only
  tables:
    include: all
    exclude: [*_backup, *_temp]
```

## Output

### Basic Output

```yaml
output:
  path: ./generated
  package: com.myapp.generated
  language: java                   # java, kotlin, scala
  json: jackson                    # jackson, circe, play-json, zio-json
```

### Per-Source Output

```yaml
output:
  # Shared types (unified across sources)
  shared:
    path: ./generated/shared
    package: com.myapp.shared

  # Per-source output directories
  sources:
    postgres:
      path: ./generated/db/postgres
      package: com.myapp.db.postgres

    mariadb:
      path: ./generated/db/mariadb
      package: com.myapp.db.mariadb

    api:
      path: ./generated/api
      package: com.myapp.api
```

### Language Options

```yaml
output:
  language: java

  # Scala-specific options
  scala:
    dialect: scala3                # scala2, scala3
    type_support: scala            # scala (native), java (java types in scala)

  # Kotlin-specific options
  kotlin:
    nullable_annotations: true     # Add @Nullable/@NotNull annotations
```

### Generation Options

```yaml
output:
  options:
    # JSON library
    json: jackson

    # Generate test data helpers
    enable_test_inserts: true

    # Generate mock repositories
    enable_mock_repos: true

    # Generate precise types (VarcharMax100, PaddedString50)
    enable_precise_types: true

    # Generate streaming insert methods
    enable_streaming_inserts: true

    # File header comment
    file_header: |
      /**
       * Generated by Typr. DO NOT EDIT.
       */

    # Schema mode for package naming
    schema_mode: multi_schema      # multi_schema, single_schema
```

### Framework Integration

Framework integration affects all boundary types (databases, APIs, events).

```yaml
output:
  framework: spring              # none, spring, quarkus, jaxrs, http4s, cats
  effect_type: completable_future
```

| Framework | Description | Effect Type |
|-----------|-------------|-------------|
| `none` | No annotations, vanilla code | `blocking` |
| `spring` | `@Service`, `@Repository`, `@KafkaListener` | `completable_future` |
| `quarkus` | `@ApplicationScoped`, `@Incoming`, Mutiny | `mutiny_uni` |
| `jaxrs` | JAX-RS annotations (APIs only) | `blocking` |
| `http4s` | Http4s routes (APIs only, Scala) | `cats_io` |
| `cats` | fs2-kafka, fs2-grpc (Scala) | `cats_io` |

| Effect Type | Return Type | Description |
|-------------|-------------|-------------|
| `blocking` | `T` | Synchronous |
| `completable_future` | `CompletableFuture<T>` | Java async |
| `mutiny_uni` | `Uni<T>` | Quarkus Mutiny |
| `cats_io` | `IO[T]` | Cats Effect |
| `zio` | `Task[T]` | ZIO |

**Note:** Cats framework only supports Avro events (`generate_kafka_events`), not Kafka RPC. fs2-kafka does not have a built-in request-reply pattern.

## Environment Variables

Use `${VAR}` syntax for sensitive values:

```yaml
sources:
  postgres:
    host: ${POSTGRES_HOST:-localhost}     # With default
    port: ${POSTGRES_PORT:-5432}
    database: ${POSTGRES_DB}              # Required
    username: ${POSTGRES_USER}
    password: ${POSTGRES_PASSWORD}
```

Environment variables can be:
- Set in your shell: `export POSTGRES_USER=myuser`
- In a `.env` file (loaded automatically)
- Passed via CLI: `POSTGRES_USER=myuser typr generate`

## File Includes

Split configuration across multiple files:

```yaml
# typr.yaml
version: 1
include:
  - ./config/sources.yaml
  - ./config/types/*.yaml
  - ./config/output.yaml
```

```yaml
# config/sources.yaml
sources:
  postgres:
    type: postgresql
    # ...
```

```yaml
# config/types/identity.yaml
types:
  CustomerId:
    db:
      column: [customer_id]
      primary_key: true
    model:
      name: [customerId]
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
    schemas: [public, sales, inventory]

  legacy-mysql:
    type: mariadb
    host: ${MYSQL_HOST:-localhost}
    port: 3306
    database: legacy_app
    username: ${MYSQL_USER}
    password: ${MYSQL_PASSWORD}

  customers-api:
    type: openapi
    spec: ./api/customers-v2.yaml

  orders-api:
    type: openapi
    spec: ./api/orders-v1.yaml

types:
  # Identity types
  CustomerId:
    db:
      column: [customer_id, cust_id]
      primary_key: true
    model:
      name: [customerId]

  OrderId:
    db:
      column: [order_id]
      primary_key: true
    model:
      name: [orderId]

  ProductId:
    db:
      column: [product_id, prod_id]
      primary_key: true
    model:
      name: [productId]

  # String types
  Email:
    db:
      column: ["*email*"]
    model:
      name: ["*email*", "*Email*"]

  FirstName:
    db:
      column: [first_name, firstname, fname]
    model:
      name: [firstName, fname]

  LastName:
    db:
      column: [last_name, lastname, lname]
    model:
      name: [lastName, lname]

  # Boolean flags
  IsActive:
    db:
      column: [is_active, active, enabled]
    model:
      name: [isActive, active, enabled]

  # Timestamps
  CreatedAt:
    db:
      column: [created_at, created_date, create_date]
    model:
      name: [createdAt]

  UpdatedAt:
    db:
      column: [updated_at, modified_at, last_modified]
    model:
      name: [updatedAt, modifiedAt]

matchers:
  schemas: [public, sales, inventory]
  exclude:
    - "*_backup"
    - "*_temp"
    - flyway_*
    - databasechangelog*

  mock_repos:
    include: all

  test_inserts:
    include: [public.*, sales.orders]

  precise_types:
    include: all

output:
  shared:
    path: ./generated/shared
    package: com.acme.shared

  sources:
    postgres:
      path: ./generated/db/postgres
      package: com.acme.db.postgres
    legacy-mysql:
      path: ./generated/db/mysql
      package: com.acme.db.mysql
    customers-api:
      path: ./generated/api/customers
      package: com.acme.api.customers
    orders-api:
      path: ./generated/api/orders
      package: com.acme.api.orders

  language: java
  options:
    json: jackson
    enable_mock_repos: true
    enable_test_inserts: true
    enable_precise_types: true
```
