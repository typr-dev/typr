---
title: CLI
sidebar_position: 2
---

# Typr CLI

The Typr CLI generates type-safe code from your configuration.

## Commands

```bash
typr              # Generate code (default)
typr init         # Create typr.yaml
typr validate     # Check configuration
```

That's it. Three commands.

## Generate Code

Running `typr` without arguments reads `typr.yaml` and generates code:

```bash
typr
```

```
Connecting to postgres... ✓
Connecting to mariadb... ✓
Loading api/openapi.yaml... ✓

Generating shared types...
  FirstName, LastName, Email, CustomerId, OrderId

Generating postgres...
  42 tables, 156 files

Generating mariadb...
  18 tables, 67 files

Generating api...
  12 endpoints, 34 files

Done. 257 files in ./generated
```

### Options

```bash
typr --config other.yaml    # Use different config file
typr --watch                # Regenerate on changes
typr --verbose              # Show detailed output
```

## Initialize Project

```bash
typr init
```

Creates a `typr.yaml` with sensible defaults. You'll be prompted for:
- Language (Java/Kotlin/Scala)
- Output directory
- First data source connection

## Validate

```bash
typr validate
```

Checks your configuration without generating code:

```
Validating typr.yaml...

Sources:
  ✓ postgres: Connected (PostgreSQL 16.1)
  ✓ mariadb: Connected (MariaDB 11.2)
  ✓ api: Loaded (3 endpoints, 12 schemas)

Types:
  ✓ FirstName: 6 matches, compatible (String)
  ✓ Email: 8 matches, compatible (String)
  ⚠ PhoneNumber: No matches in 'mariadb'

Configuration valid.
```

## Configuration File

See [Configuration Reference](../configuration) for the full `typr.yaml` format.

Minimal example:

```yaml
sources:
  postgres:
    type: postgresql
    host: localhost
    database: myapp
    username: ${POSTGRES_USER}
    password: ${POSTGRES_PASSWORD}

output:
  path: ./generated
  package: com.myapp
  language: java
```
