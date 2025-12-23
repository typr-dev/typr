---
title: Usage
sidebar_position: 6
---

# Using the OpenAPI Generator

## Basic Usage

```scala
import typr.openapi.codegen._

val spec = OpenApiParser.parse(Paths.get("api.yaml"))

val options = OpenApiCodegenOptions(
  pkg = "com.example.api",
  lang = LangJava,
  serverFramework = Some(ServerFramework.JaxRs),
  clientFramework = Some(ClientFramework.JdkHttpClient)
)

val files = ApiCodegen.generate(spec, options)

files.foreach { file =>
  Files.write(file.path, file.content.getBytes)
}
```

## Configuration Options

### Language Selection

```scala
// Java
lang = LangJava

// Kotlin
lang = LangKotlin

// Scala
lang = LangScala(scalaVersion = "3")
```

### Server Frameworks

```scala
// JAX-RS (Java/Kotlin)
serverFramework = Some(ServerFramework.JaxRs)

// Spring Boot (Java/Kotlin/Scala)
serverFramework = Some(ServerFramework.Spring)

// Quarkus Reactive (Java/Kotlin) - uses Mutiny Uni<T>
serverFramework = Some(ServerFramework.QuarkusReactive)

// Http4s (Scala) - uses Cats Effect IO
serverFramework = Some(ServerFramework.Http4s)
```

### Client Frameworks

```scala
// JDK HttpClient (all languages)
clientFramework = Some(ClientFramework.JdkHttpClient)

// Http4s Client (Scala only)
clientFramework = Some(ClientFramework.Http4s)
```

## Generated File Structure

```
com/example/api/
├── api/
│   ├── PetsApi.java           # Shared interface
│   ├── PetsApiServer.java     # Server with annotations
│   ├── PetsApiClient.java     # HTTP client
│   ├── Ok.java                # Response type
│   ├── NotFound.java          # Response type
│   └── Response200404.java    # Sealed interface
└── model/
    ├── Pet.java               # Schema types
    ├── PetCreate.java
    ├── PetId.java             # Type-safe ID
    └── Error.java
```

## Integration with Build Tools

### SBT (Scala)

```scala
lazy val generateApi = taskKey[Seq[File]]("Generate API code")

generateApi := {
  val spec = OpenApiParser.parse(baseDirectory.value / "api.yaml")
  val options = OpenApiCodegenOptions(...)
  ApiCodegen.generate(spec, options).map { file =>
    val target = (Compile / sourceManaged).value / file.relativePath
    IO.write(target, file.content)
    target
  }
}

Compile / sourceGenerators += generateApi
```

### Gradle (Kotlin/Java)

```kotlin
tasks.register("generateApi") {
    doLast {
        // Call Typo generator via CLI or embedded
    }
}

tasks.named("compileKotlin") {
    dependsOn("generateApi")
}
```
