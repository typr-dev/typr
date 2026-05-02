---
title: Getting Started
sidebar_position: 2
---

# Getting Started with Typr

Typr is a type-safe code generator for JVM languages. It reads your database schemas and OpenAPI specifications, then generates fully-typed code that the compiler can verify.

:::info Private Beta

Typr is currently in private beta. We're working with select teams to refine the experience before public release in **early 2026**.

:::

<details>
<summary>Installation (available in beta)</summary>

### Via Coursier (Recommended)

```bash
cs install typr --channel https://typr.dev/channel
```

### Via Homebrew (macOS)

```bash
brew install oyvindberg/tap/typr
```

### Via npm (Cross-platform)

```bash
npm install -g @typr/cli
```

### Direct Download

```bash
# Download the latest release
curl -L https://github.com/oyvindberg/typr/releases/latest/download/typr -o typr
chmod +x typr

# Or as a JAR file
curl -L https://github.com/oyvindberg/typr/releases/latest/download/typr.jar -o typr.jar
java -jar typr.jar --help
```

</details>

<details>
<summary>Quick Start (available in beta)</summary>

### 1. Initialize

```bash
typr init
```

Creates `typr.yaml`. You'll be prompted for language and first data source.

### 2. Configure

Edit `typr.yaml`:

```yaml
sources:
  postgres:
    type: postgresql
    host: localhost
    database: myapp
    username: ${POSTGRES_USER}
    password: ${POSTGRES_PASSWORD}

output:
  path: ./generated
  package: com.myapp
  language: java
```

### 3. Generate

```bash
typr
```

That's it. Running `typr` without arguments generates code.

### 4. Use

```java
// Generated types are ready to use
UserRow user = userRepo.selectById(new UserId(123));

// Type-safe - can't mix up ID types
// userRepo.selectById(new OrderId(123)); // Compile error!
```

</details>

## What's Next?

- [Databases](/typr/boundaries/databases/) - Database code generation
- [REST APIs](/typr/boundaries/apis/) - OpenAPI code generation
- [Unified Types](/typr/unified-types/) - Share types across boundaries
