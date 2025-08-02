---
title: Generate code into multiple projects
---

It's very common that you don't want to expose all the tables in one place in your system. 
You also don't want duplication of generated code or more than one script to generate database code. 

The solution is to pass a `ProjectGraph` structure to `generateFromDb` instead, 
in which you encode the structure of the (relevant) projects in your build.

Dependencies between projects are encoded by nesting in the tree you pass (each node has a `downstream` member), 
with the root being the uppermost one. 

If multiple downstream projects want to generate the same code, it'll be pulled up to the level necessary to become visible for all of them.

sample:
```scala mdoc:silent
import typo.*
import java.nio.file.Path
import java.sql.Connection

def generate(ds: TypoDataSource): String = {
  val cwd: Path = Path.of(sys.props("user.dir"))

  val options = Options(
      pkg = "org.mypkg",
      jsonLibs = Nil,
      dbLib = Some(DbLibName.ZioJdbc)
    )
  
  val generated = generateFromDb(
    ds,
    options,
    // setup a project graph. this outer-most project is the root project.
    // if multiple downstream projects need the same relation, it'll be pulled up until it's visible for all.
    // in this simple example it means `a.bicycle` will be pulled up here
    ProjectGraph(
      name = "a",
      target = cwd.resolve("a/src/main/typo"),
      testTarget = None,
      value = Selector.None,
      scripts = Nil,
      downstream = List(
        ProjectGraph(
          name = "b",
          target = cwd.resolve("b/src/main/typo"),
          testTarget = None,
          value = Selector.fullRelationNames(
            "a.bicycle",
            "b.person"
          ),
          // where to find sql files
          scripts = List(cwd.resolve("b/src/main/sql")),
          downstream = Nil
        ),
        ProjectGraph(
          name = "c",
          target = cwd.resolve("b/src/main/typo"),
          testTarget = None,
          value = Selector.fullRelationNames(
            "a.bicycle",
            "c.animal"
          ),
          scripts = List(cwd.resolve("b/src/main/sql")),
          downstream = Nil
        )
      )
    )
  )

  generated.foreach(_.overwriteFolder(options.dialect))

  import scala.sys.process.*

  (List("git", "add") ++ generated.map(_.folder.toString)).!!
}
```

### todo:

- [ ] `testInsert` (we'll need one for each project)
- [ ] docs
- [ ] tests