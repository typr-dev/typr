package testapi;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Test;
import testapi.api.*;
import testapi.model.Error;
import testapi.model.Pet;
import testapi.model.PetCreate;
import testapi.model.PetStatus;

import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Unit tests for the OpenAPI generated JAX-RS server code.
 *
 * These tests verify that:
 * 1. The generated server interface can be implemented
 * 2. The generated response types work correctly with phantom type parameters
 * 3. The pattern matching in switch expressions works as expected
 */
public class OpenApiIntegrationTest {

    private static final OffsetDateTime TEST_TIME = OffsetDateTime.parse("2024-01-01T12:00:00Z");

    static class TestPetsApiServer implements PetsApiServer {
        private final Map<String, Pet> pets = new HashMap<>();

        {
            pets.put("pet-123", new Pet(
                Optional.of(List.of("friendly", "cute")),
                "pet-123",
                PetStatus.available,
                TEST_TIME,
                Optional.of(Map.of("color", "brown")),
                "Fluffy",
                Optional.empty()
            ));
        }

        @Override
        public Response201400<Pet, Error> createPet(PetCreate body) {
            var newPet = new Pet(
                body.tags(),
                "pet-" + System.nanoTime(),
                body.status().orElse(PetStatus.available),
                TEST_TIME,
                Optional.empty(),
                body.name(),
                Optional.empty()
            );
            pets.put(newPet.id(), newPet);
            return new Created<>(newPet);
        }

        @Override
        public Response404Default<Error> deletePet(String petId) {
            if (pets.containsKey(petId)) {
                pets.remove(petId);
                return new Default<>(204, new Error("OK", Optional.empty(), "Deleted"));
            } else {
                return new NotFound<>(new Error("NOT_FOUND", Optional.empty(), "Pet not found"));
            }
        }

        @Override
        public Response200404<Pet, Error> getPet(String petId) {
            if (pets.containsKey(petId)) {
                return new Ok<>(pets.get(petId));
            } else {
                return new NotFound<>(new Error("NOT_FOUND", Optional.empty(), "Pet not found"));
            }
        }

        @Override
        public Void getPetPhoto(String petId) {
            throw new UnsupportedOperationException("Not implemented");
        }

        @Override
        public List<Pet> listPets(Optional<Integer> limit, Optional<String> status) {
            var filtered = pets.values().stream();
            if (status.isPresent()) {
                filtered = filtered.filter(p -> p.status().value().equals(status.get()));
            }
            var limited = filtered;
            if (limit.isPresent()) {
                limited = limited.limit(limit.get());
            }
            return limited.toList();
        }

        @Override
        public JsonNode uploadPetPhoto(String petId, String caption, Byte[] file) {
            throw new UnsupportedOperationException("Not implemented");
        }
    }

    @Test
    public void testGetPetReturnsOk() {
        var server = new TestPetsApiServer();
        Response200404<Pet, Error> result = server.getPet("pet-123");

        assertTrue(result instanceof Ok);
        assertEquals("200", result.status());

        @SuppressWarnings("unchecked")
        Ok<Pet, Error> ok = (Ok<Pet, Error>) result;
        assertEquals("Fluffy", ok.value().name());
    }

    @Test
    public void testGetPetReturnsNotFound() {
        var server = new TestPetsApiServer();
        Response200404<Pet, Error> result = server.getPet("nonexistent");

        assertTrue(result instanceof NotFound);
        assertEquals("404", result.status());

        @SuppressWarnings("unchecked")
        NotFound<Pet, Error> notFound = (NotFound<Pet, Error>) result;
        assertEquals("NOT_FOUND", notFound.value().code());
    }

    @Test
    public void testCreatePetReturnsCreated() {
        var server = new TestPetsApiServer();
        var newPet = new PetCreate(
            Optional.of(2L),
            Optional.empty(),
            "Buddy",
            Optional.of(PetStatus.pending),
            Optional.of(List.of("playful")),
            Optional.empty()
        );

        Response201400<Pet, Error> result = server.createPet(newPet);

        assertTrue(result instanceof Created);
        assertEquals("201", result.status());

        @SuppressWarnings("unchecked")
        Created<Pet, Error> created = (Created<Pet, Error>) result;
        assertEquals("Buddy", created.value().name());
        assertEquals(PetStatus.pending, created.value().status());
    }

    @Test
    public void testDeletePetReturnsDefault() {
        var server = new TestPetsApiServer();
        Response404Default<Error> result = server.deletePet("pet-123");

        assertTrue(result instanceof Default);
        assertEquals("default", result.status());

        @SuppressWarnings("unchecked")
        Default<Error> defaultResp = (Default<Error>) result;
        assertEquals(204, (int) defaultResp.statusCode());
    }

    @Test
    public void testDeletePetReturnsNotFoundForMissing() {
        var server = new TestPetsApiServer();
        Response404Default<Error> result = server.deletePet("nonexistent");

        assertTrue(result instanceof NotFound);
        assertEquals("404", result.status());
    }

    @Test
    public void testListPetsReturnsList() {
        var server = new TestPetsApiServer();
        List<Pet> result = server.listPets(Optional.empty(), Optional.empty());

        assertFalse(result.isEmpty());
        assertTrue(result.stream().anyMatch(p -> p.name().equals("Fluffy")));
    }

    @Test
    public void testPatternMatchingInSwitch() {
        // This test verifies that the generated sealed interfaces work with pattern matching
        var server = new TestPetsApiServer();

        // Test Response200404 pattern matching (simulating endpoint wrapper)
        Response200404<Pet, Error> getPetResult = server.getPet("pet-123");
        String resultBody = switch (getPetResult) {
            case Ok<?, ?> r -> "OK: " + ((Pet) r.value()).name();
            case NotFound<?, ?> r -> "NotFound: " + ((Error) r.value()).code();
            default -> "Unknown";
        };
        assertEquals("OK: Fluffy", resultBody);

        // Test Response201400 pattern matching
        Response201400<Pet, Error> createResult = server.createPet(
            new PetCreate(Optional.empty(), Optional.empty(), "Test", Optional.empty(), Optional.empty(), Optional.empty())
        );
        String createBody = switch (createResult) {
            case Created<?, ?> r -> "Created: " + ((Pet) r.value()).name();
            case BadRequest<?, ?> r -> "BadRequest: " + ((Error) r.value()).code();
            default -> "Unknown";
        };
        assertEquals("Created: Test", createBody);

        // Test Response404Default pattern matching
        Response404Default<Error> deleteResult = server.deletePet("nonexistent");
        String deleteBody = switch (deleteResult) {
            case NotFound<?, ?> r -> "NotFound: " + ((Error) r.value()).code();
            case Default<?> r -> "Default: " + r.statusCode();
            default -> "Unknown";
        };
        assertEquals("NotFound: NOT_FOUND", deleteBody);
    }

    @Test
    public void testPhantomTypeParametersEnablePolymorphism() {
        // This test verifies that the phantom type parameters allow polymorphic usage
        // Created<Pet, Error> should be assignable to Response201400<Pet, Error>

        Response201400<Pet, Error> response = new Created<>(new Pet(
            Optional.empty(), "test-id", PetStatus.available,
            OffsetDateTime.now(), Optional.empty(), "Test", Optional.empty()
        ));

        assertEquals("201", response.status());

        // Similarly for NotFound with multiple interfaces
        Response200404<Pet, Error> okResponse = new Ok<>(new Pet(
            Optional.empty(), "test-id", PetStatus.available,
            OffsetDateTime.now(), Optional.empty(), "Test", Optional.empty()
        ));
        assertEquals("200", okResponse.status());

        Response200404<Pet, Error> notFoundResponse = new NotFound<>(
            new Error("NOT_FOUND", Optional.empty(), "Not found")
        );
        assertEquals("404", notFoundResponse.status());
    }
}
