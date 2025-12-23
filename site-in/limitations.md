---
title: Limitations
---

### Stability 

the interface of the generated code is not final yet. You can keep using a particular version of Typo
~forever, but if you want to upgrade to the newest version you need to expect at least some API churn.

### Correctness

Typo tries to make a guarantee that all generated code will not fail with a type error.
You can still break [constraints](other-features/constraints.md) naturally, but the types should align.

Typo is close to delivering on this, but JDBC makes it *incredibly* difficult to get all the details exactly right.
In other words, let's call it a strong tendency to get it right, for now :)

#### Nullability 

In particular, nullability in columns returned by sql files and views is something which may never be _entirely_ right.
This is mainly due to PostgreSQL being inexact when inferring nullability for arbitrary sql.

The pgtyped project has a metaissue which collects type inference issues in PostgreSQL [here](https://github.com/adelsz/pgtyped/issues/375).

Some effort has been made in Typo to get a better source of nullability by looking at `explain` plans, 
see [#27](https://github.com/oyvindberg/typr/pull/27).


