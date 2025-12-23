---
title: Introduction to Typo
---

Typo is not just another source code generator; it's your trusted partner in database development. By harnessing the
power of PostgreSQL schema definitions and your SQL code, Typo creates a seamless bridge between your database and your
Scala code, all while putting type-safety and developer experience (DX) front and center.

## The Motivation Behind Typo

### Building Safer Systems

In the world of software development, we rely on the compiler to catch errors and ensure the correctness of our code.
But what happens when we venture into the unpredictable realm of external data sources, like databases?

Typo's core motivation is to bring contract-driven development to the database layer. Just as generating code from
OpenAPI definitions ensures the correctness of your HTTP layer, Typo aims to deliver the same level of safety for
database interactions. It achieves this by generating precise and correct code for your tables, views, and queries, all
guided by PostgreSQL metadata tables.

### Revolutionizing the SQL to JVM Workflow

The conventional workflow for SQL-to-JVM interaction often feels like a labyrinth of manual tasks and repetitive boilerplate:

1. You write SQL queries.
2. IDEs may struggle to give you proper support while writing, especially if you interpolate and concatenate much
3. Manual mapping of column names or indices to case class field names.
4. Manual mapping of column names or indices to case class field types 
5. String interpolation and type mapping may trigger cryptic errors for missing typeclass instances 
6. The compiler cannot check the mappings, forcing you into writing tests.
7. Writing and maintaining tests is tedious, and even slow to run.

But here's the kicker: Whenever you refactor your code, you find yourself revisiting all of these points.

Typo changes the game.
It streamlines steps 2-7, liberates you from boilerplate, and lets you focus on what truly matters: 
building robust and maintainable database applications.

### Example video
As an example of how typo frees you from these steps, consider this video where you write your SQL in an `.sql` file,
and you see typo regenerating correct mapping code for it on save. 
Much less testing is needed, because the name and type mapping will be correct, and the sql valid.

<video
width="100%"
controls
src="https://github.com/oyvindberg/typr/assets/247937/df7c4f2d-b118-4081-81c6-dd03dfe62ee2"
/>

Typo is also much more than this, keep reading to discover :)

### Types of Database Interactions

Interactions with the database fall into four distinct categories, each with its unique challenges and requirements:

1. **CRUD Operations**: The bread and butter of database interactions. Typo offers [repository methods](what-is/relations.md) for simple and safe CRUD, and the [SQL DSL](what-is/dsl.md) can help you do it in batch.
2. **Simple Reads**: Retrieving data from relations, often involving joins. Typo's [SQL DSL](what-is/dsl.md) is your go-to for these scenarios.
3. **Complex Reads**: Aggregations, window functions, and advanced queries. In Typo, these interactions are best handled by [writing SQL files](what-is/sql-is-king.md).
4. **Dynamic Queries**: Rare and resource-intensive, these custom queries require careful implementation. Typo leaves
   this space open for you and your [existing database library](other-features/flexible.md), as more research is needed in this direction.

Intrigued? Keep exploring to discover how Typo transforms your database development experience.