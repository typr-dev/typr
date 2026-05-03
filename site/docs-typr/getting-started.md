---
title: Getting Started
sidebar_position: 2
---

# Getting Started with Typr

Typr is a type-safe code generator for JVM languages. It reads your database schemas, OpenAPI specifications, and Avro/Kafka events, then generates fully-typed code that the compiler can verify end-to-end.

:::info Closed Beta

Typr is currently in **closed beta**. We're working with a small set of teams to harden the CLI, the DSL, and the unified-types story before opening signups more broadly. Public release is scheduled for **early 2026**.

Want a beta seat? Email [oyvind@typr.dev](mailto:oyvind@typr.dev) — we're prioritising teams running multiple boundaries (database + API, or database + Kafka) where the unified-types work earns its keep fastest.

:::

## What's available right now

You can read and link to all the documentation:

- [Databases](/typr/boundaries/databases/) — how Typr generates code from your schema
- [REST APIs](/typr/boundaries/apis/) — server + client generation from OpenAPI
- [Events (Avro/Kafka)](/typr/boundaries/events/) — typed records from Avro schemas
- [Unified Types](/typr/unified-types/) — one domain type across every boundary
- [Comparison](/typr/comparison/) — how Typr compares to alternatives

## What's *not* available right now

Installation instructions and a public CLI download are gated behind the closed beta. Once the beta opens up, this page will publish step-by-step setup for Coursier, Homebrew, SDKMAN, JBang, and Scoop, plus a `typr init`-flavoured walkthrough.
