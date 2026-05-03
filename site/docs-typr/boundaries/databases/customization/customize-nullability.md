---
title: Customize nullability for parameters and columns
---

This is done inline in the SQL you write:

```sql
-- foo is nullable, by default pg cannot infer nullability
select 1 where :foo = 1;
-- foo is explicitly set to nullable
select 1 where :"foo?" = 1;
-- foo is explicitly set to not nullable
select 1 where :"foo!" = 1;
-- foo is explicitly set to not nullable and `Long`
select 1 where :"foo:Long!" = 1;
```

also works for columns, naturally:
```sql
select 1 as "foo:Double?" -- rewrite `Int` to `Option[Double]`
```

and if you mention a parameter multiple times you only need to customize it at first mention
```sql
select 1 where :"foo!" = 1 and :foo != 2;
```
