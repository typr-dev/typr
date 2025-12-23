---
title: Customize selected relations
---


You typically have many more relations in your database than you want to expose to application code.
Maybe you're generating code for just a part of the system, not the whole thing.

Typo has a mechanism by which you can choose which relations to generate code for.

among the arguments to `generateFromDb` is `selector`, which by default picks all relations except those in the postgres schemas.
See [Selector](selector.md)

```scala
import typr.*

generateFromDb(options, selector = Selector.ExcludePostgresInternal)
```


## Transitive relations

So in Typo we say that relations have dependencies, see [flow typing](../type-safety/type-flow.md). 

Say you have some [sql files](../what-is/sql-is-king.md) and have chosen some relations, and some of those have dependencies on other relations.
Typo can optionally generate code for these dependencies as well. 

If you want that, you can  [customize](overview.md) Typo and set the `keepDependencies` parameter to `true` to generate code for those dependencies as well. 

`keepDependencies` is set to `false` by default. If it's left at `false`, you'll only see the [primary key types](../type-safety/id-types.md) for those relations
