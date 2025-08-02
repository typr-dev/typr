---
title: Customize naming
---

You provide a `typo.Naming` instance in `typo.Options` when running typo.
This is responsible for computing all scala names based on names from PostgreSQL.

### Customize field names

As an example, say you you have some weird naming standard in your schemas, for instance `id_table` instead of `table_id`.
This is how it can be prettified in the generated scala code

```scala mdoc:silent
import typo.*

val optsCustomId = Options(
  pkg = "org.foo",
  dbLib = None,
  naming = pkg => new Naming(pkg) {
    override def field(name: db.ColName): sc.Ident = {
      val newName = if (name.value.startsWith("id_")) db.ColName(name.value.drop(3) + "_id") else name
      super.field(newName)
    }
  }
)
```
```scala mdoc
// this incantation demos the effect, you don't have to write this in your code
sc.renderTree(optsCustomId.naming(sc.QIdent(optsCustomId.pkg)).field(db.ColName("id_flaff")), Dialect.Scala3)
```

### Customize enum field names

Let's say you get a name clash between a string enum value and a typeclass instance name.
This is something which can happen currently

```scala mdoc:silent
val optsCustomEnum = Options(
  pkg = "org.foo",
  dbLib = None,
  naming = pkg => new Naming(pkg) {
    override def enumValue(name: String): sc.Ident =
      sc.Ident(if (name == "writes") "Writes" else name)
  }
)
```

```scala mdoc
// this incantation demos the effect, you don't have to write this in your code
sc.renderTree(optsCustomEnum.naming(sc.QIdent(optsCustomEnum.pkg)).enumValue("writes"), Dialect.Scala3)
```

