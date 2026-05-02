---
title: "Patterns: Dynamic queries"
---

When using [SQL files](../what-is/sql-is-king.md), you often want queries with optional filters.

## Optional parameter pattern

Use the `?` suffix to mark a parameter as optional, combined with `IS NULL` fallback:

```sql
SELECT p.title, p.firstname, p.middlename, p.lastname
FROM person.person p
WHERE :"first_name:text?" = p.firstname OR :first_name IS NULL
```

This generates a repository method with an optional parameter:

```java
// Java
List<PersonDynamicSqlRow> apply(Optional<String> firstName, Connection c);
```

```kotlin
// Kotlin
fun apply(firstName: String?, c: Connection): List<PersonDynamicSqlRow>
```

```scala
// Scala
def apply(firstName: Option[String])(using Connection): List[PersonDynamicSqlRow]
```

When `firstName` is `None`/`null`/`Optional.empty`, the `IS NULL` branch matches all rows.

## Limitations

You can only use optional parameters for values that are templated as SQL parameters.
It's not possible to dynamically change keywords, table names, or column names.
