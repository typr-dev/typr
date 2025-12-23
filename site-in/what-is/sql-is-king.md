---
title: SQL is king!
---

In the realm of data operations, SQL reigns supreme. 
That's why the primary workflow in Typo revolves around writing SQL in dedicated SQL files.

Here's why this approach is a game-changer:

**1. Fully Exploded Queries**: Forget about the hassle of converting SQL queries to and from source code. With Typo,
your queries reside in their pure form, ready to be executed as intended.

**2. Superb Tooling Support**: Typo's SQL-first approach unlocks unparalleled tooling support. Whether you're using an
IDE or language model (LLM), you'll experience seamless integration, enhanced autocompletion, and precise error
messages.

**3. Parameter Support**: Typo embraces the flexibility of SQL by allowing parameterization within your queries. It's
not just about static SQL; you can dynamically pass parameters to tailor your database interactions.

**4. Centralization**: Picture this—you can now gather and manage all your queries used across (or within parts of) a
system in one centralized location. This is more than just convenience; it's a game-changer for collaboration and
analysis. Your DBA can effortlessly script analyses and optimizations.

But let's not stop at words—let's dive right into a live example! In the video below, witness the magic as the generated
code seamlessly updates in real-time as you edit your SQL file. Experience the fluidity and power of Typo's SQL-first
approach for yourself.

<video
width="100%"
controls
src="https://github.com/oyvindberg/typr/assets/247937/df7c4f2d-b118-4081-81c6-dd03dfe62ee2"
/>


## Parameters

This video shows how you can use parameters. 

It also shows the syntax to [override inferred nullability and types](../customization/customize-sql-files.md).
While PostgreSQL is pretty good at both, it's not always exactly what you want. 
In particular, parameters are optional by default. 

<video
width="100%"
controls
src="https://github.com/oyvindberg/typr/assets/247937/b2965b74-8ee5-4475-8e40-5938ff44d385"
/>

## Updates

You can also `update`, `delete` and so on in sql scripts. 
Those statements can also return columns, see this video

<video
width="100%"
controls
src="https://github.com/oyvindberg/typr/assets/247937/e8c3c34a-0691-4ad3-bd44-b73bb14d5997"
/>
