---
title: Database Setup
sidebar_position: 2
---

# Database Setup

For complete installation and configuration instructions, see the [Getting Started guide](/typr/getting-started).

## Quick Reference

### Supported Databases

| Database | Status |
|----------|--------|
| PostgreSQL | Full support including domains, enums, arrays, JSON, UUID |
| MariaDB/MySQL | Full support including unsigned types |
| SQL Server | Full support including T-SQL features |
| Oracle | Full support including OBJECT and MULTISET types |
| DuckDB | Full support for embedded analytical workloads |
| IBM DB2 | Full support including distinct types |

### Configuration Example

```yaml
sources:
  postgres:
    type: postgresql
    host: localhost
    port: 5432
    database: myapp
    username: ${POSTGRES_USER}
    password: ${POSTGRES_PASSWORD}
    schemas: [public, sales]

output:
  path: ./generated
  package: com.myapp.db
  language: java
```

### Generate Code

```bash
typr generate
```

## Database-Specific Features

Each database has unique features that Typr models with full fidelity:

- **PostgreSQL**: Arrays, enums, domains, composite types, JSON/JSONB
- **MariaDB**: Unsigned integers, ENUMs, SETs
- **Oracle**: OBJECT types, nested tables, MULTISET
- **SQL Server**: Alias types, table-valued parameters
- **DuckDB**: Nested types, structs, lists

See [Type Safety](/typr/boundaries/databases/type-safety/id-types) for details on how these are represented in generated code.

## Next Steps

- [Getting Started](/typr/getting-started) - Full installation and configuration guide
- [Configuration Reference](/typr/configuration) - All configuration options
- [Matchers](/typr/matchers) - Control which tables to include
- [Customization](/typr/boundaries/databases/customization/overview) - Customize generated code
