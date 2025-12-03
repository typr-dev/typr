# OpenAPI Test Implementation Progress

## Goal
Implement server applications and client tests for each openapi-test-* project to validate the generated OpenAPI code.

## Projects Status

| Project | Status | Notes |
|---------|--------|-------|
| openapi-test-scala-http4s | In Progress | Scala/http4s server + client |
| openapi-test-java-server-spring | Pending | |
| openapi-test-java-server-jaxrs | Pending | |
| openapi-test-java-server-quarkus-reactive | Pending | |
| openapi-test-java-client-mp | Pending | |
| openapi-test-kotlin-server-jaxrs | Pending | |
| openapi-test-kotlin-server-spring | Pending | |
| openapi-test-kotlin-server-quarkus-reactive | Pending | |
| openapi-test-scala | Pending | |

---

## openapi-test-scala-http4s

### Setup
- Added project to bleep.yaml with http4s, circe, and scalatest dependencies
- Project folder: `openapi-test-scala-server-http4s-client-http4s`

### Generated APIs
- `PetsApiServer` / `PetsApiClient` - CRUD for pets
- `AnimalsApiServer` / `AnimalsApiClient` - List polymorphic animals

### Response Types Used
- `Response201400[T201, T400]` - createPet (201 Created, 400 BadRequest)
- `Response404Default[T404]` - deletePet (404 NotFound, default)
- `Response200404[T200, T404]` - getPet (200 Ok, 404 NotFound)
- `Response2004XX5XX[T200]` - listAnimals (200 Ok, 4XX, 5XX)

### Implementation Plan
1. Create server implementation that can return all response types
2. Create http4s routes wiring
3. Create test that boots server, uses client to call all endpoints
4. Verify all response code paths work

### Progress Log
- [x] Add bleep.yaml project definition
- [x] Create server implementation (TestPetsApiServer, TestAnimalsApiServer)
- [x] Create http4s routes (Routes.scala)
- [x] Create integration test (OpenApiIntegrationTest.scala)
- [ ] Fix code generation issues - BLOCKED

### Discovered Code Generation Issues
The generated Scala/Http4s code has several issues that prevent compilation:

1. **`Response` type missing type parameter** - Http4s `Response` is `Response[F]`, generated as just `Response`
2. **Pattern match types missing type parameters** - `case r: Ok =>` should be `case r: Ok[_] =>`
3. **Missing type aliases** - `CreatePetResponse`, `DeletePetResponse`, `GetPetResponse` are referenced but not defined
4. **Lambda parameter type issues** - `(response: Response201400)` needs type args

These issues need to be fixed in the codegen before we can proceed with testing.
