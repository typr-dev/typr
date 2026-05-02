---
title: User-selected types
---

This functionality is present in Typr in order to:
- avoid generating [primary key types](id-types.md) for what is typically strings and numbers if you already have such a type and you're integrating with an existing codebase.
- to type up columns which should have had a more specific wrapper type, again typically strings or numbers

It's strongly discouraged to use it for anything else.

## Usage
You setup user-selected types by [Customizing column types](../customization/customize-types.md).

You need to implement a bunch of typeclass instances for the types you use.
The compiler will guide you, but it's basically everything which is needed to use the type with your database and json library.
The easiest approach is probably to copy/paste a generated primary key type and adapt it.

## 💣 Note 💣

It is crucial that you implement these typeclass instances **correctly**, or things may obviously blow up at runtime.
In particular, the generated Typr code makes use of `sqlType` to make explicit casts in generated SQL.


