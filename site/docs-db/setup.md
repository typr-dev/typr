---
title: Getting started
---

## Database library
Note that you're supposed to [bring your own database library](other-features/flexible.md). You choose either anorm, doobie or zio-jdbc in `Options` (see below),
and you need to have that added to your build as well.

## Getting started with DSL

If you want to use the [SQL DSL](what-is/dsl.md), you enable it by [customizing](customization/overview.md) Typo by setting `enableDsl = true`.

```scala
import typr.{Options, Dialect, TypeSupportScala}
import typr.internal.codegen.LangScala
Options(
  pkg = "mypkg",
  lang = LangScala(Dialect.Scala3, TypeSupportScala),
  dbLib = None,
  enableDsl = true
)
```

You also need to add a dependency to your build in that case, which varies by database library:
- for doobie: https://mvnrepository.com/artifact/dev.typr/typr-dsl-doobie
- for anorm: https://mvnrepository.com/artifact/dev.typr/typr-dsl-anorm
- for zio-jdbc: https://mvnrepository.com/artifact/dev.typr/typr-dsl-zio-jdbc

## example script

The Typo code generator is shipped as a library, the easiest way to get started is something like this scala-cli script:

put it in `gen-db.sc` and run `scala-cli gen-db.sc`

```scala
//
// remember to give the project a github star if you like it <3
//
//> using dep "dev.typr::typo:0.31.0"
//> using scala "3.4.2"

import typr.*
import typr.internal.codegen.LangScala

// adapt to your instance and credentials
val ds = TypoDataSource.hikari(
  server = "localhost",
  port = 6432,
  databaseName = "Adventureworks",
  username = "postgres",
  password = "password"
)

val options = Options(
  // customize package name for generated code
  pkg = "org.foo.generated",
  // choose language and dialect for generated code
  lang = LangScala(Dialect.Scala3, TypeSupportScala),
  // pick your database library
  dbLib = Some(DbLibName.Anorm),
  jsonLibs = Nil,
  // many more possibilities for customization here
  // ...
)

// current folder, where you run the script from
val location = java.nio.file.Path.of(sys.props("user.dir"))

// destination folder. All files in this dir will be overwritten!
val targetDir = location.resolve("myproject/src/main/scala/org/foo/generated")
val testTargetDir = location.resolve("myproject/src/test/scala/org/foo/generated")

// where Typo will look for sql files
val scriptsFolder = location.resolve("sql")

// you can use this to customize which relations you want to generate code for, see below
val selector = Selector.ExcludePostgresInternal

generateFromDb(
  ds,
  options,
  targetFolder = targetDir,
  testTargetFolder = Some(testTargetDir),
  selector = selector,
  scriptsPaths = List(scriptsFolder)
).foreach(_.overwriteFolder())

// add changed files to git, so you can keep them under control
//scala.sys.process.Process(List("git", "add", targetDir.toString)).!!
```

## Compiling the generated code

For **Scala 2** you need to set add `-Xsource:3` to `scalacOptions`, with a recent version of Scala 2.13.
For **Scala 3** you'll need 3.3.0 or later.

## `selector`
You can customize which relations you generate code for, see [customize selected relations](customization/customize-selected-relations.md)

## `ProjectGraph`
If you want to split the generated code across multiple projects in your build, have a look at [Generate code into multiple projects](other-features/generate-into-multiple-projects.md)

## sbt plugin
It's natural to think an sbt plugin would be a good match for Typo. This will likely be added in the future.
