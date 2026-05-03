---
title: Client Generation
sidebar_position: 5
---

# HTTP Client Generation

Typr generates type-safe HTTP clients that implement the same API interface as the server. This ensures client and server stay in sync.

## JDK HttpClient (Java/Kotlin)

A lightweight client using Java's built-in `HttpClient`:

### Java

```java
public class PetsApiClient implements PetsApi {
  private final HttpClient httpClient;
  private final URI baseUri;
  private final ObjectMapper objectMapper;

  public PetsApiClient(HttpClient httpClient, URI baseUri, ObjectMapper objectMapper) {
    this.httpClient = httpClient;
    this.baseUri = baseUri;
    this.objectMapper = objectMapper;
  }

  @Override
  public Response200404<Pet, Error> getPet(PetId petId) throws Exception {
    HttpRequest request = HttpRequest.newBuilder()
      .uri(URI.create(baseUri + "/pets/" + petId))
      .GET()
      .header("Accept", "application/json")
      .build();

    HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());
    int statusCode = response.statusCode();

    if (statusCode == 200) {
      return new Ok<>(objectMapper.readValue(response.body(), Pet.class));
    } else if (statusCode == 404) {
      return new NotFound<>(objectMapper.readValue(response.body(), Error.class));
    } else {
      throw new IllegalStateException("Unexpected status: " + statusCode);
    }
  }
}
```

### Kotlin

```kotlin
data class PetsApiClient(
  val httpClient: HttpClient,
  val baseUri: URI,
  val objectMapper: ObjectMapper
) : PetsApi {

  override fun getPet(petId: PetId): Response200404<Pet, Error> {
    val request = HttpRequest.newBuilder(URI.create("$baseUri/pets/$petId"))
      .GET()
      .header("Accept", "application/json")
      .build()

    val response = httpClient.send(request, BodyHandlers.ofString())
    val statusCode = response.statusCode()

    return when (statusCode) {
      200 -> Ok(objectMapper.readValue(response.body(), Pet::class.java))
      404 -> NotFound(objectMapper.readValue(response.body(), Error::class.java))
      else -> throw IllegalStateException("Unexpected status: $statusCode")
    }
  }
}
```

## Reactive Clients (Quarkus)

For Quarkus, clients return `Uni<T>` for non-blocking I/O:

```java
public Uni<Response200404<Pet, Error>> getPet(PetId petId) {
  HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create(baseUri + "/pets/" + petId))
    .GET()
    .build();

  return Uni.createFrom()
    .completionStage(() -> httpClient.sendAsync(request, BodyHandlers.ofString()))
    .map(response -> {
      int statusCode = response.statusCode();
      if (statusCode == 200) {
        return new Ok<>(objectMapper.readValue(response.body(), Pet.class));
      } else if (statusCode == 404) {
        return new NotFound<>(objectMapper.readValue(response.body(), Error.class));
      } else {
        throw new IllegalStateException("Unexpected status: " + statusCode);
      }
    });
}
```

## Http4s Client (Scala)

Functional Scala with Cats Effect:

```scala
class PetsApiClient(client: Client[IO], baseUri: Uri) extends PetsApi {

  override def getPet(petId: PetId): IO[Response200404[Pet, Error]] = {
    val request = Request[IO](Method.GET, baseUri / "pets" / petId)

    client.run(request).use { response =>
      val statusCode = response.status.code
      if (statusCode == 200)
        response.as[Pet].map(Ok(_))
      else if (statusCode == 404)
        response.as[Error].map(NotFound(_))
      else
        IO.raiseError(new IllegalStateException(s"Unexpected status: $statusCode"))
    }
  }
}
```

## Shared Interface

Both client and server implement the same interface:

```java
public interface PetsApi {
  Response200404<Pet, Error> getPet(PetId petId);
}

// Server implements it
public class PetsApiServerImpl implements PetsApiServer { ... }

// Client implements it
public class PetsApiClient implements PetsApi { ... }
```

This enables powerful patterns:

- **Contract testing** - Verify client and server agree
- **In-process testing** - Run integration tests without HTTP
- **Mocking** - Easily mock the API for unit tests
