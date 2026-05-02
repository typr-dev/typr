---
title: Open Enums
---

import ShowcaseSnippet from '@site/src/components/ShowcaseSnippet';

# Open Enums

Open enums use lookup tables with foreign keys to encode enum values. This pattern works across all databases, making it a portable alternative to native enum types.

## When to Use Open Enums

- Your database doesn't support native enums (Oracle, SQL Server)
- You need to add new enum values without regenerating code
- You want forwards compatibility with unknown values
- You need referential integrity enforced by the database

## Schema Pattern

```sql
CREATE TABLE title (code TEXT PRIMARY KEY);
INSERT INTO title (code) VALUES ('mr'), ('ms'), ('dr'), ('phd');

CREATE TABLE titled_person (
    title TEXT NOT NULL REFERENCES title,
    name  TEXT NOT NULL
);
```

## Configuration

Configure Typr to generate open enums:

```scala
val options = Options(
  // ...
  openEnums = Selector.relationNames("title")
)
```

## Generated Code

Typr generates an "open" enum type that accepts unknown values:

<ShowcaseSnippet file="title/TitleId" />

The key difference from [native enums](/typr/boundaries/databases/type-safety/enums): the `Unknown` case handles values not present at code generation time.

## Supported Data Types

Open enums support:
- `TEXT` / `VARCHAR` columns
- Domain types with `TEXT` as the base type
