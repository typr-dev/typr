---
title: Customize naming
---

You provide a `typr.Naming` instance in `typr.Options` when running typo.
This is responsible for computing all scala names based on names from PostgreSQL.

### Customize field names

As an example, say you you have some weird naming standard in your schemas, for instance `id_table` instead of `table_id`.
This is how it can be prettified in the generated scala code

```scala
import typr.*
import typr.internal.codegen.LangScala

val lang = LangScala(Dialect.Scala3, TypeSupportScala)

val optsCustomId = Options(
  pkg = "org.foo",
  lang = lang,
  dbLib = None,
  naming = (pkg, lang) => new Naming(pkg, lang) {
    override def field(name: db.ColName): jvm.Ident = {
      val newName = if (name.value.startsWith("id_")) db.ColName(name.value.drop(3) + "_id") else name
      super.field(newName)
    }
  }
)
```
```scala
// this incantation demos the effect, you don't have to write this in your code
lang.renderTree(optsCustomId.naming(jvm.QIdent(optsCustomId.pkg), lang).field(db.ColName("id_flaff")), lang.Ctx.Empty)
// res0: Code = Str(value = "flaffId")
```

### Customize enum field names

Let's say you get a name clash between a string enum value and a typeclass instance name.
This is something which can happen currently

```scala
val optsCustomEnum = Options(
  pkg = "org.foo",
  lang = lang,
  dbLib = None,
  naming = (pkg, lang) => new Naming(pkg, lang) {
    override def enumValue(name: String): jvm.Ident =
      jvm.Ident(if (name == "writes") "Writes" else name)
  }
)
```

```scala
// this incantation demos the effect, you don't have to write this in your code
lang.renderTree(optsCustomEnum.naming(jvm.QIdent(optsCustomEnum.pkg), lang).enumValue("writes"), lang.Ctx.Empty)
// res1: Code = Str(value = "Writes")
```

