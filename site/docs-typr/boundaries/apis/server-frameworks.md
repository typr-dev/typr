---
title: Server Frameworks
sidebar_position: 4
---

# Server Framework Support

Typr generates server interfaces with framework-native annotations. Each framework gets idiomatic code that integrates naturally.

## Java + JAX-RS

Full Jakarta EE/JAX-RS support with proper annotations:

```java
@Path("/pets")
public interface PetsApiServer extends PetsApi {
  /** Create a pet */
  @Override
  Response201400<Pet, Error> createPet(PetCreate body);

  /** Endpoint wrapper - handles response status codes */
  @POST
  @Path("")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @SecurityRequirement(name = "oauth2", scopes = {"write:pets"})
  default Response createPetEndpoint(PetCreate body) {
    return switch (createPet(body)) {
      case Created r -> Response.status(201).entity(r.value()).build();
      case BadRequest r -> Response.status(400).entity(r.value()).build();
      default -> throw new IllegalStateException("Unexpected response type");
    };
  }

  @DELETE
  @Path("/{petId}")
  Void deletePet(@PathParam("petId") PetId petId);
}
```

## Java + Spring Boot

Spring MVC with `@RestController` and response entities:

```java
@RestController
@RequestMapping("/pets")
public interface PetsApiServer extends PetsApi {
  @Override
  Response201400<Pet, Error> createPet(PetCreate body);

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE,
               produces = MediaType.APPLICATION_JSON_VALUE)
  @SecurityRequirement(name = "oauth2", scopes = {"write:pets"})
  default ResponseEntity<?> createPetEndpoint(@RequestBody PetCreate body) {
    return switch (createPet(body)) {
      case Created r -> ResponseEntity.status(201).body(r.value());
      case BadRequest r -> ResponseEntity.status(400).body(r.value());
      default -> throw new IllegalStateException("Unexpected response type");
    };
  }

  @DeleteMapping("/{petId}")
  Void deletePet(@PathVariable("petId") PetId petId);
}
```

## Java + Quarkus (Reactive)

Quarkus with Mutiny `Uni<T>` for reactive programming:

```java
@Path("/pets")
public interface PetsApiServer extends PetsApi {
  @Override
  Uni<Response201400<Pet, Error>> createPet(PetCreate body);

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  default Uni<Response> createPetEndpoint(PetCreate body) {
    return createPet(body).map(response -> switch (response) {
      case Created r -> Response.status(201).entity(r.value()).build();
      case BadRequest r -> Response.status(400).entity(r.value()).build();
      default -> throw new IllegalStateException("Unexpected response type");
    });
  }

  @DELETE
  @Path("/{petId}")
  Uni<Void> deletePet(@PathParam("petId") PetId petId);
}
```

## Kotlin + JAX-RS

Idiomatic Kotlin with `when` expressions:

```kotlin
interface PetsApiServer : PetsApi {
  override fun createPet(body: PetCreate): Response201400<Pet, Error>

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  fun createPetEndpoint(body: PetCreate): Response =
    when (val response = createPet(body)) {
      is Created<*> -> Response.status(201).entity(response.value).build()
      is BadRequest<*> -> Response.status(400).entity(response.value).build()
      else -> throw IllegalStateException("Unexpected response type")
    }

  @DELETE
  @Path("/{petId}")
  override fun deletePet(petId: PetId)
}
```

## Scala + Http4s

Functional Scala with Cats Effect and auto-generated routes:

```scala
trait PetsApiServer extends PetsApi {
  override def createPet(body: PetCreate): IO[Response201400[Pet, Error]]

  def createPetEndpoint(body: PetCreate): IO[Response[IO]] =
    createPet(body).flatMap {
      case r: Created[Pet] => r.toResponse
      case r: BadRequest[Error] => r.toResponse
    }

  override def deletePet(petId: PetId): IO[Unit]

  /** HTTP routes - wire to your Http4s server */
  def routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> Root / "pets" =>
      req.as[PetCreate].flatMap(createPetEndpoint)
    case DELETE -> Root / "pets" / PetId(petId) =>
      deletePet(petId).flatMap(_ => NoContent())
  }
}
```

## Shared API Contract

All server frameworks extend the same base API interface. This means you can:

1. **Test with mocks** - Implement the base interface for unit tests
2. **Switch frameworks** - Change frameworks without changing business logic
3. **Share types** - Use the same request/response types everywhere
