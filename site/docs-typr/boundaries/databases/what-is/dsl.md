---
title: SQL DSL
---


The Typr SQL DSL, an optional yet strongly recommended SQL DSL, can be an invaluable addition to your Scala toolbox,
offering you a lot of convenience:

**Simplicity**:
It's simple. Both for the developer, and for the compiler!

Neither aggregation nor projection is allowed — you always work on whole rows.
On the other hand, you get predicates, joins, ordering, pagination.

It's designed to cover the "I just need to fetch/update this data" scenario, without requiring you to break flow by creating an sql file.
Whenever you need more flexibility, you should reach for [SQL files](sql-is-king.md)

**Type Safety**:
With the Typr DSL, you benefit from Scala's robust type system, ensuring that your queries are
syntactically correct at compile time.
Say goodbye to runtime errors caused by mismatched column names or data types.

**Readability and Maintainability**:
The Typr DSL promotes clean, expressive code, making it easier to understand and maintain your database queries.
No more wrestling with long, convoluted SQL strings scattered throughout your codebase.

**Tooling Integration**: Leverage the power of IDEs and code editors for autocomplete, refactoring, and error checking,
as
the Typr DSL seamlessly integrates with IDEs. This significantly boosts developer productivity.



## Select DSL

Some features

- Type-safe
- Generates not-too-terrible SQL
- Arbitrarily deep joins
- Order by
- Expresses a rich set of predicates (sql operators, functions, string functions, comparisons, etc)
- Some functions defined for string and array types
- Set of operators and functions are extendable by user
- Works both when backed by [in-memory stubs](../other-features/testing-with-stubs.md) and by PostgreSQL

<video
width="100%"
controls
src="https://user-images.githubusercontent.com/247937/257662719-2a295f48-7cd2-4c49-b043-90bc8511de67.mp4"
/>

## Update DSL

Can express batch updates, where you `set` arbitrary number of columns with an arbitrary number of (implicitly `AND`ed)
predicates.

Column values can be computed from the original value in the column or from the entire row, as seen below

<video
width="100%"
controls
src="https://user-images.githubusercontent.com/247937/257148737-7b32df2c-af54-4397-85d3-eab863179d78.mp4"
/>

## Delete DSL

There is also a `delete` DSL, similar to `select` and `update`. It has no video yet, unfortunately.

## Further reading

- [DSL In-Depth Guide](../other-features/dsl-in-depth.md) for comprehensive coverage of all DSL features with examples
- [Getting started](../setup.md) for some information about how to set up the DSL.
- [Limitations](../limitations.md) for a caveat on how PostgreSQL infers nullability.
- [Customize sql files](../customization/customize-sql-files.md) for how to override parameter/column names, types and nullability
- [Dynamic queries](../patterns/dynamic-queries.md) for how to introduce some amount of dynamism in your queries

## Example usage:




```scala
productRepo.select
  .where(_.`class` === "H ")
  .where(x => x.daystomanufacture > 25 or x.daystomanufacture <= 0)
  .where(x => x.productline === "foo")
  .join(unitmeasureRepo.select.where(_.name.like("name%")))
  .on { case (p, um) => p.sizeunitmeasurecode === um.unitmeasurecode }
  .join(projectModelRepo.select)
  .leftOn { case ((product, _), productModel) => product.productmodelid === productModel.productmodelid }
  .where { case ((product, _), productModel) => product.productmodelid === productModel.productmodelid }
  .orderBy { case ((product, _), _) => product.productmodelid.asc }
  .orderBy { case ((_, _), productModel) => productModel.name.desc.withNullsFirst }
  .toList
// res1: List[Tuple2[Tuple2[ProductRow, UnitmeasureRow], Option[ProductmodelRow]]] = List(
// )
```
