---
title: Customizing Typo
---

All customizations are done through the `typr.Options` object passed to typo:

```scala
import typr.*
import typr.internal.codegen.LangScala

val options = Options(
  pkg = "org.foo",
  lang = LangScala(Dialect.Scala3, TypeSupportScala),
  jsonLibs = List(JsonLibName.PlayJson),
  dbLib = Some(DbLibName.Anorm),
  // .. more options here
)

```

## All options

| Field Name               | Effect                                                                                                                                                                                                        |
|--------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `pkg`                    | Specifies the package name for the generated code.                                                                                                                                                            |
| `dbLib`                  | Defines the database library to use for code generation (anorm, doobie, zio-jdbc or `None`).                                                                                                                  |
| `jsonLibs`               | JSON libraries to generate codecs for (default is empty).                                                                                                                                                     |
| `silentBanner`           | Controls whether to suppress the Typo banner while running Typo (default is `false`).                                                                                                                         |
| `fileHeader`             | Sets the header text that appears at the top of generated files.                                                                                                                                              |
| `naming`                 | Configures naming conventions for generated code. See section below                                                                                                                                           |
| `typeOverride`           | Defines type overrides for specific database types See section below.                                                                                                                                         |
| `nullabilityOverride`    | Defines nullability overrides for specific columns See section below.                                                                                                                                         |
| `generateMockRepos`      | Specifies which repositories to generate mock versions for (default is all).                                                                                                                                  |
| `enableFieldValue`       | Controls whether to enable `FieldValue` code generation for specific repositories (default is disabled).                                                                                                      |
| `enableStreamingInserts` | Controls whether to enable [streaming inserts](../other-features/streaming-inserts.md)                                                                                                                        |
| `enableTestInserts`      | Controls whether to enable [test inserts](../other-features/testing-with-random-values.md) for specific repositories (default is none).                                                                       |
| `enablePrimaryKeyType`   | Controls whether to enable [primary key types](../type-safety/id-types.md) for specific repositories (default is all).                                                                                        |
| `readonlyRepo`           | Specifies whether to generate read-only repositories for specific repositories. Useful when you're working on a part of the system where you only consume certain tables. (default is `false` - all mutable). |
| `enableDsl`              | Enables the [SQL DSL](../what-is/dsl.md) for code generation (default is `false`).                                                                                                                            |
| `keepDependencies`       | Specifies whether to generate [table dependencies](../type-safety/type-flow.md) in generated code even if you didn't select them (default is `false`).                                                        |
| `rewriteDatabase`        | Let's you perform arbitrary rewrites of database schema snapshot. you can add/remove rows, foreign keys and so on.                                                                                            |
| `openEnums`              | Controls if you want to tag tables ids as [open string enums](../type-safety/open-string-enums.md)                                                                                                            |
| `dialect`                | Controls the Scala syntax for implicit parameters and instances: `Dialect.Scala2XSource3` (default) generates `implicit` for Scala 2.12/2.13 and shared Scala 3 code, while `Dialect.Scala3` generates `using`/`given` for Scala 3-only code. You must choose the appropriate dialect based on your target Scala version. |

## Database Libraries

Typo supports multiple Scala database libraries, each with specific optimizations:

- **Anorm** (`DbLibName.Anorm`) - Lightweight SQL parser for Play Framework
- **Doobie** (`DbLibName.Doobie`) - Functional JDBC layer for Cats Effect
- **ZIO-JDBC** (`DbLibName.ZioJdbc`) - Type-safe JDBC wrapper for ZIO

Each library generates code optimized for that specific ecosystem, including appropriate return types, error handling,
and integration patterns.

```scala
val options = Options(
  pkg = "myapp.db",
  dbLib = Some(DbLibName.Anorm), // Choose your library
  // ... other options
)
```

## Scala Version Dialect

Typo can generate code for different Scala versions using the `dialect` option:

- **`Dialect.Scala2XSource3`** (default): Generates code using `implicit` syntax that works with Scala 2.12, 2.13, and Scala 3 (in Scala 2 compatibility mode)
- **`Dialect.Scala3`**: Generates code using Scala 3's `using`/`given` syntax for cleaner, more modern code

```scala
// For Scala 2.12/2.13 or mixed Scala 2/3 projects
val options = Options(
  pkg = "myapp.db",
  dbLib = Some(DbLibName.Anorm),
  dialect = Dialect.Scala2XSource3  // This is the default
)

// For Scala 3-only projects
val options = Options(
  pkg = "myapp.db", 
  dbLib = Some(DbLibName.Anorm),
  dialect = Dialect.Scala3
)
```

Choose `Dialect.Scala3` only if your entire codebase is on Scala 3 and you want to use the modern syntax. Otherwise, stick with the default `Dialect.Scala2XSource3` for maximum compatibility.

## Development options

| Field Name        | Effect                                                                                                  |
|-------------------|---------------------------------------------------------------------------------------------------------|
| `debugTypes`      | Enables debug mode for types during code generation (default is `false`).                               |
| `inlineImplicits` | Controls whether to inline implicits for generated code (default is `true`).                            |
| `logger`          | Specifies the logger to use for logging during code generation (default is console logging). Useful for |



