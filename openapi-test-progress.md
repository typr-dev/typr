# OpenAPI Test Projects Progress

## Goal
Create integration tests for all openapi-test-* projects:
- Implement the server using generated server traits
- Start the server
- Call all endpoints with the generated client
- Verify responses

## Projects

| Project | Language | Framework | Server | Client | Status |
|---------|----------|-----------|--------|--------|--------|
| openapi-test-scala-server-http4s-client-http4s | Scala | Http4s | Http4s | Http4s | ✅ Done |
| openapi-test-java-server-spring | Java | Spring | Spring WebFlux | - | ⏳ Pending |
| openapi-test-java-server-jaxrs | Java | JAX-RS | Jersey/Jetty | - | ⏳ Pending |
| openapi-test-java-server-quarkus-reactive-client-mp-reactive | Java | Quarkus | Quarkus | MP Rest Client | ⏳ Pending |
| openapi-test-java-client-mp | Java | - | - | MP Rest Client | ⏳ Pending |
| openapi-test-kotlin-server-spring | Kotlin | Spring | Spring WebFlux | - | ⏳ Pending |
| openapi-test-kotlin-server-jaxrs | Kotlin | JAX-RS | Jersey/Jetty | - | ⏳ Pending |
| openapi-test-kotlin-server-quarkus-reactive-client-mp-reactive | Kotlin | Quarkus | Quarkus | MP Rest Client | ⏳ Pending |
| openapi-test-scala | Scala | - | - | - | ⏳ Pending |

## Completed

### openapi-test-scala-server-http4s-client-http4s
- ✅ Server trait with `routes` method generating HttpRoutes[IO]
- ✅ `toResponse` method on response case classes
- ✅ No asInstanceOf casts (using @unchecked)
- ✅ Query param type conversion (Int, Long, etc.)
- ✅ PetsApiTest with 4 passing tests:
  - create and get pet roundtrip
  - get non-existent pet returns 404
  - list pets with pagination
  - delete pet

## In Progress

### openapi-test-java-server-spring
- [ ] Add bleep project definition
- [ ] Add Spring WebFlux dependencies
- [ ] Implement PetsApiServer
- [ ] Implement AnimalsApiServer
- [ ] Create integration test
- [ ] Verify all endpoints work

## Pending

### openapi-test-java-server-jaxrs
### openapi-test-java-server-quarkus-reactive-client-mp-reactive
### openapi-test-java-client-mp
### openapi-test-kotlin-server-spring
### openapi-test-kotlin-server-jaxrs
### openapi-test-kotlin-server-quarkus-reactive-client-mp-reactive
### openapi-test-scala
