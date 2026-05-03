---
title: DSL In-Depth Guide
---

# Typr DSL In-Depth Guide

This guide provides comprehensive coverage of Typr's SQL DSL, including all its features and practical examples.


## Basic Selects

The DSL provides a type-safe way to query your database. Every repository has a `select` method that returns a `SelectBuilder`:

```scala
// Simple select all
val allProducts: List[ProductRow] = productRepo.select.toList

// Select specific rows
val product: Option[ProductRow] = productRepo.selectById(ProductId(1))

// Select by multiple IDs
val products: List[ProductRow] = productRepo.selectByIds(Array(ProductId(1), ProductId(2)))
```

## Where Clauses

The `where` method allows you to filter results using type-safe predicates.

**Note**: Consecutive calls to `where`, `orderBy`, and other query methods create an implicit **AND** operation. Multiple `where` clauses are combined with AND logic, and multiple `orderBy` clauses create a compound sort order.

```scala
// Simple equality
productRepo.select
  .where(_.name === Name("Mountain Bike"))
  .toList

// Multiple conditions (implicitly ANDed)
productRepo.select
  .where(_.color === "Red")
  .where(_.listprice > BigDecimal(1000))
  .where(_.daystomanufacture >= 5)
  .toList

// OR conditions
productRepo.select
  .where(p => (p.color === "Red").or(p.color === "Blue"))
  .toList

// Complex predicates
productRepo.select
  .where(p => p.listprice > BigDecimal(100) and p.listprice < BigDecimal(500))
  .where(_.name.like("Mountain%"))
  .where(_.discontinueddate.isNull)
  .toList

// IN clause with arrays
productRepo.select
  .where(p => p.productid.in(Array(ProductId(1), ProductId(22))))
  .toList

// Complex boolean logic
productRepo.select
  .where(x => (x.daystomanufacture > 25).or(x.daystomanufacture <= 0))
  .toList
```

### String Operations

```scala
// LIKE patterns
productRepo.select.where(_.name.like("Mountain%"))
productRepo.select.where(_.name.like("%Bike%"))

// NOT LIKE
productRepo.select.where(p => !p.name.like("Road%"))

// String length
productRepo.select.where(_.name.strLength > 10)

// String concatenation
productRepo.select.where(p => (p.name.underlying || " - " || p.productnumber).like("%Special%"))
```

### Null Handling

The DSL tracks nullability through the type system:

```scala
// Check for NULL
productRepo.select.where(_.color.isNull)
productRepo.select.where(p => !p.color.isNull)

// COALESCE
productRepo.select.where(p => p.color.coalesce("Unknown") === "Red")

// Working with non-nullable fields
productRepo.select.where(_.makeflag === Flag(true))
```

**Note**: There's also a `whereStrict` variant that requires non-nullable predicates. This can be useful when you want to ensure at compile time that your predicate cannot be null, which helps when dealing with PostgreSQL's nullability semantics. Use regular `where` for most cases.

## Order By

Sort results using the `orderBy` method:

```scala
// Simple ordering
productRepo.select
  .orderBy(_.name.asc)
  .toList

// Multiple sort criteria
productRepo.select
  .orderBy(_.listprice.desc)
  .orderBy(_.name.asc)
  .toList

// With null handling
productRepo.select
  .orderBy(_.color.desc.withNullsFirst)
  .orderBy(_.modifieddate.asc.withNullsFirst)
  .toList
```

## Joins

Typr supports various join types with type-safe predicates.

### Inner Joins

```scala
// Simple inner join
val joinedData = productRepo.select
  .join(unitmeasureRepo.select)
  .on { case (product, unitmeasure) =>
    product.sizeunitmeasurecode === unitmeasure.unitmeasurecode
  }
  .toList
```

### Left Joins

Left joins return `Option[Row]` for the right side:

```scala
// Left join - note the Option type for the right side
val leftJoined: List[(ProductRow, Option[ProductmodelRow])] =
  productRepo.select
    .join(productmodelRepo.select)
    .leftOn { case (product, productmodel) =>
      product.productmodelid === productmodel.productmodelid
    }
    .toList

// Accessing fields from the optional side
leftJoined.foreach { case (product, maybeModel) =>
  println(s"Product: ${product.name}")
  maybeModel.foreach(model => println(s"  Model: ${model.name}"))
}
```

### Multiple Joins

When performing multiple joins, the result builds up as nested tuples:

```scala
// Multiple joins create nested tuples: ((a, b), c)
val multiJoined = productRepo.select
  .join(productmodelRepo.select)
  .on { case (p, pm) => p.productmodelid === pm.productmodelid }
  .join(productsubcategoryRepo.select)
  .on { case ((p, _), ps) => p.productsubcategoryid === ps.productsubcategoryid }
  .join(productcategoryRepo.select)
  .on { case (((_, _), ps), pc) => ps.productcategoryid === pc.productcategoryid }
  .toList
```

### Foreign Key Joins

Typr provides a convenient `joinFk` method that leverages foreign key relationships:

```scala
// Using foreign key relationships
val fkJoined = productRepo.select
  .joinFk(_.fkProductmodel)(productmodelRepo.select)
  .joinFk(_._1.fkProductsubcategory)(productsubcategoryRepo.select)
  .joinFk(_._2.fkProductcategory)(productcategoryRepo.select)
  .toList
```

### Handling Optionality in Left Joins

When working with left joins, you access fields from the optional side directly. The fields are still accessible even though the row result will be optional:

```scala
val leftJoinWithFilter = productRepo.select
  .join(productmodelRepo.select)
  .leftOn { case (p, pm) => p.productmodelid === pm.productmodelid }
  .where { case (product, productModel) =>
    // Access fields directly from the fields object
    productModel.productmodelid === product.productmodelid
  }
  .orderBy { case (_, productModel) =>
    // Ordering on nullable field from left join
    productModel.name.desc.withNullsFirst
  }
  .toList
```

## Tuple Syntax with ~

Typr provides an alternative syntax for working with tuples using the `~` operator:

```scala
// Instead of nested tuples ((a, b), c), you can use a ~ b ~ c
val query = productRepo.select
  .join(productmodelRepo.select)
  .on { case (p, pm) => p.productmodelid === pm.productmodelid }
  .join(productsubcategoryRepo.select)
  .on { case (p ~ pm, ps) => p.productsubcategoryid === ps.productsubcategoryid }
  .toList

// Pattern matching with ~
query.foreach {
  case product ~ productModel ~ productSubcategory =>
    println(s"${product.name} - ${productModel.name} - ${productSubcategory.name}")
}
```

## Printing SQL

You can inspect the generated SQL for any query:

```scala
val query = productRepo.select
  .where(_.listprice > BigDecimal(100))
  .join(productmodelRepo.select)
  .on { case (p, pm) => p.productmodelid === pm.productmodelid }
  .orderBy { case (p, _) => p.name.asc }

// Print the SQL that will be executed
query.sql.foreach(println)
```

## Limit and Offset

```scala
// Get first 10 products
productRepo.select
  .orderBy(_.name.asc)
  .limit(10)
  .toList

// Pagination
productRepo.select
  .orderBy(_.productid.asc)
  .offset(20)
  .limit(10)
  .toList
```

## Update DSL

The update DSL allows batch updates with type-safe predicates. Like other DSL operations, multiple calls to `setValue`, `setComputedValue`, and `where` methods are combined together - all set operations are applied to the same UPDATE statement, and multiple where clauses are ANDed together.

```scala
// Simple update
val updated = productRepo.update
  .setValue(_.listprice)(BigDecimal(99.99))
  .setValue(_.modifieddate)(TypoLocalDateTime.now)
  .where(_.productid === ProductId(1))
  .execute()

// Update with computed values from the column
productRepo.update
  .setComputedValue(_.listprice)(price => price * BigDecimal(1.1)) // 10% increase
  .setComputedValue(_.reorderpoint)(_ + TypoShort(22))
  .where(_.productsubcategoryid === ProductsubcategoryId(1))
  .execute()

// Complex computed values with string operations
val update = productRepo.update
  .setComputedValue(_.name)(p => (p.reverse.upper || Name("flaff")).substring(2, 4))
  .setValue(_.listprice)(BigDecimal(2))
  .setComputedValue(_.reorderpoint)(_ + TypoShort(22))
  .setComputedValue(_.sizeunitmeasurecode)(_ => testUnitmeasure.unitmeasurecode)
  .where(_.productid === testProduct.productid)

// Return updated rows
val updatedRows: List[ProductRow] = update.executeReturnChanged()

// Print the SQL that will be executed (with RETURNING clause)
update.sql(returning = true).foreach(println)
```

## Delete DSL

```scala
// Delete with predicate
val deleted = productRepo.delete
  .where(_.discontinueddate < TypoLocalDateTime.now)
  .execute()

// Delete by ID
productRepo.deleteById(ProductId(1))

// Delete multiple IDs
productRepo.deleteByIds(Array(ProductId(1), ProductId(2), ProductId(3)))
```

## Type Safety Features

The DSL leverages Scala's type system to prevent common errors:

1. **Column type safety**: You can't compare incompatible types
2. **Nullability tracking**: The DSL knows which columns are nullable
3. **Foreign key type safety**: ID types prevent joining on wrong columns
4. **Result type inference**: The compiler knows the exact shape of your results

## Performance Considerations

- The DSL generates efficient SQL with proper aliasing
- Joins are performed at the database level, not in memory
- Use `limit` for large result sets
- The generated SQL can be inspected using `.sql`

## When to Use SQL Files

While the DSL is powerful, it's designed for the "I just need to fetch/update this data" scenario. Use [SQL files](../what-is/sql-is-king.md) when you need:

- Aggregations (GROUP BY, COUNT, SUM, etc.)
- Window functions
- CTEs (Common Table Expressions)
- Complex subqueries
- Database-specific features

The DSL and SQL files complement each other - use the right tool for each job!
