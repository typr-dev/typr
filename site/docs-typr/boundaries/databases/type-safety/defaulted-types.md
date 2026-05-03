---
title: Defaulted Types
---

import ShowcaseSnippet from '@site/src/components/ShowcaseSnippet';

# Defaulted Types

When inserting rows, some columns have database defaults (auto-increment IDs, timestamps, etc.). The `Defaulted` type lets you explicitly choose: provide a value, or use the database default.

## The Defaulted Type

Typr generates a `Defaulted<T>` type in your project:

<ShowcaseSnippet file="customtypes/Defaulted" toByLang={{ java: "static", kotlin: "abstract fun", scala: "object Defaulted" }} />

- `Provided(value)` - Use this specific value
- `UseDefault` - Let the database generate the value

## Unsaved Row Types

For tables with default columns, Typr generates an "Unsaved" row type:

<ShowcaseSnippet
  file="company/CompanyRowUnsaved"
  toByLang={{ java: "public CompanyRowUnsaved with", kotlin: "fun toRow", scala: "def toRow" }}
/>

Notice how `active` and `createdAt` are wrapped in `Defaulted`—they have database defaults. The `id`, `name`, and `foundedYear` columns don't have defaults, so they're required.

## Using Defaulted

Insert with defaults:

```java
// Java - use database defaults
var unsaved = new CompanyRowUnsaved(
    new CompanyId(1),
    "Acme Corp"
);
CompanyRow saved = repo.insert(unsaved, conn);
// saved.active() will be true (the database default)
// saved.createdAt() will be the current timestamp
```

```kotlin
// Kotlin - use database defaults
val unsaved = CompanyRowUnsaved(
    id = CompanyId(1),
    name = "Acme Corp"
)
val saved = repo.insert(unsaved, conn)
```

Override a default:

```java
// Java - provide a specific value
var unsaved = new CompanyRowUnsaved(
    new CompanyId(1),
    "Acme Corp",
    Optional.empty(),
    new Defaulted.Provided<>(Optional.of(false)),  // Override active
    new Defaulted.UseDefault<>()                    // Use default for createdAt
);
```

## Repository Methods

Repositories accept both regular rows and unsaved rows:

<ShowcaseSnippet
  file="company/CompanyRepo"
  fromByLang={{ java: "CompanyRow insert(", kotlin: "abstract fun insert(", scala: "def insert(" }}
  toByLang={{ java: "SelectBuilder", kotlin: "abstract fun select():", scala: "def select:" }}
/>
