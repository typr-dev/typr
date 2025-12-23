---
title: Primary key types
---

For every table with primary keys, a corresponding id type is created.

```scala 
case class PgNamespaceId(value: /* oid */ Long) extends AnyVal
object PgNamespaceId {
  // ... instances
}
```

## Composite keys
Composite keys are also supported, and the id type is a product type in that case.  

```scala mdoc
import adventureworks.customtypes.TypoLocalDate
import adventureworks.humanresources.employeedepartmenthistory.EmployeedepartmenthistoryId
import adventureworks.humanresources.department.DepartmentId
import adventureworks.humanresources.shift.ShiftId
import adventureworks.person.businessentity.BusinessentityId

/** Type for the composite primary key of table `humanresources.employeedepartmenthistory` */
case class EmployeedepartmenthistoryId(
    businessentityid: BusinessentityId, 
    startdate: TypoLocalDate, 
    departmentid: DepartmentId, 
    shiftid: ShiftId
)
object EmployeedepartmenthistoryId {
  // ...instances
}
```

## I don't want these

if you have some tables where you don't want the type-safety this brings, you can [customize](../customization/overview.md)
code generation by tweaking `Options#enablePrimaryKeyType`:

```scala mdoc:silent
import typr.*
import typr.internal.codegen.LangScala

val options = Options(
  pkg = "mypkg",
  lang = LangScala.scalaDsl(Dialect.Scala3, TypeSupportScala),
  dbLib = Some(DbLibName.Doobie),
  enablePrimaryKeyType = Selector.relationNames("myrelationname"),
)
```

Composite id key types are currently always created.
