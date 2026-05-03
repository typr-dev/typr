---
title: Testing with Random Values
---

import ShowcaseSnippet from '@site/src/components/ShowcaseSnippet';
import UsageExample from '@site/src/components/UsageExample';

If you enable `enableTestInserts` in `typr.Options` you get a `TestInsert` class with a method to insert a row for each table Typr knows about.
All values **except** IDs and foreign keys are **randomly generated**, but you can override them.

## Usage Example

<UsageExample file="TestInsertExample" databases={['postgres']} />

You're setting up a graph of data in the database: `Company` → `Department` → `Employee`.
The foreign keys force you to create data in the right order, and after each insert you get the persisted row back with its generated ID.

Almost no ceremony:
- set only the values you care about
- get random values for everything else
- FKs are type-safe, so you can't mix them up
- use a fixed seed for reproducible tests, or vary it to prove the random values don't matter

## Generated TestInsert

<ShowcaseSnippet
  file="TestInsert"
  from="public record TestInsert"
  fromByLang={{ kotlin: "data class TestInsert", scala: "case class TestInsert" }}
  to="def showcaseAuditLog"
  toByLang={{ java: "public Inserter<AuditLogRowUnsaved", kotlin: "fun showcaseAuditLog" }}
  databases={['postgres']}
/>

## Domains

If you use [domains](../type-safety/domains.md) you typically want to control the generation of data yourself.
For that reason there is an interface you need to implement and pass in. This only affects you if you use domains.

### TestDomainInsert Interface

<ShowcaseSnippet file="TestDomainInsert" databases={['postgres']} />

### DomainInsert Implementation

<UsageExample file="DomainInsertImpl" databases={['postgres']} />

## Comparison with ScalaCheck

This does look a lot like ScalaCheck/property-based testing.

But look closer, there are:
- no implicits or typeclasses to define
- no integration glue code with test libraries
- almost no imports needed, you mention very few types
- no keeping track of all the possible row types and repositories
- automatic handling of the FK graph

This feature is meant to be easy to use, and I really think/hope it is!
