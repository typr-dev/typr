---
title: Primary Key Types
---

import ShowcaseSnippet from '@site/src/components/ShowcaseSnippet';

# Primary Key Types

For every table with a primary key, Typr generates a distinct ID type. This prevents mixing up IDs from different tables—a common source of bugs.

## Simple Primary Keys

A table with a single-column primary key gets a wrapper type:

<ShowcaseSnippet file="company/CompanyId" />

The wrapper ensures you can't accidentally pass a `CustomerId` where a `CompanyId` is expected. The compiler catches these mistakes.

## Composite Primary Keys

Tables with multi-column primary keys get a composite ID type:

<ShowcaseSnippet file="project_assignment/ProjectAssignmentId" />

The composite type bundles all key columns together, making it easy to pass around and use in lookups.

## Using ID Types

ID types integrate with repositories for type-safe lookups:

<ShowcaseSnippet
  file="company/CompanyRepo"
  fromByLang={{ java: "Optional<CompanyRow> selectById", kotlin: "abstract fun selectById", scala: "def selectById" }}
  toByLang={{ java: "List<CompanyRow> selectByIds", kotlin: "abstract fun selectByIds", scala: "def selectByIds" }}
/>

## Disabling ID Types

If you don't want wrapper types for certain tables, configure `enablePrimaryKeyType`:

```java
Options options = new Options(
    "mypkg",
    Lang.Java,
    // ... other options
    Selector.relationNames("legacy_table"), // Only generate ID types for this table
);
```

Use `Selector.All` (default) for all tables, `Selector.None` to disable, or `Selector.relationNames(...)` for specific tables.

Composite ID types are always generated regardless of this setting.
