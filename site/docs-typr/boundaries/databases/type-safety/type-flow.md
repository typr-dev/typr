---
title: Type Flow
---

import ShowcaseSnippet from '@site/src/components/ShowcaseSnippet';

# Type Flow

Typr follows dependencies between columns (foreign keys and view dependencies) so that types "flow" from the base column to other tables that reference it. This makes it easy to work with related data using the correct types.

## How It Works

When table B has a foreign key to table A's primary key, Typr:
1. Uses the same ID type in both places (e.g., `EmployeeId`)
2. Adds documentation linking to the source column
3. Ensures type safety across the relationship

## Example: Foreign Key Types

The `project_assignment` table has foreign keys to both `employee` and `project`:

<ShowcaseSnippet
  file="project_assignment/ProjectAssignmentRow"
  toByLang={{ java: "public ProjectAssignmentRowUnsaved toUnsavedRow", kotlin: "fun toUnsavedRow", scala: "def toUnsavedRow" }}
/>

Notice how:
- `employeeId` has type `EmployeeId` (not just `String`)
- `projectId` has type `ProjectId` (not just `String`)
- Documentation points to the source table

## Composite Primary Keys

Tables with composite primary keys get a composite ID type that combines the key columns:

<ShowcaseSnippet file="project_assignment/ProjectAssignmentId" />

The composite ID preserves the flowed types - `EmployeeId` and `ProjectId` remain distinct even within the composite.

## Benefits

1. **Compile-time safety**: Can't accidentally pass an `EmployeeId` where a `ProjectId` is expected
2. **IDE navigation**: Click-through from foreign key to source table
3. **Self-documenting**: Code shows relationships clearly
4. **Refactoring confidence**: Changing a type reveals all affected code
