package testapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.JacksonJsonProvider;
import org.glassfish.jersey.server.ResourceConfig;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Before;
import org.junit.Test;
import testapi.api.*;
import testapi.model.Error;
import testapi.model.Pet;
import testapi.model.PetCreate;
import testapi.model.PetId;
import testapi.model.PetStatus;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Integration tests for the OpenAPI generated JAX-RS server code.
 *
 * These tests start a real Grizzly HTTP server, use JAX-RS client to make HTTP calls,
 * and verify the round-trip works correctly through the generated code.
 */
public class OpenApiIntegrationTest {

    private static final OffsetDateTime TEST_TIME = OffsetDateTime.parse("2024-01-01T12:00:00Z");

    private static HttpServer server;
    private static Client client;
    private static URI serverUri;
    private static TestPetsApiServer serverImpl;

    static class TestPetsApiServer implements PetsApiServer {
        private final Pet initialPet = new Pet(
            Optional.of(List.of("friendly", "cute")),
            new PetId("pet-123"),
            PetStatus.available,
            TEST_TIME,
            Optional.of(Map.of("color", "brown")),
            "Fluffy",
            Optional.empty()
        );

        private final Map<PetId, Pet> pets = new HashMap<>();

        TestPetsApiServer() {
            reset();
        }

        void reset() {
            pets.clear();
            pets.put(initialPet.id(), initialPet);
        }

        @Override
        public Response201400<Pet, Error> createPet(PetCreate body) {
            var newPet = new Pet(
                body.tags(),
                new PetId("pet-" + System.nanoTime()),
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
        public Void deletePet(PetId petId) {
            pets.remove(petId);
            return null;
        }

        @Override
        public Response200404<Pet, Error> getPet(PetId petId) {
            if (pets.containsKey(petId)) {
                return new Ok<>(pets.get(petId));
            } else {
                return new NotFound<>(new Error("NOT_FOUND", Optional.empty(), "Pet not found"));
            }
        }

        @Override
        public Void getPetPhoto(PetId petId) {
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
        public JsonNode uploadPetPhoto(PetId petId, String caption, Byte[] file) {
            throw new UnsupportedOperationException("Not implemented");
        }
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new Jdk8Module());
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    @BeforeClass
    public static void startServer() {
        ObjectMapper mapper = createObjectMapper();
        JacksonJsonProvider jacksonProvider = new JacksonJsonProvider(mapper);

        serverImpl = new TestPetsApiServer();
        ResourceConfig config = new ResourceConfig()
            .registerInstances(serverImpl)
            .register(jacksonProvider);

        URI baseUri = URI.create("http://localhost:0/");
        server = GrizzlyHttpServerFactory.createHttpServer(baseUri, config);

        int port = server.getListener("grizzly").getPort();
        serverUri = URI.create("http://localhost:" + port);

        client = ClientBuilder.newClient().register(jacksonProvider);
    }

    @AfterClass
    public static void stopServer() {
        if (client != null) {
            client.close();
        }
        if (server != null) {
            server.shutdownNow();
        }
    }

    @Before
    public void resetState() {
        serverImpl.reset();
    }

    @Test
    public void testGetPetReturnsExistingPet() {
        Response response = client.target(serverUri)
            .path("/pets/pet-123")
            .request(MediaType.APPLICATION_JSON)
            .get();

        assertEquals(200, response.getStatus());
        Pet pet = response.readEntity(Pet.class);
        assertEquals("Fluffy", pet.name());
        assertEquals(new PetId("pet-123"), pet.id());
        assertEquals(PetStatus.available, pet.status());
    }

    @Test
    public void testGetPetReturnsNotFoundForNonExistentPet() {
        Response response = client.target(serverUri)
            .path("/pets/nonexistent")
            .request(MediaType.APPLICATION_JSON)
            .get();

        assertEquals(404, response.getStatus());
        Error error = response.readEntity(Error.class);
        assertEquals("NOT_FOUND", error.code());
    }

    @Test
    public void testCreatePetCreatesAndReturnsPet() {
        PetCreate newPet = new PetCreate(
            Optional.of(2L),
            Optional.empty(),
            "Buddy",
            Optional.of(PetStatus.pending),
            Optional.of(List.of("playful")),
            Optional.empty()
        );

        Response response = client.target(serverUri)
            .path("/pets")
            .request(MediaType.APPLICATION_JSON)
            .post(Entity.json(newPet));

        // JAX-RS returns 200 by default unless we explicitly set response status
        assertEquals(200, response.getStatus());
        Pet createdPet = response.readEntity(Pet.class);
        assertEquals("Buddy", createdPet.name());
        assertEquals(PetStatus.pending, createdPet.status());
    }

    @Test
    public void testDeletePetDeletesExistingPet() {
        Response response = client.target(serverUri)
            .path("/pets/pet-123")
            .request(MediaType.APPLICATION_JSON)
            .delete();

        assertEquals(204, response.getStatus());
    }

    @Test
    public void testListPetsReturnsAllPets() {
        Response response = client.target(serverUri)
            .path("/pets")
            .request(MediaType.APPLICATION_JSON)
            .get();

        assertEquals(200, response.getStatus());
        @SuppressWarnings("unchecked")
        List<LinkedHashMap<String, Object>> petsList = response.readEntity(List.class);
        assertFalse(petsList.isEmpty());
        assertTrue(petsList.stream().anyMatch(p -> "Fluffy".equals(p.get("name"))));
    }

    @Test
    public void testListPetsWithLimitReturnsLimitedPets() {
        Response response = client.target(serverUri)
            .path("/pets")
            .queryParam("limit", 1)
            .request(MediaType.APPLICATION_JSON)
            .get();

        assertEquals(200, response.getStatus());
        @SuppressWarnings("unchecked")
        List<LinkedHashMap<String, Object>> petsList = response.readEntity(List.class);
        assertEquals(1, petsList.size());
    }
}
