---
title: "Patterns: The multi-repo"
---

import UsageExample from '@site/src/components/UsageExample';

Typr generates one repository per table. But often you need to coordinate multiple tables in a transaction.

Enter the **multi-repo pattern**: take low-level Typr repositories as parameters, and write the higher-level flow yourself.

## Example

<UsageExample file="patterns/MultiRepoExample" />

## Benefits

You still get huge benefits from using Typr:

- All of this is **type-safe**
- You get perfect **auto-complete** from your IDE
- Strongly typed [Id types](../type-safety/id-types.md) and [type flow](../type-safety/type-flow.md) ensure that you have to follow foreign keys correctly
- It's testable! You can wire in [mock repositories](../other-features/testing-with-stubs.md) and test without a running database

## Isn't this a service at this point?

Maybe! You likely shouldn't use the generated `Row` types at the service level, and there should likely be a transaction boundary.
You get to decide that, however.
