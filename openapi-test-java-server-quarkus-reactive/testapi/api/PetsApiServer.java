package testapi.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.smallrye.mutiny.Uni;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.lang.IllegalStateException;
import java.lang.Void;
import java.util.List;
import java.util.Optional;
import org.glassfish.jersey.media.multipart.FormDataParam;
import testapi.model.Error;
import testapi.model.Pet;
import testapi.model.PetCreate;
import testapi.model.PetId;

@Path("/pets")
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
@SecurityScheme(name = "apiKeyHeader", type = SecuritySchemeType.APIKEY, in = SecuritySchemeIn.HEADER, paramName = "X-API-Key")
@SecurityScheme(name = "apiKeyQuery", type = SecuritySchemeType.APIKEY, in = SecuritySchemeIn.QUERY, paramName = "api_key")
@SecurityScheme(name = "oauth2", type = SecuritySchemeType.OAUTH2)
public interface PetsApiServer extends PetsApi {
  /** Create a pet */
  @Override
  Uni<Response201400<Pet, Error>> createPet(PetCreate body);

  /** Endpoint wrapper for createPet - handles response status codes */
  @POST
  @Path("/")
  @Consumes(value = { MediaType.APPLICATION_JSON })
  @Produces(value = { MediaType.APPLICATION_JSON })
  @SecurityRequirement(name = "oauth2", scopes = { "write:pets" })
  @SecurityRequirement(name = "apiKeyHeader")
  default Uni<Response> createPetEndpoint(PetCreate body) {
    return createPet(body).map((Response201400 response) -> switch (response) {
      case Created r -> Response.ok(r.value()).build();
      case BadRequest r -> Response.status(400).entity(r.value()).build();
      default -> throw new IllegalStateException("Unexpected response type");
    });
  };

  /** Delete a pet */
  @Override
  @DELETE
  @Path("/{petId}")
  Uni<Void> deletePet(
  
    /** The pet ID */
    @PathParam("petId") PetId petId
  );

  /** Get a pet by ID */
  @Override
  Uni<Response200404<Pet, Error>> getPet(
  
    /** The pet ID */
    PetId petId
  );

  /** Endpoint wrapper for getPet - handles response status codes */
  @GET
  @Path("/{petId}")
  @Produces(value = { MediaType.APPLICATION_JSON })
  default Uni<Response> getPetEndpoint(
  
    /** The pet ID */
    @PathParam("petId") PetId petId
  ) {
    return getPet(petId).map((Response200404 response) -> switch (response) {
      case Ok r -> Response.ok(r.value()).build();
      case NotFound r -> Response.status(404).entity(r.value()).build();
      default -> throw new IllegalStateException("Unexpected response type");
    });
  };

  /** Get pet photo */
  @Override
  @GET
  @Path("/{petId}/photo")
  @Produces(value = { MediaType.APPLICATION_OCTET_STREAM })
  Uni<Void> getPetPhoto(
  
    /** The pet ID */
    @PathParam("petId") PetId petId
  );

  /** List all pets */
  @Override
  @GET
  @Path("/")
  @Produces(value = { MediaType.APPLICATION_JSON })
  Uni<List<Pet>> listPets(
    /** Maximum number of pets to return */
    @QueryParam("limit") @DefaultValue("20") Optional<Integer> limit,
    /** Filter by status */
    @QueryParam("status") @DefaultValue("available") Optional<String> status
  );

  /** Upload a pet photo */
  @Override
  @POST
  @Path("/{petId}/photo")
  @Consumes(value = { MediaType.MULTIPART_FORM_DATA })
  @Produces(value = { MediaType.APPLICATION_JSON })
  Uni<JsonNode> uploadPetPhoto(
    /** The pet ID */
    @PathParam("petId") PetId petId,
    /** Optional caption for the photo */
    @FormDataParam("caption") String caption,
    /** The photo file to upload */
    @FormDataParam("file") Byte[] file
  );
}