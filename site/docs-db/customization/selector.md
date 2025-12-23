---
title: Picking subsets of relations with Selector
---

For much of the customization, you select sets of relations, or enable particular pieces of code generation for a set of relations.

In order to make this convenient, there is a `Selector` data type.

## Pick relations by name or by schema
You can pick relations by expressing with `Selector` what you want:

```scala
import typr.*

val personAndPet0 = Selector.fullRelationNames("myschema.person", "myschemapet") // picks exactly these tables
val personAndPet = Selector.relationNames("person", "pet") // picks these regardless of schema
val mySchema = Selector.schemas("myschema") // picks all relations in schema

// heaviest syntax, but most flexible
val custom: Selector = relName => relName.schema.exists(_.contains("foo")) && relName.name.contains("bar")

```

### Selectors can be inverted
```scala
!Selector.schemas("myschema") // matches everything except schema "myschema"
```

### Selectors are also composable:

```scala
// picks relations which are called `person` or `pet` AND are in the `myschema` schema
personAndPet and mySchema

// picks those who are *both* called `person` or `pet` OR are in the `myschema` schema. 
// This will typically select more relations
personAndPet or mySchema 
```

The and/or names follows boolean logic, and may actually be a bit counter-intuitive in this particular context. Suggestions welcome to improve naming
