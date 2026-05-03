---
title: Testing with Stubs
---

import ShowcaseSnippet from '@site/src/components/ShowcaseSnippet';

It can be incredibly tiring to write tests for the database layer.

Often you want to split your code into pure/effectful parts and just test the pure parts,
but sometimes you want to observe mutations in the database as well.

Sometimes spinning up a real database for this is the right answer, sometimes it's not.
It is always slow, however, so it's way easier to get a fast test suite if you're not doing it.

The argument for the approach taken by Typr is that since the interaction between your code
and the database is guaranteed to be correct*, it is less important to back your tests with a real database.

This leads us to stubs (called mocks in the generated code), implementations of the repository
interfaces backed by a mutable `Map`. This can be generated for all tables with a primary key.

## Generated RepoMock

For every repository, Typr generates a mock implementation:

<ShowcaseSnippet file="employee/EmployeeRepoMock" />

## DSL Support

These mocks work with the [DSL](../what-is/dsl.md), which lets you describe semi-complex joins, updates, where predicates,
string operations and so on in your code, and test it in-memory!

## Note

Typr guarantees schema correctness, but you can still break constraints.
Or your tests need more advanced database functionality.

Stubs are obviously not a full replacement, but if they can be used for some non-zero percentage
of your tests it's still very beneficial!
