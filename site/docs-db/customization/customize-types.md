---
title: Customize column types
---

Note that use of this feature is in general **discouraged**, 
see warning in [user-selected type](../type-safety/user-selected-types.md).

If you're not happy with the types PostgreSQL and Typo has ended up with for a given column, you can override it.

This is referred to within Typo as a [user-selected type](../type-safety/user-selected-types.md).



```scala
import typr.{Options, TypeOverride, Dialect, TypeSupportScala}
import typr.internal.codegen.LangScala

val rewriteColumnTypes = TypeOverride.relation {
  case ("schema.table", "column") => "org.foo.ColumnId"
}

Options(
  pkg = "org.foo",
  lang = LangScala(Dialect.Scala3, TypeSupportScala),
  dbLib = None,
  typeOverride = rewriteColumnTypes
)
```

### More structured version

The version above is "simplified", in that is takes a descriptive type and explodes it into strings.
You may prefer the version below which is more cumbersome but more structured:

```scala
import typr.db.RelationName

val rewriteMore = TypeOverride.of { 
  case (RelationName(Some(schema), tableName), colName) if schema.contains("foo") && colName.value.startsWith("foo") => "org.foo.Bar" 
}
```

### Composing multiple column overrides:

```scala
rewriteColumnTypes.orElse(rewriteMore)
```
