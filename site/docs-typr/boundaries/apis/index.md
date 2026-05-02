---
title: Introduction to Typr API
sidebar_position: 1
---

Your OpenAPI spec is a contract. Typr API enforces it.

Typr API generates type-safe servers and clients from your OpenAPI specification—for Java, Kotlin, and Scala. Same interface on both sides. Sealed response types for exhaustive handling. Framework-native code.

## The Problem: The API Boundary

Your OpenAPI spec defines your API contract. But between that spec and your implementation code:

- Request/response types defined manually, drifting from the spec
- Status codes handled inconsistently (or not at all)
- ID types mixed up across endpoints (`userId` as String in one place, Long in another)
- Breaking changes to the spec that don't break the build

**The result**: API contracts that exist on paper but aren't enforced in code.

## The Solution: Spec as Contract

Typr API treats your OpenAPI spec as the source of truth:

1. **Read your spec** (OpenAPI 3.0/3.1 or JSON Schema)
2. **Generate typed code** for every endpoint, request, response, and parameter
3. **Enforce at compile time** that implementations match the contract

When you change an endpoint, add a parameter, or modify a response—the compiler shows every impact. Server and client stay in sync.

:::tip Supported Specifications
- **OpenAPI 3.0/3.1** — Full API contracts with endpoints, parameters, and responses
- **JSON Schema** — Standalone type definitions for data models
:::

## Who Benefits

**New team members** implement endpoints within well-defined interfaces. The types guide them. The compiler catches mistakes.

**Contractors** can't accidentally break the API contract. The interface is explicit. Deviations don't compile.

**Seniors** review spec changes—not implementation details. High-leverage review where it matters.

## Cross-Language, Cross-Framework

Unlike most OpenAPI generators that target a single language, Typr generates **semantically equivalent code** across:

| Language | Server Frameworks | Client |
|----------|-------------------|--------|
| **Java** | JAX-RS, Spring Boot, Quarkus (reactive) | JDK HttpClient |
| **Kotlin** | JAX-RS, Spring Boot, Quarkus (reactive) | JDK HttpClient |
| **Scala** | Http4s, Spring Boot | Http4s, JDK HttpClient |

All generated code shares the same API contract—the same type-safe interfaces, response types, and ID wrappers work identically across all targets.

## Key Features

| Feature | Description |
|---------|-------------|
| **Type-safe ID wrappers** | `UserId`, `OrderId`—distinct types that can't be mixed up |
| **Sealed response types** | Exhaustive pattern matching for HTTP status codes |
| **Server + Client generation** | Both sides implement the same interface |
| **Framework-native code** | Idiomatic annotations and patterns for each framework |
| **Reactive support** | Mutiny `Uni<T>` for Quarkus, Cats Effect `IO` for Http4s |

## Shared Types with Typr DB

Typr API and Typr DB can share the same types. A `UserId` in your API response becomes the exact same type as `UserId` in your database row.

[Learn more about shared types →](/typr/unified-types/)

Ready to make your API boundary type-safe? Keep reading to explore Typr API's features.
