---
title: CLI & TUI Tool
sidebar_position: 3
---

# Typr CLI & TUI

The Typr command-line tool provides both a powerful CLI for automation and an interactive TUI (Terminal User Interface) for exploring and configuring your data sources interactively.

## Installation

```bash
# Via Coursier (recommended)
cs install typr

# Via Homebrew (macOS)
brew install oyvindberg/tap/typr

# Via npm (cross-platform)
npm install -g @typr/cli

# Or download directly
curl -L https://github.com/oyvindberg/typr/releases/latest/download/typr-cli.jar -o typr.jar
java -jar typr.jar --help
```

## Quick Start

```bash
# Initialize a new project
typr init

# Launch interactive TUI
typr tui

# Generate code from config
typr generate

# Validate configuration
typr validate
```

## Interactive TUI

The TUI provides a visual interface for exploring and configuring data sources:

```bash
typr tui
```

```
┌─────────────────────────────────────────────────────────────────┐
│  TYPR - Unified Type Generator                         v2.0.0  │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Data Sources                                                   │
│  ────────────                                                   │
│  ▸ postgres     PostgreSQL 16.1    production    ● Connected   │
│    mariadb      MariaDB 11.2       legacy        ● Connected   │
│    api          OpenAPI 3.0        customers.yaml ✓ Loaded     │
│                                                                 │
│  + Add Source                                                   │
│                                                                 │
├─────────────────────────────────────────────────────────────────┤
│  Unified Types                                                  │
│  ─────────────                                                  │
│  ▸ FirstName       4 db columns, 3 api fields                   │
│    LastName        4 db columns, 3 api fields                   │
│    Email           6 db columns, 4 api fields                   │
│    IsActive        8 db columns, 5 api fields                   │
│    CustomerId      2 db columns, 2 api fields   (primary key)   │
│                                                                 │
│  + Add Type                                                     │
│                                                                 │
├─────────────────────────────────────────────────────────────────┤
│  [G]enerate   [V]alidate   [S]ave   [Q]uit   [?]Help           │
└─────────────────────────────────────────────────────────────────┘
```

### Adding a Data Source

Press `+` on "Add Source" to open the connection wizard:

```
┌─────────────────────────────────────────────────────────────────┐
│  Add Data Source                                                │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Source Type:                                                   │
│  ────────────                                                   │
│  ▸ PostgreSQL                                                   │
│    MariaDB / MySQL                                              │
│    SQL Server                                                   │
│    Oracle                                                       │
│    DuckDB                                                       │
│    OpenAPI Specification                                        │
│                                                                 │
├─────────────────────────────────────────────────────────────────┤
│  ↑↓ Select   Enter Confirm   Esc Cancel                        │
└─────────────────────────────────────────────────────────────────┘
```

After selecting a type, configure the connection:

```
┌─────────────────────────────────────────────────────────────────┐
│  Configure PostgreSQL Connection                                │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Name:     [postgres___________]                                │
│  Host:     [localhost__________]                                │
│  Port:     [5432__]                                             │
│  Database: [production_________]                                │
│  Username: [postgres___________]                                │
│  Password: [••••••••___________]                                │
│                                                                 │
│  Schemas:  [public, person, sales_____________________]         │
│                                                                 │
│  [ ] Use SSL                                                    │
│  [ ] Use environment variables                                  │
│                                                                 │
├─────────────────────────────────────────────────────────────────┤
│  [T]est Connection   [S]ave   [C]ancel                         │
└─────────────────────────────────────────────────────────────────┘
```

### Exploring Schema

Select a data source and press Enter to explore its schema:

```
┌─────────────────────────────────────────────────────────────────┐
│  postgres - Schema Explorer                                     │
├─────────────────────────────────────────────────────────────────┤
│  Tables & Views                 │  Columns                      │
│  ────────────────               │  ───────                      │
│  ▸ person.person               │  businessentityid  int4   PK  │
│    person.address              │  persontype        bpchar     │
│    person.emailaddress         │  namestyle         bool       │
│    sales.customer              │  title             varchar    │
│    sales.salesorderheader      │  firstname         varchar ✓  │
│    sales.salesorderdetail      │  middlename        varchar    │
│    production.product          │  lastname          varchar ✓  │
│    production.productcategory  │  suffix            varchar    │
│    humanresources.employee     │  emailpromotion    int4       │
│    ...                         │  modifieddate      timestamp  │
│                                │                               │
│  [/] Search  [F] Filter        │  [T] Map Type  [U] Unmap      │
├─────────────────────────────────────────────────────────────────┤
│  ✓ = mapped to unified type                                     │
└─────────────────────────────────────────────────────────────────┘
```

### Creating Type Mappings

Press `T` on a column to map it to a unified type:

```
┌─────────────────────────────────────────────────────────────────┐
│  Map Column to Unified Type                                     │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Column: person.person.firstname (varchar 50)                   │
│                                                                 │
│  Select or create type:                                         │
│  ──────────────────────                                         │
│  ▸ FirstName         (existing - 3 matches)                     │
│    Name              (existing - 1 match)                       │
│    + Create new type "Firstname"                                │
│                                                                 │
│  Auto-suggestions based on column name:                         │
│  ──────────────────────────────────────                         │
│    Also match: first_name, fname, firstName                     │
│    [ ] Add patterns automatically                               │
│                                                                 │
├─────────────────────────────────────────────────────────────────┤
│  Enter Select   N New Type   Esc Cancel                        │
└─────────────────────────────────────────────────────────────────┘
```

### Auto-Discovery

The TUI can automatically suggest type mappings based on naming patterns:

```
┌─────────────────────────────────────────────────────────────────┐
│  Auto-Discover Type Mappings                                    │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Analyzing 3 sources, 47 tables, 312 columns...                 │
│                                                                 │
│  Suggested Types:                                               │
│  ────────────────                                               │
│                                                                 │
│  [✓] Email                                                      │
│      postgres: person.emailaddress.emailaddress                 │
│      postgres: sales.customer.emailaddress                      │
│      mariadb:  customers.email                                  │
│      api:      Customer.email, User.email                       │
│                                                                 │
│  [✓] FirstName                                                  │
│      postgres: person.person.firstname                          │
│      mariadb:  customers.first_name, employees.first_name       │
│      api:      Customer.firstName, Employee.firstName           │
│                                                                 │
│  [ ] Phone (needs review - mixed patterns)                      │
│      postgres: person.personphone.phonenumber                   │
│      mariadb:  customers.phone, customers.mobile                │
│      api:      Customer.phoneNumber                             │
│                                                                 │
│  [?] CreatedAt vs CreateDate (naming conflict)                  │
│      Choose: [CreatedAt] [CreateDate] [Both] [Skip]             │
│                                                                 │
├─────────────────────────────────────────────────────────────────┤
│  Space Toggle   A Accept All   R Review   Esc Cancel           │
└─────────────────────────────────────────────────────────────────┘
```

## CLI Commands

### Initialize Project

```bash
# Interactive initialization
typr init

# With options
typr init --lang java --json jackson --output ./generated
```

### Generate Code

```bash
# Generate from config file
typr generate

# Specify config file
typr generate --config typr-types.yaml

# Generate specific sources only
typr generate --sources postgres,api

# Watch mode - regenerate on file changes
typr generate --watch
```

### Validate Configuration

```bash
# Validate config and test connections
typr validate

# Verbose output
typr validate --verbose

# Check specific sources
typr validate --sources postgres
```

### Schema Commands

```bash
# List tables in a source
typr schema list postgres

# Show table details
typr schema show postgres person.person

# Export schema to JSON
typr schema export postgres --output schema.json

# Compare schemas between sources
typr schema diff postgres mariadb
```

### Type Commands

```bash
# List defined types
typr types list

# Show type details and matches
typr types show Email

# Add a new type interactively
typr types add

# Add type from command line
typr types add FirstName \
  --db-column "first_name,firstname" \
  --api-name "firstName"

# Find unmapped columns
typr types suggest

# Remove a type
typr types remove PhoneNumber
```

### Source Commands

```bash
# List configured sources
typr sources list

# Test source connection
typr sources test postgres

# Add source interactively
typr sources add

# Remove source
typr sources remove legacy-db
```

## Watch Mode

The watch mode automatically regenerates code when source files change:

```bash
typr generate --watch
```

```
Watching for changes...

[12:34:56] api/openapi.yaml changed
[12:34:56] Regenerating api...
[12:34:57] Generated 12 files in ./generated/api

[12:35:10] typr-types.yaml changed
[12:35:10] Regenerating all sources...
[12:35:12] Generated 156 files in ./generated

Press Ctrl+C to stop
```

## CI/CD Integration

### GitHub Actions

```yaml
name: Generate Types
on:
  push:
    paths:
      - 'api/**'
      - 'typr-types.yaml'

jobs:
  generate:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:16
        env:
          POSTGRES_PASSWORD: test
        ports:
          - 5432:5432

    steps:
      - uses: actions/checkout@v4

      - name: Install Typr
        run: |
          curl -L https://github.com/oyvindberg/typr/releases/latest/download/typr-cli -o typr
          chmod +x typr

      - name: Validate Configuration
        run: ./typr validate

      - name: Generate Code
        run: ./typr generate

      - name: Commit Generated Code
        run: |
          git add generated/
          git commit -m "chore: regenerate types" || exit 0
          git push
```

### Pre-commit Hook

```bash
#!/bin/bash
# .git/hooks/pre-commit

# Validate configuration before commit
typr validate --quiet || {
  echo "Typr validation failed. Please fix configuration."
  exit 1
}

# Regenerate and stage changes
typr generate
git add generated/
```

## Configuration Reference

See [YAML Configuration](./yaml-config.md) for complete configuration options.
